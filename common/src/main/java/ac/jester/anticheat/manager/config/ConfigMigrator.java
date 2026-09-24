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
import java.util.LinkedHashMap;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigMigrator {
    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)('?[^'\\s#][^:]*?'?):(\\s*)(.*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)-\\s?.*$");

    public static final String VERSION_KEY = "config-version";

    private record Relocation(int version, String fromFile, String toFile, List<String> keys) {
    }

    private static final List<Relocation> RELOCATIONS = List.of(
            new Relocation(2, "config.yml", "messages.yml", List.of(
                    "alerts-enabled", "alerts-disabled",
                    "verbose-enabled", "verbose-disabled",
                    "brands-enabled", "brands-disabled",
                    "help"))
    );

    private record DefaultChange(int version, String path, double oldDefault) {
    }

    private static final List<DefaultChange> DEFAULT_CHANGES = List.of(
            new DefaultChange(3, "alert-defaults.minimum-tps", 19.5),
            new DefaultChange(3, "checks.MovementA.minimum-tps", 19.5),
            new DefaultChange(3, "checks.Fly.minimum-tps", 19.0),
            new DefaultChange(3, "checks.Reach.dont-alert-until", 4)
    );

    private ConfigMigrator() {
    }

    public static void migrate(File userFile, String resourcePath) {
        migrate(userFile, resourcePath, Map.of());
    }

    public static Map<String, Object> collectRelocated(File fromFile, String toFileName) {
        Map<String, Object> carried = new LinkedHashMap<>();
        if (!fromFile.exists()) return carried;
        try {
            String text = new String(Files.readAllBytes(fromFile.toPath()), StandardCharsets.UTF_8);
            Object loaded = new Yaml().load(text);
            if (!(loaded instanceof Map<?, ?> raw)) return carried;
            @SuppressWarnings("unchecked") Map<String, Object> user = (Map<String, Object>) raw;
            int userVersion = versionOf(user);
            for (Relocation r : RELOCATIONS) {
                if (r.version() <= userVersion) continue;
                if (!r.fromFile().equals(fromFile.getName()) || !r.toFile().equals(toFileName)) continue;
                for (String key : r.keys()) {
                    if (user.get(key) != null) carried.put(key, user.get(key));
                }
            }
        } catch (Exception e) {
            LogUtil.error("Could not read " + fromFile.getName() + " to carry moved keys over", e);
        }
        return carried;
    }

    private static int versionOf(Map<String, Object> map) {
        Object v = map == null ? null : map.get(VERSION_KEY);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public static void migrate(File userFile, String resourcePath, Map<String, Object> carried) {
        if (!userFile.exists()) {
            if (carried.isEmpty()) return;
            try (InputStream in = GrimAPI.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) return;
                userFile.getParentFile().mkdirs();
                Files.copy(in, userFile.toPath());
            } catch (Exception e) {
                LogUtil.error("Could not create " + userFile.getName() + " for moved keys", e);
                return;
            }
        }

        try {
            String defaultText;
            try (InputStream in = GrimAPI.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) return;
                defaultText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String userText = new String(Files.readAllBytes(userFile.toPath()), StandardCharsets.UTF_8);

            defaultText = defaultText.replace("\r\n", "\n").replace("\r", "\n");
            userText = userText.replace("\r\n", "\n").replace("\r", "\n");

            Yaml yaml = new Yaml();
            Map<String, Object> defaultMap = yaml.load(defaultText);
            Map<String, Object> userMap = yaml.load(userText);
            if (defaultMap == null || userMap == null) return;

            int userVersion = versionOf(userMap);
            int defaultVersion = versionOf(defaultMap);
            boolean outdated = userVersion < defaultVersion;
            if (!outdated && carried.isEmpty() && !hasMissingKeys(defaultMap, userMap)) return;

            Map<String, Object> values = new LinkedHashMap<>(userMap);
            values.putAll(carried);
            values.remove(VERSION_KEY);
            int reset = 0;
            for (DefaultChange change : DEFAULT_CHANGES) {
                if (change.version() <= userVersion) continue;
                if (removeIfEquals(values, change.path(), change.oldDefault())) reset++;
            }
            String merged = mergeIntoDefault(defaultText, values);

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

            LogUtil.info("Migrated " + userFile.getName()
                    + (outdated ? " from layout v" + userVersion + " to v" + defaultVersion : " to the latest format")
                    + (carried.isEmpty() ? "" : ", keeping " + carried.size() + " value(s) moved here from another file")
                    + (reset == 0 ? "" : ", updating " + reset + " setting(s) still at an old default")
                    + " (your values were kept; previous file saved as " + backup.getName() + ")");
        } catch (Exception e) {
            LogUtil.error("Failed to migrate " + userFile.getName() + " — leaving it unchanged", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean removeIfEquals(Map<String, Object> root, String path, double expected) {
        String[] parts = path.split("\\.");
        Map<String, Object> node = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = node.get(parts[i]);
            if (!(next instanceof Map)) return false;
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) next);
            node.put(parts[i], copy);
            node = copy;
        }
        Object value = node.get(parts[parts.length - 1]);
        if (!(value instanceof Number n) || Math.abs(n.doubleValue() - expected) > 1e-9) return false;
        node.remove(parts[parts.length - 1]);
        return true;
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

    private static String mergeIntoDefault(String defaultText, Map<String, Object> userMap) {
        String[] lines = defaultText.split("\n", -1);
        StringBuilder out = new StringBuilder(defaultText.length() + 512);

        Deque<int[]> indents = new ArrayDeque<>();
        Deque<String> path = new ArrayDeque<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

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

            while (!indents.isEmpty() && indents.peek()[0] >= indent) {
                indents.pop();
                path.pop();
            }
            indents.push(new int[]{indent});
            path.push(key);

            Object userValue = lookup(userMap, path);

            boolean hasInlineScalar = !inlineValue.isEmpty() && !inlineValue.startsWith("#");
            boolean defaultIsListBlock = inlineValue.isEmpty() && nextContentIsListItem(lines, i, indent);

            if (defaultIsListBlock && userValue instanceof List && isScalarList((List<?>) userValue)) {
                out.append(line).append('\n');
                int j = i + 1;
                while (j < lines.length) {
                    String peek = lines[j].trim();
                    if (peek.isEmpty() || peek.startsWith("#")) {
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
                String comment = "";
                int hash = findTrailingComment(inlineValue);
                if (hash != -1) comment = "    " + inlineValue.substring(hash);
                out.append(m.group(1)).append(m.group(2)).append(':').append(' ')
                        .append(scalarToYaml(userValue)).append(comment).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }

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
