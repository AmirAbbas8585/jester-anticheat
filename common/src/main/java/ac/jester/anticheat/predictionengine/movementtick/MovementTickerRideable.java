package ac.jester.anticheat.predictionengine.movementtick;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityRideable;
import ac.jester.anticheat.utils.nmsutil.Collisions;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public class MovementTickerRideable extends MovementTickerLivingVehicle {
    public MovementTickerRideable(GrimPlayer player) {
        super(player);

        float f = getSteeringSpeed();

        PacketEntityRideable boost = ((PacketEntityRideable) player.compensatedEntities.self.getRiding());

        if (boost.currentBoostTime++ < boost.boostTimeMax) {
            f += f * 1.15F * player.trigHandler.sin((float) boost.currentBoostTime / (float) boost.boostTimeMax * (float) Math.PI);
        }

        player.speed = f;
    }

    public float getSteeringSpeed() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void livingEntityTravel() {
        super.livingEntityTravel();
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17))
            Collisions.handleInsideBlocks(player);
    }
}
