package ac.jester.anticheat.manager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class RecentAlerts {
    private static final int MAX_SIZE = 30;

    public record AlertEntry(long timestamp, String checkName, int vl, String verbose) {}

    private final ArrayDeque<AlertEntry> entries = new ArrayDeque<>(MAX_SIZE + 1);

    public void add(String checkName, int vl, String verbose) {
        entries.addLast(new AlertEntry(System.currentTimeMillis(), checkName, vl, verbose));
        if (entries.size() > MAX_SIZE) entries.pollFirst();
    }

    public List<AlertEntry> getAll() {
        List<AlertEntry> list = new ArrayList<>(entries);
        java.util.Collections.reverse(list);
        return list;
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
