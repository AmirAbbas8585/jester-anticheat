package ac.jester.anticheat.hooks;

import java.util.UUID;

/**
 * Platform bridge for inventory GUIs (the common module has no Bukkit access).
 * The Bukkit platform registers an implementation on startup.
 */
public abstract class GuiProvider {

    private static GuiProvider instance;

    public static void register(GuiProvider provider) {
        instance = provider;
    }

    /** Opens the violation log GUI for the viewer. False if unsupported on this platform. */
    public abstract boolean openViolationLog(UUID viewerUuid, String targetName);

    public static final GuiProvider NOOP = new GuiProvider() {
        @Override
        public boolean openViolationLog(UUID viewerUuid, String targetName) {
            return false;
        }
    };

    public static GuiProvider safe() {
        return instance != null ? instance : NOOP;
    }
}
