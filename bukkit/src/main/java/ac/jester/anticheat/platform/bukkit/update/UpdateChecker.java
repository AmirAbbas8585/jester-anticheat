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

public final class UpdateChecker {
    private static final String UPDATE_URL =
            "https://api.modrinth.com/v2/project/jester-anticheat/version";

    private static final Pattern LEADING_VERSION = Pattern.compile("^(\\d+(?:\\.\\d+)*)");
    private static final Pattern MODRINTH_VERSION =
            Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private ScheduledExecutorService executor;
    private volatile String lastAnnounced = "";

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var cfg = GrimAPI.INSTANCE.getConfigManager().getConfig();
        if (!cfg.getBooleanElse("update-checker.enabled", true)) return;

        final String current = plugin.getDescription().getVersion();
        final int intervalMinutes = Math.max(0, cfg.getIntElse("update-checker.interval-minutes", 180));

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Jester-UpdateChecker");
            t.setDaemon(true);
            return t;
        });
        Runnable task = () -> check(UPDATE_URL, current);
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
            String latest = extractLatest(fetch(url));
            if (latest == null || latest.isEmpty()) return;
            boolean newer = isNewer(latest, current);
            UpdateState.set(newer, current, latest);
            if (newer) {
                LogUtil.info("A new version is available: " + latest + " (current: " + current + ").");
                if (!latest.equals(lastAnnounced)) {
                    lastAnnounced = latest;
                    notifyOnlineStaff();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyOnlineStaff() {
        try {
            var cfg = GrimAPI.INSTANCE.getConfigManager().getConfig();
            String perm = cfg.getStringElse("update-checker.notify-permission", "jester.update");
            String msg = cfg.getStringElse("update-available",
                            "%prefix% <yellow>A new version is available: <green>%latest%</green> <gray>(running <red>%current%</red>)")
                    .replace("%latest%", UpdateState.latest())
                    .replace("%current%", UpdateState.current());
            for (org.bukkit.entity.Player online : plugin.getServer().getOnlinePlayers()) {
                var platformPlayer = GrimAPI.INSTANCE.getPlatformPlayerFactory().getFromNativePlayerType(online);
                if (platformPlayer.hasPermission(perm, false)) {
                    platformPlayer.sendMessage(ac.jester.anticheat.utils.anticheat.MessageUtil.miniMessage(msg));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent",
                "AmirAbbas8585/jester-anticheat (Modrinth update check)");
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) body.append(line).append('\n');
            return body.toString();
        } finally {
            con.disconnect();
        }
    }

    static String extractLatest(String body) {
        if (body == null || body.isBlank()) return null;
        Matcher m = MODRINTH_VERSION.matcher(body);
        String best = null;
        while (m.find()) {
            String v = m.group(1);
            if (best == null || isNewer(v, best)) best = v;
        }
        if (best != null) return best;
        for (String line : body.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

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
