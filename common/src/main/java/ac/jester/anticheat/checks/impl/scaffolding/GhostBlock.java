package ac.jester.anticheat.checks.impl.scaffolding;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.platform.api.world.PlatformWorld;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.change.BlockModification;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3i;

/**
 * GhostBlock — placing a block against a position that is AIR on the server.
 *
 * The classic ghost-block exploit: in a protected region a placement is denied
 * (so the block stays air server-side) but the client keeps rendering it, then
 * the player places ANOTHER block against that "ghost" to keep bridging/building
 * where they shouldn't. Against a ghost the server sees the support as air.
 *
 * We deliberately read the LIVE platform world here, not player.compensatedWorld.
 * A WorldGuard-style deny is only reflected in compensatedWorld once the
 * corrective block-change packet round-trips back out to the client; a player
 * spamming placements against the same denied spot can place the "supported"
 * block before that correction lands, so compensatedWorld still reads solid and
 * the check never fires. The real world updates the instant the placement is
 * denied (same tick, main thread), so it can't be raced.
 *
 * A same-tick instant break / netty resync can legitimately leave the support
 * momentarily air, so those are excused and a short streak is required. This is
 * a focused, separately-configurable signal for ghost-block abuse (the
 * scaffolding AirLiquidPlace check covers the same geometry more broadly).
 * Experimental + alert-only by default.
 */
@CheckData(name = "GhostBlock", experimental = true,
        description = "Placing a block against a position that is air server-side (ghost block)")
public class GhostBlock extends BlockPlaceCheck {

    private int minConsecutive = 2;
    private int consecutive = 0;

    public GhostBlock(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minConsecutive = Math.max(1, config.getIntElse("GhostBlock.min-consecutive", 2));
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (player.gamemode == GameMode.CREATIVE) return;
        if (player.platformPlayer == null) return;

        Vector3i pos = place.position;
        PlatformWorld world = player.platformPlayer.getWorld();
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return;

        WrappedBlockState state = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
        StateType against = state.getType();

        // Excuse a same-tick instant-break / netty resync that left the support
        // block momentarily air — that's a legit desync, not a ghost block.
        int tick = GrimAPI.INSTANCE.getTickManager().currentTick;
        for (BlockModification mod : player.blockHistory.getRecentModifications(m ->
                tick - m.tick() < 2 && pos.equals(m.location())
                        && (m.cause() == BlockModification.Cause.START_DIGGING
                        || m.cause() == BlockModification.Cause.HANDLE_NETTY_SYNC_TRANSACTION))) {
            StateType old = mod.oldBlockContents().getType();
            if (!old.isAir() && !Materials.isNoPlaceLiquid(old)) return;
        }

        if (against.isAir() || Materials.isNoPlaceLiquid(against)) {
            if (++consecutive >= minConsecutive) {
                flagAndAlert("against=air at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                consecutive = 0;
            }
        } else {
            consecutive = 0;
        }
    }
}
