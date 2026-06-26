package ac.jester.anticheat.manager.config;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic config migration.
 *
 * On every startup/reload, compares the user's config file against the default
 * bundled in the jar. If the default contains keys the user file is missing
 * (i.e. the plugin was updated), the file is regenerated FROM THE DEFAULT —
 * keeping all new keys and comments — with the user's existing values written
 * over the default values. The previous file is backed up as `<name>.bak`.
 *
 * If no keys are missing, the user file is left completely untouched.
 *
 * Strategy: walk the default file line by line, tracking the YAML key path via
 * indentation. Scalar values and list blocks whose path exists in the user's
 * file are replaced with the user's values; everything else (new keys, all
 * comments, ordering) comes from the default.
 */
public final class ConfigMigrator {

    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)('?[^'\\s#][^:]*?'?):(\\s*)(.*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)-\\s?.*$");

    private ConfigMigrator() {
    }

    /**
     * @param userFile     e.g. plugins/JesterAntiCheat/config.yml
     * @param resourcePath bundled default, e.g. "config/en.yml"
     */
    public static void migrate(File userFile, String resourcePath) {
        if (!userFile.exists()) return; // fresh install — defaults get saved as-is

        try {
            String defaultText;
            try (InputStream in = GrimAPI.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) return;
                defaultText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String userText = new String(Files.readAllBytes(userFile.toPath()), StandardCharsets.UTF_8);

            // The bundled config resources are saved with Windows CRLF line endings.
            // mergeIntoDefault splits on "\n" only, so a leftover "\r" on every line
            // made the KEY_LINE regex's matches() fail on EVERY line (the trailing \r
            // was never consumed) — every line silently fell through to "pass through
            // unchanged", meaning NO user value was ever substituted and the output
            // was effectively just the raw bundled default. This is what caused the
            // entire config to reset on every update. Normalizing to LF before any
            // line-based processing fixes it; LF-only YAML is valid and reads fine in
            // any editor.
            defaultText = defaultText.replace("\r\n", "\n").replace("\r", "\n");
            userText = userText.replace("\r\n", "\n").replace("\r", "\n");

            Yaml yaml = new Yaml();
            Map<String, Object> defaultMap = yaml.load(defaultText);
            Map<String, Object> userMap = yaml.load(userText);
            if (defaultMap == null || userMap == null) return;

            if (!hasMissingKeys(defaultMap, userMap)) return; // up to date — don't touch

            String merged = mergeIntoDefault(defaultText, userMap);

            // Safety: never write output that doesn't parse — a malformed merge
            // would break plugin startup entirely. Abort and keep the user file.
            try {
                Object parsed = new Yaml().load(merged);
                if (!(parsed instanceof Map)) throw new IllegalStateException("merge produced non-map YAML");
            } catch (Exception parseError) {
                LogUtil.error("Config migration of " + userFile.getName()
                        + " produced invalid YAML — keeping your existing file", parseError);
                return;
            }

            File backup = new File(userFile.getParentFile(), userFile.getName() + ".bak");
            Files.copy(userFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.write(userFile.toPath(), merged.getBytes(StandardCharsets.UTF_8));

            LogUtil.info("Migrated " + userFile.getName() + " to the latest format "
                    + "(your values were kept; previous file saved as " + backup.getName() + ")");
        } catch (Exception e) {
            LogUtil.error("Failed to migrate " + userFile.getName() + " — leaving it unchanged", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasMissingKeys(Map<String, Object> defaults, Map<String, Object> user) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!user.containsKey(entry.getKey())) return true;
            Object defValue = entry.getValue();
            Object userValue = user.get(entry.getKey());
            if (defValue instanceof Map && userValue instanceof Map) {
                if (hasMissingKeys((Map<String, Object>) defValue, (Map<String, Object>) userValue)) return true;
            }
        }
        return false;
    }

    /** Rebuilds the default text with the user's values substituted in. */
    private static String mergeIntoDefault(String defaultText, Map<String, Object> userMap) {
        String[] lines = defaultText.split("\n", -1);
        StringBuilder out = new StringBuilder(defaultText.length() + 512);

        // Stack of (indent, key) describing the current YAML path
        Deque<int[]> indents = new ArrayDeque<>();   // indent widths
        Deque<String> path = new ArrayDeque<>();     // keys, top of stack = deepest

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // Pass through blanks, comments, and document markers untouched
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---")) {
                out.append(line).append('\n');
                continue;
            }

            Matcher m = KEY_LINE.matcher(line);
            if (!m.matches() || LIST_ITEM.matcher(line).matches()) {
                out.append(line).append('\n');
                continue;
            }

            int indent = m.group(1).length();
            String key = stripQuotes(m.group(2).trim());
            String inlineValue = m.group(4);

            // Unwind the path to this line's depth
            while (!indents.isEmpty() && indents.peek()[0] >= indent) {
                indents.pop();
                path.pop();
            }
            indents.push(new int[]{indent});
            path.push(key);

            Object userValue = lookup(userMap, path);

            boolean hasInlineScalar = !inlineValue.isEmpty() && !inlineValue.startsWith("#");
            boolean defaultIsListBlock = inlineValue.isEmpty() && nextContentIsListItem(lines, i, indent);

            // The list-replacement branch below only knows how to dump SCALAR list
            // items (strings/numbers/booleans). A list of MAPS (e.g. the legacy
            // "Punishments:" block, where each "- " item has its own nested
            // checks/commands/remove-violations-after keys) would get each item
            // Map.toString()'d into a single garbage scalar AND leave the original
            // nested key lines orphaned afterward, producing invalid YAML. Treat
            // those exactly like punishments.yml: leave them untouched by falling
            // through to the default-passthrough branch.
            if (defaultIsListBlock && userValue instanceof List && isScalarList((List<?>) userValue)) {
                // Replace the entire default list block with the user's list
                out.append(line).append('\n');
                int j = i + 1;
                while (j < lines.length) {
                    String peek = lines[j].trim();
                    if (peek.isEmpty() || peek.startsWith("#")) {
                        // keep skipping only if a list item still follows
                        if (!nextContentIsListItem(lines, j - 1, indent)) break;
                        j++;
                        continue;
                    }
                    if (LIST_ITEM.matcher(lines[j]).matches()
                            && leadingSpaces(lines[j]) > indent - 1) {
                        j++;
                    } else {
                        break;
                    }
                }
                String itemIndent = " ".repeat(indent + 4);
                for (Object item : (List<?>) userValue) {
                    out.append(itemIndent).append("- ").append(scalarToYaml(item)).append('\n');
                }
                i = j - 1;
            } else if (hasInlineScalar && userValue != null
                    && !(userValue instanceof Map) && !(userValue instanceof List)) {
                // Scalar: keep the default line's key/comment shape, swap the value
                String comment = "";
                // preserve a trailing comment if the default line had one after the value
                int hash = findTrailingComment(inlineValue);
                if (hash != -1) comment = "    " + inlineValue.substring(hash);
                out.append(m.group(1)).append(m.group(2)).append(':').append(' ')
                        .append(scalarToYaml(userValue)).append(comment).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }

        // Trim the extra trailing newline introduced by split(-1) handling
        if (out.length() >= 2 && out.charAt(out.length() - 1) == '\n' && defaultText.endsWith("\n")) {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    private static boolean isScalarList(List<?> list) {
        for (Object o : list) {
            if (o instanceof Map || o instanceof List) return false;
        }
        return true;
    }

    private static boolean nextContentIsListItem(String[] lines, int index, int parentIndent) {
        for (int j = index + 1; j < lines.length; j++) {
            String t = lines[j].trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            return LIST_ITEM.matcher(lines[j]).matches() && leadingSpaces(lines[j]) > parentIndent - 1;
        }
        return false;
    }

    private static int leadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') n++;
        return n;
    }

    /** Index of a trailing ' #' comment outside quotes, or -1. */
    private static int findTrailingComment(String value) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '#' && !inSingle && !inDouble && i > 0 && value.charAt(i - 1) == ' ') return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static Object lookup(Map<String, Object> map, Deque<String> reversedPath) {
        // path stack has deepest key on top — walk from the bottom
        Object current = map;
        Object[] keys = reversedPath.toArray();
        for (int i = keys.length - 1; i >= 0; i--) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(String.valueOf(keys[i]));
            if (current == null) return null;
        }
        return current;
    }

    private static String stripQuotes(String key) {
        if (key.length() >= 2 && key.startsWith("'") && key.endsWith("'")) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    private static String scalarToYaml(Object value) {
        if (value == null) return "''";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        DumperOptions options = new DumperOptions();
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.SINGLE_QUOTED);
        String dumped = new Yaml(options).dump(value.toString()).trim();
        return dumped;
    }
}
