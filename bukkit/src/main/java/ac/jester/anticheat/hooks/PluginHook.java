package ac.jester.anticheat.hooks;

public interface PluginHook {
    String getPluginName();

    void onEnable();

    default boolean isAvailable() { return true; }

    default void onReload() {}

    default void onDisable() {}
}
