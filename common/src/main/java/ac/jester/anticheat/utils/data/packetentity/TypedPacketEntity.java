package ac.jester.anticheat.utils.data.packetentity;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;

public abstract class TypedPacketEntity {
    public final EntityType type;
    public final boolean isLivingEntity, isMinecart, isHorse, isAgeable, isAnimal, isBoat, isHappyGhast;

    public TypedPacketEntity(EntityType type) {
        this.type = type;
        this.isLivingEntity = EntityTypes.isTypeInstanceOf(type, EntityTypes.LIVINGENTITY);
        this.isMinecart = EntityTypes.isTypeInstanceOf(type, EntityTypes.MINECART_ABSTRACT);
        this.isHorse = EntityTypes.isTypeInstanceOf(type, EntityTypes.ABSTRACT_HORSE);
        this.isAgeable =
                (EntityTypes.isTypeInstanceOf(type, EntityTypes.ABSTRACT_AGEABLE) && !(EntityTypes.isTypeInstanceOf(type, EntityTypes.ABSTRACT_PARROT) || type == EntityTypes.FROG))
                        || EntityTypes.isTypeInstanceOf(type, EntityTypes.ZOMBIE)
                        || EntityTypes.isTypeInstanceOf(type, EntityTypes.ABSTRACT_PIGLIN)
                        || type == EntityTypes.ZOGLIN;
        this.isAnimal = EntityTypes.isTypeInstanceOf(type, EntityTypes.ABSTRACT_ANIMAL);
        this.isBoat = EntityTypes.isTypeInstanceOf(type, EntityTypes.BOAT);
        this.isHappyGhast = EntityTypes.HAPPY_GHAST.equals(type);
    }

    public boolean isPushable() {
        if (type == EntityTypes.ARMOR_STAND || type == EntityTypes.BAT || type == EntityTypes.PARROT)
            return false;
        return isLivingEntity || isBoat || isMinecart;
    }
}
