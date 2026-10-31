package ac.jester.anticheat.utils.data.webhook.discord;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.GrimUser;
import ac.jester.anticheat.player.GrimPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CompiledDiscordTemplate(Segment[] segments) {
    private enum MarkdownContext {
        NORMAL,
        INLINE_CODE,
        CODE_BLOCK
    }

    public enum EscapeMode {
        FULL_MARKDOWN,
        CODE_SPAN,
        NONE
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");

    private sealed interface Segment permits Literal, Placeholder {}

    private record Literal(String text) implements Segment {}

    private record Placeholder(String key, EscapeMode mode) implements Segment {}

    public static CompiledDiscordTemplate compile(String template) {
        List<Segment> parts = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        MarkdownContext ctx = MarkdownContext.NORMAL;
        int lastEnd = 0;

        while (m.find()) {
            String gap = template.substring(lastEnd, m.start());
            if (!gap.isEmpty()) parts.add(new Literal(gap));

            ctx = advanceContext(ctx, gap);

            EscapeMode mode = switch (ctx) {
                case NORMAL -> EscapeMode.FULL_MARKDOWN;
                case INLINE_CODE,
                     CODE_BLOCK -> EscapeMode.CODE_SPAN;
            };
            parts.add(new Placeholder(m.group(0), mode));
            lastEnd = m.end();
        }

        if (lastEnd < template.length()) {
            parts.add(new Literal(template.substring(lastEnd)));
        }
        return new CompiledDiscordTemplate(parts.toArray(Segment[]::new));
    }

    public String render(@NotNull GrimPlayer player,
                         @NotNull Map<String, String> statics,
                         @NotNull Map<String, Function<GrimUser, String>> dynamics,
                         char backtickReplacement) {
        StringBuilder sb = new StringBuilder(segments.length * 32);
        for (Segment seg : segments) {
            if (seg instanceof Literal l) {
                sb.append(l.text);
            } else if (seg instanceof Placeholder p) {
                String val = statics.get(p.key);

                if (val == null) {
                    Function<GrimUser, String> fn = dynamics.get(p.key);
                    if (fn != null) val = fn.apply(player);
                }

                if (val == null) {
                    String resolved = GrimAPI.INSTANCE.getMessagePlaceHolderManager()
                            .replacePlaceholders(player.platformPlayer, p.key);
                    if (!resolved.equals(p.key)) val = resolved;
                }

                if (val != null) {
                    sb.append(escape(val, p.mode, backtickReplacement));
                } else {
                    sb.append(p.key);
                }
            }
        }
        return sb.toString();
    }

    private static String escape(String value, EscapeMode mode, char backtickReplacement) {
        if (mode == EscapeMode.NONE) return value;
        if (mode == EscapeMode.CODE_SPAN) return escapeCodeSpan(value, backtickReplacement);
        return escapeMarkdown(value);
    }

    public static String escapeMarkdown(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '`' -> sb.append("\\`");
                case '*' -> sb.append("\\*");
                case '_' -> sb.append("\\_");
                case '~' -> sb.append("\\~");
                case '|' -> sb.append("\\|");
                case '[' -> sb.append("\\[");
                case ']' -> sb.append("\\]");
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case ':' -> sb.append("\\:");
                case '<' -> sb.append("\\<");
                case '#' -> sb.append("\\#");
                case '>' -> sb.append("\\>");
                case '-' -> sb.append("\\-");
                case '.' -> sb.append("\\.");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String escapeCodeSpan(String s, char replacement) {
        if (s == null || s.isEmpty() || replacement == '`') return s;
        return s.replace('`', replacement);
    }

    private static MarkdownContext advanceContext(MarkdownContext ctx, String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (ctx == MarkdownContext.NORMAL) {
                if (c == '\\' && i + 1 < text.length()) {
                    i += 2;
                    continue;
                }
                if (c == '`') {
                    if (i + 2 < text.length()
                            && text.charAt(i + 1) == '`'
                            && text.charAt(i + 2) == '`') {
                        ctx = MarkdownContext.CODE_BLOCK;
                        i += 3;
                        continue;
                    }
                    ctx = MarkdownContext.INLINE_CODE;
                }
            } else if (ctx == MarkdownContext.INLINE_CODE) {
                if (c == '`') ctx = MarkdownContext.NORMAL;
            } else {
                if (c == '`'
                        && i + 2 < text.length()
                        && text.charAt(i + 1) == '`'
                        && text.charAt(i + 2) == '`') {
                    ctx = MarkdownContext.NORMAL;
                    i += 3;
                    continue;
                }
            }
            i++;
        }
        return ctx;
    }
}
