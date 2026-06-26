package ac.jester.anticheat.manager;

/**
 * Shared, thread-safe flag set by the platform update checker and read on the
 * packet thread when a player joins. Kept in the common module so the join
 * handler can notify staff without depending on platform code.
 */
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
