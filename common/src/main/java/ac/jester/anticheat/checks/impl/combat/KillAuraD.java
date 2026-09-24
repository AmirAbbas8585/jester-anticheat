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

        double eyeX = player.x;
        double eyeY = player.y + player.getEyeHeight();
        double eyeZ = player.z;

        double dx = entityPos.x - eyeX;
        double dy = entityPos.y + 0.9 - eyeY;
        double dz = entityPos.z - eyeZ;

        double dist2d = Math.sqrt(dx * dx + dz * dz);
        double dist3d = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist3d < 3.0) {
            consecutiveBadAngle = 0;
            return;
        }

        double expectedYaw = Math.toDegrees(Math.atan2(-dx, dz));

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
