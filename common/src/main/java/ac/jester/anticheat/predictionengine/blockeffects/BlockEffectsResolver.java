package ac.jester.anticheat.predictionengine.blockeffects;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.math.Vector3dm;

import java.util.List;

public interface BlockEffectsResolver {
    void applyEffectsFromBlocks(GrimPlayer player, Vector3dm clientVelocity, boolean onlyApplyVelocity, List<GrimPlayer.Movement> movements);
}
