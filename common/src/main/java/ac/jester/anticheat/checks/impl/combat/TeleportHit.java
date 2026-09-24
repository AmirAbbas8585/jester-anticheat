package ac.jester.anticheat.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "TeleportHit", description = "Attacked right after an impossible single-packet move (mace fall spoof, teleport aura)")
public final class TeleportHit extends Check implements PacketCheck {
    private double maxMove = 8.0;
    private long windowMs = 150;

    private Vector3d lastPos = null;
    private long lastImpossibleMoveMs = 0;
    private double lastImpossibleDistance = 0;
    private int teleportExcuses = 0;
    private long teleportExcuseUntil = 0;

    public TeleportHit(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxMove = Math.max(6.0, config.getDoubleElse("TeleportHit.max-move", 8.0));
        windowMs = Math.max(50, config.getLongElse("TeleportHit.window-ms", 150));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        var type = event.getPacketType();
        if (type == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            teleportExcuses++;
            teleportExcuseUntil = System.currentTimeMillis() + 3000;
        } else if (type == PacketType.Play.Server.RESPAWN || type == PacketType.Play.Server.JOIN_GAME) {
            lastPos = null;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();

        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            if (player.inVehicle()) {
                lastPos = null;
                return;
            }
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (!flying.hasPositionChanged()) return;
            Vector3d pos = flying.getLocation().getPosition();
            if (lastPos != null) {
                double dx = pos.getX() - lastPos.getX();
                double dy = pos.getY() - lastPos.getY();
                double dz = pos.getZ() - lastPos.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance > maxMove) {
                    long now = System.currentTimeMillis();
                    if (teleportExcuses > 0 && now < teleportExcuseUntil) {
                        teleportExcuses--;
                    } else {
                        lastImpossibleMoveMs = now;
                        lastImpossibleDistance = distance;
                    }
                }
            }
            lastPos = pos;
            if (System.currentTimeMillis() >= teleportExcuseUntil) teleportExcuses = 0;
            return;
        }

        if (type != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (System.currentTimeMillis() - lastImpossibleMoveMs > windowMs) return;

        var held = player.inventory.getHeldItem();
        boolean mace = held != null && held.getType() == ItemTypes.MACE;
        if (flagAndAlert(String.format("move=%.1f blocks weapon=%s ping=%dms",
                lastImpossibleDistance, mace ? "mace" : (held == null ? "hand" : held.getType().getName().getKey()),
                player.getTransactionPing()))
                && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
