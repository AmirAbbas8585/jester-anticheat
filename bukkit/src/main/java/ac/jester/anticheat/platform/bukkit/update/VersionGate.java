package ac.jester.anticheat.platform.bukkit.update;

import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hard, startup-only version gate (remote kill switch). Runs ONCE during
 * onEnable, before anything else initialises, and is NOT run again while the
 * server is up. The endpoint is hardcoded (not configurable) so a server owner
 * can't point it elsewhere.
 *
 * The plugin refuses to enable when:
 *   - the running version is in the killed list, or below the published minimum, OR
 *   - the gate server cannot be reached (fail-CLOSED — if we can't verify, we
 *     don't run).
 *
 * Endpoint format (plain text, one directive per line):
 *   min=0.0.3
 *   killed=0.0.2,0.0.4
 *
 * Set {@link #GATE_URL} to your own endpoint before publishing. While it's empty
 * the gate is skipped, so development / private builds are unaffected.
 *
 * Note: this is a soft control — a determined user can block the domain or patch
 * the jar. It deters honest misuse; it is not unbreakable DRM.
 */
public final class VersionGate {

    // Hardcoded on purpose so it can't be overridden from config. Replace with
    // your own plain-text endpoint before releasing on Modrinth. Empty disables
    // the gate entirely (so current private/dev builds still run).
    private static final String GATE_URL = "";

    private static final Pattern LEADING_VERSION = Pattern.compile("^(\\d+(?:\\.\\d+)*)");

    private VersionGate() {
    }

    /** @return true if the plugin must NOT enable. */
    public static boolean isBlocked(JavaPlugin plugin) {
        if (GATE_URL == null || GATE_URL.isBlank()) return false; // gate off

        String current = leading(plugin.getDescription().getVersion());

        String body;
        try {
            body = fetch(GATE_URL);
        } catch (Exception unreachable) {
            // fail-closed: we couldn't verify this build, so we don't run it.
            LogUtil.error("Could not reach the version server to verify this build — refusing to enable. "
                    + "Check the server's internet connection and restart.");
            return true;
        }

        String min = value(body, "min");
        Set<String> killed = set(value(body, "killed"));

        if (current != null && killed.contains(current)) {
            LogUtil.error("This version (" + current + ") has been disabled by the developer. Please update.");
            return true;
        }
        if (current != null && min != null && compare(current, min) < 0) {
            LogUtil.error("This version (" + current + ") is below the required minimum (" + min + "). Please update.");
            return true;
        }
        return false;
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);
        con.setRequestProperty("User-Agent", "JesterAntiCheat-VersionGate");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) sb.append(line).append('\n');
        } finally {
            con.disconnect();
        }
        return sb.toString();
    }

    private static String value(String body, String key) {
        if (body == null) return null;
        Matcher m = Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*(.+?)\\s*$").matcher(body);
        return m.find() ? m.group(1).trim() : null;
    }

    private static Set<String> set(String csv) {
        Set<String> out = new HashSet<>();
        if (csv != null) {
            for (String s : csv.split(",")) {
                String v = leading(s.trim());
                if (v != null) out.add(v);
            }
        }
        return out;
    }

    private static String leading(String v) {
        if (v == null) return null;
        Matcher m = LEADING_VERSION.matcher(v.trim());
        return m.find() ? m.group(1) : null;
    }

    private static int compare(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? parseInt(pa[i]) : 0;
            int y = i < pb.length ? parseInt(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
