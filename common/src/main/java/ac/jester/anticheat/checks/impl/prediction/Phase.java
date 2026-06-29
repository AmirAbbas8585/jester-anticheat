package ac.jester.anticheat.checks.impl.prediction;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PostPredictionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PredictionComplete;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

import java.util.ArrayList;
import java.util.List;

@CheckData(name = "Phase", setback = 1, decay = 0.005)
public class Phase extends Check implements PostPredictionCheck {
    private SimpleCollisionBox oldBB;

    public Phase(GrimPlayer player) {
        super(player);
        oldBB = player.boundingBox;
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        // Post-join / resource-pack load: position can desync into a block while
        // chunks/blocks resync — don't flag phase during that transient
        if (player.inJoinOrLoadGrace()) {
            oldBB = player.boundingBox;
            return;
        }

        if (!player.getSetbackTeleportUtil().blockOffsets && !predictionComplete.getData().isTeleport() && predictionComplete.isChecked()) { // Not falling through world
            SimpleCollisionBox newBB = player.boundingBox;

            List<SimpleCollisionBox> boxes = new ArrayList<>();
            Collisions.getCollisionBoxes(player, newBB, boxes, false);

            for (SimpleCollisionBox box : boxes) {
                if (newBB.isIntersected(box) && !oldBB.isIntersected(box)) {
                    WrappedBlockState state = player.compensatedWorld.getBlock((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);

                    // Shulker boxes grow their collision box upward when opened (the
                    // lid rises ~0.5 block). A player standing on top is then
                    // intersected by the grown box without having moved into it —
                    // that's the block changing shape, not the player phasing.
                    if (BlockTags.SHULKER_BOXES.contains(state.getType())) {
                        continue;
                    }

                    if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) {
                        if (BlockTags.ANVIL.contains(state.getType()) || state.getType() == StateTypes.CHEST || state.getType() == StateTypes.TRAPPED_CHEST) {
                            continue; // 1.8 glitchy block, ignore
                        }
                    }
                    flagAndAlertWithSetback();
                    return;
                }
            }
        }

        oldBB = player.boundingBox;
        reward();
    }
}
