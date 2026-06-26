package ac.jester.anticheat.platform.bukkit.update;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.manager.UpdateState;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight update checker. Fetches the latest version string from a
 * configurable URL — a plain-text endpoint the server owner hosts, e.g. a file
 * whose contents are just "0.0.2" — and, if it's newer than the running version,
 * logs it to the console and lets staff with the notify permission see it when
 * they join (handled in PacketPlayerJoinQuit via {@link UpdateState}).
 *
 * Runs on its own daemon thread, so it never touches the main/region thread and
 * is safe on Folia. Contacts nothing unless update-checker.url is set.
 */
public final class UpdateChecker {

    private static final Pattern LEADING_VERSION = Pattern.compile("^(\\d+(?:\\.\\d+)*)");

    private final JavaPlugin plugin;
    private ScheduledExecutorService executor;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var cfg = GrimAPI.INSTANCE.getConfigManager().getConfig();
        if (!cfg.getBooleanElse("update-checker.enabled", true)) return;

        String url = cfg.getStringElse("update-checker.url", "");
        if (url == null || url.isBlank()) return; // nothing to check against yet

        final String current = plugin.getDescription().getVersion();
        final int intervalMinutes = Math.max(0, cfg.getIntElse("update-checker.interval-minutes", 180));

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Jester-UpdateChecker");
            t.setDaemon(true);
            return t;
        });
        Runnable task = () -> check(url, current);
        if (intervalMinutes > 0) {
            executor.scheduleAtFixedRate(task, 5, intervalMinutes * 60L, TimeUnit.SECONDS);
        } else {
            executor.schedule(task, 5, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        if (executor != null) executor.shutdownNow();
    }

    private void check(String url, String current) {
        try {
            String latest = fetch(url);
            if (latest == null || latest.isEmpty()) return;
            boolean newer = isNewer(latest, current);
            UpdateState.set(newer, current, latest);
            if (newer) {
                LogUtil.info("A new version is available: " + latest + " (current: " + current + ").");
            }
        } catch (Exception ignored) {
            // A network/parse problem must never spam the console or break a tick.
        }
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);
        con.setRequestProperty("User-Agent", "JesterAntiCheat-UpdateChecker");
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line = in.readLine();
            return line == null ? null : line.trim();
        } finally {
            con.disconnect();
        }
    }

    /** True if {@code latest} parses to a strictly higher version than {@code current}. */
    static boolean isNewer(String latest, String current) {
        int[] l = parse(latest);
        int[] c = parse(current);
        if (l == null || c == null) return false;
        int n = Math.max(l.length, c.length);
        for (int i = 0; i < n; i++) {
            int lv = i < l.length ? l[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (lv != cv) return lv > cv;
        }
        return false;
    }

    private static int[] parse(String v) {
        if (v == null) return null;
        Matcher m = LEADING_VERSION.matcher(v.trim());
        if (!m.find()) return null;
        String[] parts = m.group(1).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
