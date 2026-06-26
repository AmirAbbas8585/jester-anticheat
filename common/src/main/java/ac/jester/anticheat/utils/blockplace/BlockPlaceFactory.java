package ac.jester.anticheat.utils.blockplace;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;

public interface BlockPlaceFactory {
    void applyBlockPlaceToWorld(GrimPlayer player, BlockPlace place);
}
