package ac.jester.anticheat.checks.impl.player;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityHook;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import java.util.List;

@CheckData(name = "AutoFish", description = "Reeling in fishing rod immediately after fish bite")
public final class AutoFish extends Check implements PacketCheck {
    private static final int BITING_META_INDEX = resolveBitingIndex();

    private static int resolveBitingIndex() {
        var version = com.github.retrooper.packetevents.PacketEvents.getAPI().getServerManager().getVersion();
        if (version.isOlderThan(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_16)) return -1;
        if (version.isOlderThanOrEquals(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_16_5)) return 8;
        return 9;
    }

    private long lastBiteTime = 0L;
    private int minReactMs = 150;

    public AutoFish(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minReactMs = config.getIntElse("AutoFish.min-react-ms", 150);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (BITING_META_INDEX < 0 || event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(event);
        if (!(player.compensatedEntities.entityMap.get(meta.getEntityId()) instanceof PacketEntityHook hook)
                || hook.owner != player.entityID) {
            return;
        }

        List<EntityData<?>> dataList = meta.getEntityMetadata();
        if (dataList == null) return;
        for (EntityData<?> data : dataList) {
            if (data.getIndex() == BITING_META_INDEX && data.getValue() instanceof Boolean biting) {
                if (biting) lastBiteTime = System.currentTimeMillis();
                else lastBiteTime = 0L;
                break;
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;
        if (lastBiteTime == 0L) return;

        var held = player.inventory.getHeldItem();
        var offhand = player.inventory.getOffHand();
        boolean holdingRod = (held != null && held.getType() == ItemTypes.FISHING_ROD)
                || (offhand != null && offhand.getType() == ItemTypes.FISHING_ROD);
        if (!holdingRod) return;

        long elapsed = System.currentTimeMillis() - lastBiteTime;
        lastBiteTime = 0L;

        if (!player.isTickingReliablyFor(3)) return;
        if (player.getTransactionPing() > 500) return;

        if (elapsed < minReactMs) {
            flagAndAlert(String.format("react=%dms min=%dms ping=%dms",
                    elapsed, minReactMs, player.getTransactionPing()));
        }
    }
}
