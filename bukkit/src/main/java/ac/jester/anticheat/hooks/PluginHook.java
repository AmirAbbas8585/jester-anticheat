package ac.jester.anticheat.hooks;

/**
 * Base interface for all plugin compatibility hooks.
 * Each hook is responsible for detecting its plugin and setting up integration.
 */
public interface PluginHook {

    /** @return the plugin name this hook targets */
    String getPluginName();

    /** Called when the plugin is detected. Set up listeners, API hooks, etc. */
    void onEnable();

    /**
     * @return true if the hook actually initialized and is operational. Used by
     * the manager to report honest status — a hook whose API reflection failed
     * must return false here so it isn't reported as "hooked" when it's inert.
     */
    default boolean isAvailable() { return true; }

    /** Called on plugin reload */
    default void onReload() {}

    /** Called when disabling. Clean up resources. */
    default void onDisable() {}
}
