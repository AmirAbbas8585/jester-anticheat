package ac.jester.anticheat.hooks.impl;

import ac.jester.anticheat.hooks.PluginHook;

public final class LPXHook implements PluginHook {
    private boolean available = false;

    @Override
    public String getPluginName() { return "LPX"; }

    @Override
    public void onEnable() {
        available = true;
    }

    public boolean isActive() { return available; }
}
