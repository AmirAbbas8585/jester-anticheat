package ac.jester.anticheat.checks.impl.groundspoof;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.predictionengine.GhostBlockDetector;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import ac.jester.anticheat.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

import java.util.ArrayList;
import java.util.List;

@CheckData(name = "NoFall", setback = 10)
public class NoFall extends Check implements PacketCheck {
    public boolean flipPlayerGroundStatus = false;

    public NoFall(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING || event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
            if (player.getSetbackTeleportUtil().insideUnloadedChunk()) return;
            if (player.getSetbackTeleportUtil().blockOffsets) return;

            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);

            if (wrapper.isOnGround() && !wrapper.hasPositionChanged()) {
                if (!isNearGround(wrapper.isOnGround())) {
                    if (!GhostBlockDetector.isGhostBlock(player)) flagAndAlertWithSetback();
                    if (shouldModifyPackets()) {
                        wrapper.setOnGround(false);
                        event.markForReEncode(true);
                    }
                }
            }
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            if (flipPlayerGroundStatus) {
                flipPlayerGroundStatus = false;
                if (shouldModifyPackets()) {
                    wrapper.setOnGround(!wrapper.isOnGround());
                    event.markForReEncode(true);
                }
            }
            if (player.packetStateData.lastPacketWasTeleport) {
                if (shouldModifyPackets()) {
                    wrapper.setOnGround(false);
                    event.markForReEncode(true);
                }
            }
        }
    }

    private boolean isNearGround(boolean onGround) {
        if (onGround) {
            SimpleCollisionBox feetBB = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.x, player.y, player.z, 0.6f, 0.001f);
            feetBB.expand(player.getMovementThreshold());

            return checkForBoxes(feetBB);
        }
        return true;
    }

    private boolean checkForBoxes(SimpleCollisionBox playerBB) {
        List<SimpleCollisionBox> boxes = new ArrayList<>();
        Collisions.getCollisionBoxes(player, playerBB, boxes, false);

        for (SimpleCollisionBox box : boxes) {
            if (playerBB.collidesVertically(box)) {
                return true;
            }
        }

        return player.compensatedWorld.isNearHardEntity(playerBB.copy().expand(4));
    }
}
