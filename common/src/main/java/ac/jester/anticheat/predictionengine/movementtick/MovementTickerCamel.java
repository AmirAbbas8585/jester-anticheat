package ac.jester.anticheat.predictionengine.movementtick;

import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityCamel;

public class MovementTickerCamel extends MovementTickerHorse {
    public MovementTickerCamel(GrimPlayer player) {
        super(player);
    }

    @Override
    public float getExtraSpeed() {
        PacketEntityCamel camel = (PacketEntityCamel) player.compensatedEntities.self.getRiding();

        final boolean wantsToJump = camel.getJumpPower() > 0.0F && !camel.isJumping() && player.lastOnGround;
        if (wantsToJump) return 0;

        return player.isSprinting && camel.getDashCooldown() <= 0 && !camel.isDashing() ? 0.1f : 0.0f;
    }
}
