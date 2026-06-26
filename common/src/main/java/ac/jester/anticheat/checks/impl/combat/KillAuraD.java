package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * KillAura D — Attack angle check.
 *
 * In vanilla, a player can only hit entities they are facing. The hit ray starts at
 * the player's eye position and extends in the direction of their yaw/pitch. If
 * the entity is not within the player's approximate field of view when the attack
 * packet is sent, the hit is impossible without client modification.
 *
 * We compute the yaw angle between the player's actual look direction and the
 * direction toward the entity they attacked. If the player is looking more than
 * maxAngleDegrees away from the target, it is flagged.
 *
 * False-positive mitigations:
 *  - Conservative threshold (default 90°) — far beyond any legitimate hit angle
 *  - Skip on high ping (entity position tracking becomes unreliable)
 *  - Skip if entity is not tracked (recently spawned or despawned)
 *  - Skip if entity is very close (<1.5 blocks) where angle math is noisy
 */
@CheckData(name = "KillAura", configName = "KillAuraD",
        description = "Attacking entity while looking significantly away from it (angle check)")
public final class KillAuraD extends Check implements PacketCheck {

    private double maxAngleDeg = 135.0;
    private int maxPingMs = 300;
    private int minConsecutive = 3;

    private int consecutiveBadAngle = 0;

    public KillAuraD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxAngleDeg = config.getDoubleElse("KillAuraD.max-angle-deg", 135.0);
        maxPingMs = config.getIntElse("KillAuraD.max-ping", 300);
        minConsecutive = config.getIntElse("KillAuraD.min-consecutive", 3);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (!player.isTickingReliablyFor(5)) return;
        if (player.getTransactionPing() > maxPingMs) return;

        PacketEntity entity = player.compensatedEntities.entityMap.get(interact.getEntityId());
        if (entity == null) return;

        var entityPos = entity.trackedServerPosition.getPos();

        // Player's eye position
        double eyeX = player.x;
        double eyeY = player.y + player.getEyeHeight();
        double eyeZ = player.z;

        double dx = entityPos.x - eyeX;
        double dy = entityPos.y + 0.9 - eyeY; // approx entity vertical center
        double dz = entityPos.z - eyeZ;

        double dist2d = Math.sqrt(dx * dx + dz * dz);
        double dist3d = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Too close: tracked entity position can lag 1-2 blocks behind a moving
        // target — at short range that error alone produces huge angle deltas
        if (dist3d < 3.0) {
            consecutiveBadAngle = 0;
            return;
        }

        // Expected yaw direction toward target (Minecraft convention)
        double expectedYaw = Math.toDegrees(Math.atan2(-dx, dz));

        // Yaw difference, normalized to [-180, 180]
        double yawDiff = player.yaw - expectedYaw;
        yawDiff = ((yawDiff % 360) + 540) % 360 - 180;
        double absYawDiff = Math.abs(yawDiff);

        if (absYawDiff > maxAngleDeg) {
            consecutiveBadAngle++;
            if (consecutiveBadAngle >= minConsecutive) {
                flagAndAlert(String.format("yawDiff=%.1f° max=%.0f° consecutive=%d dist=%.2f ping=%dms",
                        absYawDiff, maxAngleDeg, consecutiveBadAngle, dist3d, player.getTransactionPing()));
                consecutiveBadAngle = 0;
            }
        } else {
            consecutiveBadAngle = 0;
        }
    }
}
