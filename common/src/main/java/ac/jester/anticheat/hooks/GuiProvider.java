package ac.jester.anticheat.hooks;

import java.util.UUID;

public abstract class GuiProvider {
    private static GuiProvider instance;

    public static void register(GuiProvider provider) {
        instance = provider;
    }

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
