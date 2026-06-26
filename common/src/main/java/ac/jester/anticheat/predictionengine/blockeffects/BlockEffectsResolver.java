package ac.jester.anticheat.predictionengine.blockeffects;

import ac.jester.anticheat.player.GrimPlayer;

import java.util.List;

public interface BlockEffectsResolver {

    void applyEffectsFromBlocks(GrimPlayer player, List<GrimPlayer.Movement> movements);

}
