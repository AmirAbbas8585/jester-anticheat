package ac.jester.anticheat.manager;

public final class UpdateState {
    private UpdateState() {
    }

    private static volatile boolean available = false;
    private static volatile String latest = "";
    private static volatile String current = "";

    public static void set(boolean isAvailable, String currentVersion, String latestVersion) {
        current = currentVersion == null ? "" : currentVersion;
        latest = latestVersion == null ? "" : latestVersion;
        available = isAvailable;
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String latest() {
        return latest;
    }

    public static String current() {
        return current;
    }
}
