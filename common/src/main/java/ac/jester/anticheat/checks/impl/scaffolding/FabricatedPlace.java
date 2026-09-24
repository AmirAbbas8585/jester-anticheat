package ac.jester.anticheat.checks.impl.scaffolding;

import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.BlockPlaceCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3f;

@CheckData(name = "FabricatedPlace", description = "Sent out of bounds cursor position")
public class FabricatedPlace extends BlockPlaceCheck {
    private static final double MAX_DOUBLE_ERROR = Math.ulp(30_000_000.0) * 2.0;

    private static final double FLOAT_STEP_AT_ONE = Math.ulp(1.0f);

    public FabricatedPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        Vector3f cursor = place.cursor;
        if (cursor == null) return;

        boolean isExtended = Materials.isShapeExceedsCube(place.getPlacedAgainstMaterial())
                || place.getPlacedAgainstMaterial() == StateTypes.LECTERN;

        double maxBound = isExtended ? 1.5 : 1.0;
        double minBound = 1.0 - maxBound;

        if (cursor.getX() < minBound - MAX_DOUBLE_ERROR ||
                cursor.getY() < minBound - MAX_DOUBLE_ERROR ||
                cursor.getZ() < minBound - MAX_DOUBLE_ERROR) {
            String debug = String.format("cursor=%s limit=%.16f", cursor, minBound - MAX_DOUBLE_ERROR);
            if (flagAndAlert(debug) && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
            return;
        }

        double upperTolerance = FLOAT_STEP_AT_ONE;

        if (cursor.getX() > maxBound + upperTolerance ||
                cursor.getY() > maxBound + upperTolerance ||
                cursor.getZ() > maxBound + upperTolerance) {
            String debug = String.format("cursor=%s limit=%.16f", cursor, maxBound + upperTolerance);
            if (flagAndAlert(debug) && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
        }
    }
}
