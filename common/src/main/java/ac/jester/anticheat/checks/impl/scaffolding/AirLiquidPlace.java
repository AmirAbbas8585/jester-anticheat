package ac.jester.anticheat.checks.impl.scaffolding;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.change.BlockModification;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3i;

@CheckData(name = "AirLiquidPlace", description = "Placed a block against an invalid support")
public class AirLiquidPlace extends BlockPlaceCheck {
    public AirLiquidPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (player.gamemode == GameMode.CREATIVE) return;

        Vector3i blockPos = place.position;
        StateType placeAgainst = player.compensatedWorld.getBlockType(blockPos.getX(), blockPos.getY(), blockPos.getZ());

        int currentTick = GrimAPI.INSTANCE.getTickManager().currentTick;
        Iterable<BlockModification> blockModifications = player.blockHistory.getRecentModifications((blockModification) -> currentTick - blockModification.tick() < 2
                && blockPos.equals(blockModification.location())
                && (blockModification.cause() == BlockModification.Cause.START_DIGGING || blockModification.cause() == BlockModification.Cause.HANDLE_NETTY_SYNC_TRANSACTION));

        for (BlockModification blockModification : blockModifications) {
            StateType stateType = blockModification.oldBlockContents().getType();
            if (!stateType.isAir() && !Materials.isNoPlaceLiquid(stateType)) {
                return;
            }
        }

        if (placeAgainst.isAir() || Materials.isNoPlaceLiquid(placeAgainst)) {
            if (flagAndAlert() && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
        }
    }

    @Override
    public void onReload(ConfigManager config) {
        this.cancelVL = config.getIntElse(getConfigName() + ".cancelVL", 0);
    }
}
