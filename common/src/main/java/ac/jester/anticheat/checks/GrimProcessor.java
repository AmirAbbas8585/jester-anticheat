package ac.jester.anticheat.checks;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.AbstractProcessor;
import ac.grim.grimac.api.config.ConfigReloadable;
import ac.jester.anticheat.utils.common.ConfigReloadObserver;

public abstract class GrimProcessor implements AbstractProcessor, ConfigReloadable, ConfigReloadObserver {
    @Override
    public void reload() {
        reload(GrimAPI.INSTANCE.getConfigManager().getConfig());
    }
}
