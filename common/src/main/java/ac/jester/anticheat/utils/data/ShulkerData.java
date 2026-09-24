package ac.jester.anticheat.utils.data;

import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.packetentity.PacketEntity;
import ac.jester.anticheat.utils.data.packetentity.PacketEntityShulker;
import com.github.retrooper.packetevents.util.Vector3i;

import java.util.Objects;

public class ShulkerData {
    public final int lastTransactionSent;
    public final boolean isClosing;

    public PacketEntity entity = null;
    public Vector3i blockPos = null;

    private int ticksOfOpeningClosing = 0;

    public ShulkerData(Vector3i position, int lastTransactionSent, boolean isClosing) {
        this.lastTransactionSent = lastTransactionSent;
        this.isClosing = isClosing;
        this.blockPos = position;
    }

    public ShulkerData(PacketEntityShulker entity, int lastTransactionSent, boolean isClosing) {
        this.lastTransactionSent = lastTransactionSent;
        this.isClosing = isClosing;
        this.entity = entity;
    }

    public boolean tickIfGuaranteedFinished() {
        return isClosing && ++ticksOfOpeningClosing >= 25;
    }

    public SimpleCollisionBox getCollision() {
        if (blockPos != null) {
            return new SimpleCollisionBox(blockPos);
        }
        return entity.getPossibleCollisionBoxes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShulkerData that = (ShulkerData) o;
        return Objects.equals(entity, that.entity) && Objects.equals(blockPos, that.blockPos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity, blockPos);
    }
}
