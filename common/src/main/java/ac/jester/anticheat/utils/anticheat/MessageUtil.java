package ac.jester.anticheat.utils.anticheat;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.GrimUser;
import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import ac.jester.anticheat.platform.api.sender.Sender;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class MessageUtil {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + '§' + "[0-9A-FK-ORX]");
    private final Pattern HEX_PATTERN = Pattern.compile("([&§]#[A-Fa-f0-9]{6})|([&§]x([&§][A-Fa-f0-9]){6})");
    private final Pattern BARE_HEX_PATTERN = Pattern.compile("(?<![:<&§x])#[A-Fa-f0-9]{6}");
    private final char PLACEHOLDER_ESCAPE_CHAR = '\uFFFF';

    public @NotNull String toUnlabledString(@Nullable Vector3i vec) {
        return vec == null ? "null" : vec.x + ", " + vec.y + ", " + vec.z;
    }

    public @NotNull String toUnlabledString(@Nullable Vector3f vec) {
        return vec == null ? "null" : vec.x + ", " + vec.y + ", " + vec.z;
    }

    @Contract("_, null, _ -> null; _, !null, _ -> !null")
    public @Nullable String replacePlaceholders(@Nullable GrimPlayer player, @Nullable String string, boolean removeFormatting) {
        return replacePlaceholders(player, player == null ? null : player.platformPlayer, string, removeFormatting);
    }

    @Contract("_, null -> null; _, !null -> !null")
    public @Nullable String replacePlaceholders(@Nullable GrimPlayer player, @Nullable String string) {
        return replacePlaceholders(player, player == null ? null : player.platformPlayer, string, false);
    }

    @Contract("_, null -> null; _, !null -> !null")
    public @Nullable String replacePlaceholders(@Nullable Sender sender, @Nullable String string) {
        return replacePlaceholders(sender != null ? sender.getPlatformPlayer() : null, string);
    }

    @Contract("_, null -> null; _, !null -> !null")
    public @Nullable String replacePlaceholders(@Nullable PlatformPlayer player, @Nullable String string) {
        return replacePlaceholders(player == null ? null : GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(player.getUniqueId()), player, string, false);
    }

    private static final Pattern UNIFIED_PLACEHOLDER_PATTERN = Pattern.compile("%([a-zA-Z0-9_]+)%");

    @Contract("_, _, null, _ -> null; _, _, !null, _ -> !null")
    private @Nullable String replacePlaceholders(@Nullable GrimPlayer grimPlayer, @Nullable PlatformPlayer platformPlayer, @Nullable String string, boolean removeFormatting) {
        if (string == null) return null;

        if (string.indexOf('%') == -1) {
            return string;
        }

        final Matcher matcher = UNIFIED_PLACEHOLDER_PATTERN.matcher(string);

        if (!matcher.find()) {
            return GrimAPI.INSTANCE.getMessagePlaceHolderManager().replacePlaceholders(platformPlayer, string);
        }

        final Map<String, String> staticReplacements = GrimAPI.INSTANCE.getExternalAPI().getStaticReplacements();
        final Map<String, Function<GrimUser, String>> variableReplacements = GrimAPI.INSTANCE.getExternalAPI().getVariableReplacements();
        final StringBuilder sb = new StringBuilder(string.length() + 32);

        do {
            final String keyWithPercent = matcher.group(0);
            String value = null;

            String staticValue = staticReplacements.get(keyWithPercent);

            if (staticValue != null) {
                value = staticValue;
            } else if (grimPlayer != null) {
                final Function<GrimUser, String> func = variableReplacements.get(keyWithPercent);
                if (func != null) {
                    value = func.apply(grimPlayer);
                }
            }

            if (value == null) {
                value = keyWithPercent;
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        } while (matcher.find());

        matcher.appendTail(sb);

        String grimReplaced = sb.toString();

        return GrimAPI.INSTANCE.getMessagePlaceHolderManager().replacePlaceholders(platformPlayer, grimReplaced).replace(PLACEHOLDER_ESCAPE_CHAR, '%');
    }

    public @NotNull Component replacePlaceholders(@NotNull GrimPlayer player, @NotNull Component component) {
        final TextReplacementConfig safeReplacement = TextReplacementConfig.builder()
                .match("%[a-zA-Z0-9_]+%")
                .replacement(placeholder -> Component.text(replacePlaceholders(player, placeholder.content())))
                .build();
        return component.replaceText(safeReplacement);
    }

    public @NotNull Component miniMessage(@NotNull String string) {
        string = string.replace("%prefix%", GrimAPI.INSTANCE.getConfigManager().getConfig().getStringElse("prefix", "&#c19a6bᴊᴇꜱᴛᴇʀ &8»"));

        Matcher matcher = HEX_PATTERN.matcher(string);
        StringBuilder sb = new StringBuilder(string.length());

        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(0).replaceAll("[&§#x]", "") + ">");
        }

        string = matcher.appendTail(sb).toString();

        matcher = BARE_HEX_PATTERN.matcher(string);
        sb = new StringBuilder(string.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<" + matcher.group(0) + ">");
        }
        string = matcher.appendTail(sb).toString();

        string = translateAlternateColorCodes('&', string)
                .replace("§0", "<!b><!i><!u><!st><!obf><black>")
                .replace("§1", "<!b><!i><!u><!st><!obf><dark_blue>")
                .replace("§2", "<!b><!i><!u><!st><!obf><dark_green>")
                .replace("§3", "<!b><!i><!u><!st><!obf><dark_aqua>")
                .replace("§4", "<!b><!i><!u><!st><!obf><dark_red>")
                .replace("§5", "<!b><!i><!u><!st><!obf><dark_purple>")
                .replace("§6", "<!b><!i><!u><!st><!obf><gold>")
                .replace("§7", "<!b><!i><!u><!st><!obf><gray>")
                .replace("§8", "<!b><!i><!u><!st><!obf><dark_gray>")
                .replace("§9", "<!b><!i><!u><!st><!obf><blue>")
                .replace("§a", "<!b><!i><!u><!st><!obf><green>")
                .replace("§b", "<!b><!i><!u><!st><!obf><aqua>")
                .replace("§c", "<!b><!i><!u><!st><!obf><red>")
                .replace("§d", "<!b><!i><!u><!st><!obf><light_purple>")
                .replace("§e", "<!b><!i><!u><!st><!obf><yellow>")
                .replace("§f", "<!b><!i><!u><!st><!obf><white>")
                .replace("§r", "<reset>")
                .replace("§k", "<obfuscated>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>");

        return MiniMessage.miniMessage().deserialize(string).compact();
    }

    public @NotNull String toNativeText(@NotNull String string) {
        if (string.indexOf('<') == -1 && string.indexOf('&') == -1
                && string.indexOf('§') == -1 && string.indexOf('#') == -1) {
            return string;
        }
        return LegacyComponentSerializer.legacySection().serialize(miniMessage(string));
    }

    public Component getParsedComponent(Sender sender, String key, String fallbackText) {
        String message = GrimAPI.INSTANCE.getConfigManager().getConfig().getStringElse(key, fallbackText);
        message = MessageUtil.replacePlaceholders(sender, message);
        return MessageUtil.miniMessage(message);
    }

    @Contract("_, _ -> new")
    public static @NotNull String translateAlternateColorCodes(char altColorChar, @NotNull String textToTranslate) {
        char[] b = textToTranslate.toCharArray();

        for (int i = 0; i < b.length - 1; ++i) {
            if (b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = 167;
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }

        return new String(b);
    }

    @Contract("!null -> !null; null -> null")
    public static @Nullable String stripColor(@Nullable String input) {
        return input == null ? null : STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }
}
