package ac.jester.anticheat.checks;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.AbstractProcessor;
import ac.grim.grimac.api.config.ConfigReloadable;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import ac.jester.anticheat.utils.common.ConfigReloadObserver;

public abstract class GrimProcessor implements AbstractProcessor, ConfigReloadable, ConfigReloadObserver {
    @Override
    public void reload() {
        reload(GrimAPI.INSTANCE.getConfigManager().getConfig());
    }

    public static boolean isAnimation(PacketTypeCommon packetType) {
        return packetType == PacketType.Play.Client.ANIMATION || packetType == PacketType.Play.Client.PUNCH;
    }
}
