package ac.jester.anticheat.manager;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.manager.init.ReloadableInitable;
import ac.jester.anticheat.manager.init.start.StartableInitable;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import ac.jester.anticheat.utils.data.Pair;
import ac.jester.anticheat.utils.data.webhook.discord.CompiledDiscordTemplate;
import ac.jester.anticheat.utils.data.webhook.discord.Embed;
import ac.jester.anticheat.utils.data.webhook.discord.EmbedField;
import ac.jester.anticheat.utils.data.webhook.discord.EmbedFooter;
import ac.jester.anticheat.utils.data.webhook.discord.WebhookMessage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DiscordManager implements StartableInitable, ReloadableInitable {
    private static final Predicate<String> WEBHOOK_REGEX = Pattern.compile("^https://(?:canary\\.)?discord\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w-]+(\\?thread_id=\\d+)?$").asMatchPredicate();
    private static final Predicate<String> HTTPS_URL_REGEX = Pattern.compile("^https://[^/\\s]+/\\S+$").asMatchPredicate();
    private static final Duration timeout = Duration.ofSeconds(15);
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
    private static final ConcurrentLinkedDeque<Pair<HttpRequest, CompletableFuture<Boolean>>> requests = new ConcurrentLinkedDeque<>();
    private static final AtomicBoolean taskStarted = new AtomicBoolean();
    private static final AtomicBoolean sending = new AtomicBoolean();
    private static volatile long rateLimitedUntil;
    private static final int MAX_QUEUED = 200;
    private URI url;
    private int embedColor;
    private CompiledDiscordTemplate compiledContent;
    private char backtickReplacement = '\u02CB';
    private String embedTitle = "";
    private boolean includeTimestamp;
    private boolean includeVerbose;
    private @Nullable String embedImageUrl;
    private @Nullable String embedThumbnailUrl;
    private @Nullable String embedFooterUrl;
    private String embedFooterText = "";

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://(?:www\\.)?[-a-z0-9@:%._+~#=]{1,256}\\.[a-z0-9()]{1,6}\\b[-a-z0-9()@:%_+.~#?&/=]*$", Pattern.CASE_INSENSITIVE);

    private static String validatedConfigURL(String configPath, String defaultURL) {
        String url = GrimAPI.INSTANCE.getConfigManager().getConfig().getStringElse(configPath, defaultURL);
        if (url == null || url.isBlank()) return null;
        if (URL_PATTERN.matcher(url).matches()) {
            return url;
        } else {
            LogUtil.warn("Invalid embed url for config path " + configPath + ": " + url);
            return defaultURL;
        }
    }

    @Override
    public void start() {
        reload();
    }

    @Override
    public void reload() {
        try {
            ConfigManager config = GrimAPI.INSTANCE.getConfigManager().getConfig();
            if (!config.getBooleanElse("enabled", false)) {
                url = null;
                return;
            }

            String webhook = config.getStringElse("webhook", "");
            boolean strictValidation = !config.getBooleanElse("disable-webhook-validation", false);

            if (webhook.isEmpty()) {
                url = null;
            } else if (strictValidation) {
                if (!WEBHOOK_REGEX.test(webhook)) {
                    LogUtil.error("Discord webhook URL does not match expected format"
                            + " (https://discord.com/api/webhooks/<id>/<token>): " + webhook);
                    LogUtil.error("If you are using a proxy or custom endpoint,"
                            + " set 'disable-webhook-validation: true' in the Discord config.");
                    url = null;
                } else {
                    url = new URI(webhook);
                }
            } else {
                if (!HTTPS_URL_REGEX.test(webhook)) {
                    LogUtil.error("Discord webhook URL is not a valid HTTPS URL: " + webhook);
                    url = null;
                } else {
                    LogUtil.info("Webhook validation disabled — using custom endpoint: "
                            + webhook.substring(0, Math.min(webhook.length(), 40)) + "...");
                    url = new URI(webhook);
                }
            }
            embedImageUrl = validatedConfigURL("embed-image-url", null);
            embedThumbnailUrl = validatedConfigURL("embed-thumbnail-url", "https://crafthead.net/helm/%uuid%");
            embedFooterUrl = validatedConfigURL("embed-footer-url", null);
            embedFooterText = config.getStringElse("embed-footer-text", "Jester Anti Cheat v%jester_version%");
            embedTitle = config.getStringElse("embed-title", "**Jester Alert**");

            try {
                embedColor = Color.decode(config.getStringElse("embed-color", "#00FFFF")).getRGB();
            } catch (NumberFormatException e) {
                LogUtil.warn("Discord embed color is invalid");
            }

            StringBuilder sb = new StringBuilder();
            for (String string : config.getStringListElse("violation-content", getDefaultContents())) {
                sb.append(string).append("\n");
            }
            includeTimestamp = config.getBooleanElse("include-timestamp", true);
            includeVerbose = config.getBooleanElse("include-verbose", true);
            String btReplace = config.getStringElse("backtick-replacement-char", "\u02CB");
            backtickReplacement = (btReplace.isEmpty()) ? '\u02CB' : btReplace.charAt(0);
            compiledContent = CompiledDiscordTemplate.compile(sb.toString());
        } catch (Exception e) {
            LogUtil.error("Failed to load Discord webhook configuration", e);
        }
    }

    @Contract(value = " -> new", pure = true)
    private @NotNull @Unmodifiable List<@NotNull String> getDefaultContents() {
        return List.of(
                "**Player**: `%player%`",
                "**Check**: %check%",
                "**Violations**: %violations%",
                "**Client Version**: %version%",
                "**Brand**: `%brand%`",
                "**Ping**: %ping%",
                "**TPS**: %tps%"
        );
    }

    public void sendAlert(@NotNull GrimPlayer player, String verbose, String checkName, int violations) {
        if (isDisabled()) {
            return;
        }

        Map<String, String> statics = new HashMap<>(GrimAPI.INSTANCE.getExternalAPI().getStaticReplacements());
        statics.put("%check%", checkName);
        statics.put("%violations%", Integer.toString(violations));

        Map<String, Function<GrimUser, String>> dynamics = GrimAPI.INSTANCE.getExternalAPI().getVariableReplacements();

        String content = compiledContent.render(player, statics, dynamics, backtickReplacement);

        Embed embed = new Embed(content)
                .color(embedColor)
                .title(embedTitle)
                .imageURL(MessageUtil.replacePlaceholders(player, embedImageUrl, false))
                .thumbnailURL(MessageUtil.replacePlaceholders(player, embedThumbnailUrl, false))
                .footer(new EmbedFooter(
                        MessageUtil.replacePlaceholders(player, embedFooterText, false),
                        MessageUtil.replacePlaceholders(player, embedFooterUrl, false)
                ));

        if (includeTimestamp) embed.timestamp(Instant.now());

        if (!verbose.isEmpty() && includeVerbose) {
            embed.addFields(new EmbedField("Verbose", CompiledDiscordTemplate.escapeMarkdown(verbose), true));
        }

        sendWebhookMessage(new WebhookMessage().addEmbeds(embed));
    }

    public CompletableFuture<Boolean> sendWebhookMessage(WebhookMessage message) {
        if (isDisabled()) return CompletableFuture.completedFuture(false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(message.toJson().toString()))
                .timeout(timeout)
                .build();

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        while (requests.size() >= MAX_QUEUED) {
            Pair<HttpRequest, CompletableFuture<Boolean>> dropped = requests.pollFirst();
            if (dropped != null) dropped.second().complete(false);
        }
        requests.add(new Pair<>(request, future));

        if (!taskStarted.getAndSet(true)) {
            GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(GrimAPI.INSTANCE.getGrimPlugin(), DiscordManager::tick, 0, 1);
        }

        return future;
    }

    public boolean isDisabled() {
        return url == null;
    }

    private static void tick() {
        Pair<HttpRequest, CompletableFuture<Boolean>> pair = requests.peek();
        if (pair != null && rateLimitedUntil < System.currentTimeMillis() && !sending.getAndSet(true)) {
            HttpRequest request = pair.first();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    requests.remove(pair);
                    rateLimitedUntil = System.currentTimeMillis() + 5000;
                    sending.set(false);
                    LogUtil.error("Exception caught while sending a Discord webhook alert", throwable);
                    pair.second().complete(false);
                    return;
                }

                if (response != null && response.statusCode() == 429) {
                    long waitUntil = System.currentTimeMillis() + 2000;
                    try {
                        var retry = response.headers().firstValue("Retry-After");
                        if (retry.isPresent()) {
                            waitUntil = System.currentTimeMillis() + (long) (Double.parseDouble(retry.get()) * 1000) + 100;
                        } else {
                            var reset = response.headers().firstValue("X-RateLimit-Reset");
                            if (reset.isPresent()) waitUntil = (long) (Double.parseDouble(reset.get()) * 1000) + 100;
                        }
                    } catch (RuntimeException ignored) {
                    }
                    rateLimitedUntil = Math.max(waitUntil, rateLimitedUntil);
                    sending.set(false);
                    return;
                }

                requests.remove(pair);
                sending.set(false);

                if (response != null && response.statusCode() >= 400) {
                    LogUtil.error("Encountered status code " + response.statusCode() + " with body " + response.body() + " and headers " + response.headers().map() + " while sending a Discord webhook alert.");
                    pair.second().complete(false);
                } else {
                    pair.second().complete(true);
                }
            });
        }
    }
}
