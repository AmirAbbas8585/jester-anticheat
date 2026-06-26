package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;

/**
 * Coordination hook with LPX (Low Ping eXploit fix).
 * LPX handles: packet exploits, item NBT bombs, creative exploits, packet flooding.
 * JesterAC must NOT duplicate these checks — instead, we detect LPX and skip our own
 * redundant packet-level exploit checks if LPX is active.
 *
 * This hook is intentionally minimal — LPX runs independently and doesn't need
 * deep integration, just awareness.
 */
public final class LPXHook implements PluginHook {

    private boolean available = false;

    @Override
    public String getPluginName() { return "LPX"; }

    @Override
    public void onEnable() {
        available = true;
    }

    /**
     * Returns true if LPX is active. When true, JesterAC skips its own
     * packet flood, item NBT, and creative exploit checks to avoid duplication.
     */
    public boolean isActive() { return available; }
}
