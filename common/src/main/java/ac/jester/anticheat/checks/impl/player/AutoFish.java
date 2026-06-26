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

/**
 * AutoFish — detects automatically reeling in after a fish bite.
 *
 * AutoFish mods watch for the server's fish-bite notification (metadata update
 * on the fishing bobber entity where hookedEntityId becomes non-zero) and
 * immediately send a USE_ITEM packet to reel in, within milliseconds.
 *
 * A human player needs at minimum ~150ms of visual reaction time plus the time
 * to physically click the mouse (total ~250ms minimum). AutoFish typically
 * reacts in < 80ms — often 0-20ms.
 *
 * Detection: When the server sends entity metadata for this player's fishing
 * bobber (hookedEntityId field, index 8, becomes > 0), record the bite time.
 * If the player sends USE_ITEM with a fishing rod within minReactMs, flag it.
 *
 * Note: The hookedEntityId field index in Minecraft 1.20+ is index 8 (after
 * the 8 base entity data indices). We check for any OptInt field becoming
 * a positive value on the bobber entity.
 */
@CheckData(name = "AutoFish", description = "Reeling in fishing rod immediately after fish bite")
public final class AutoFish extends Check implements PacketCheck {

    private static final int HOOKED_ENTITY_META_INDEX = 8;

    private long lastBiteTime = 0L;
    private int minReactMs = 150;
    private int activeBobberEntityId = -1;

    public AutoFish(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minReactMs = config.getIntElse("AutoFish.min-react-ms", 150);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        // Track: find our fishing bobber entity ID
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY
                || event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            // Entity spawn handled by CompensatedEntities; we'll look it up lazily
            return;
        }

        // Watch for metadata update on our bobber
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

        // Invalidate stale cache: the previous bobber despawned (missed catch,
        // recast) — without this the check goes blind after the first cast
        if (activeBobberEntityId != -1
                && !(player.compensatedEntities.entityMap.get(activeBobberEntityId) instanceof PacketEntityHook)) {
            activeBobberEntityId = -1;
        }

        // Resolve our bobber entity ID lazily: search entityMap for our hook
        if (activeBobberEntityId == -1) {
            for (var entry : player.compensatedEntities.entityMap.int2ObjectEntrySet()) {
                if (entry.getValue() instanceof PacketEntityHook hook
                        && hook.owner == player.entityID) {
                    activeBobberEntityId = entry.getIntKey();
                    break;
                }
            }
        }
        if (activeBobberEntityId == -1) return;

        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(event);
        if (meta.getEntityId() != activeBobberEntityId) return;

        List<EntityData<?>> dataList = meta.getEntityMetadata();
        if (dataList == null) return;

        for (EntityData<?> data : dataList) {
            if (data.getIndex() == HOOKED_ENTITY_META_INDEX) {
                // Any non-zero/present value in the hooked-entity slot = fish on the hook
                Object value = data.getValue();
                int hookedId = 0;
                if (value instanceof Integer i) hookedId = i;
                else if (value instanceof Number n) hookedId = n.intValue();

                if (hookedId > 0) {
                    lastBiteTime = System.currentTimeMillis();
                }
                break;
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Track bobber despawn (player reeled in or missed)
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            if (lastBiteTime == 0L) return;

            // Check player is holding a fishing rod
            var held = player.inventory.getHeldItem();
            var offhand = player.inventory.getOffHand();
            boolean holdingRod = (held != null && held.getType() == ItemTypes.FISHING_ROD)
                    || (offhand != null && offhand.getType() == ItemTypes.FISHING_ROD);
            if (!holdingRod) return;

            long elapsed = System.currentTimeMillis() - lastBiteTime;
            lastBiteTime = 0L;
            activeBobberEntityId = -1;

            if (!player.isTickingReliablyFor(3)) return;
            if (player.getTransactionPing() > 500) return;

            if (elapsed < minReactMs) {
                flagAndAlert(String.format("react=%dms min=%dms ping=%dms",
                        elapsed, minReactMs, player.getTransactionPing()));
            }
        }
    }
}
