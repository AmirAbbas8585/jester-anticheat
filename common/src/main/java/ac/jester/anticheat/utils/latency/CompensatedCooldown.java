package ac.jester.anticheat.utils.latency;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.type.PositionCheck;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.update.PositionUpdate;
import ac.jester.anticheat.utils.data.CooldownData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemUseCooldown;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.resources.ResourceLocation;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CompensatedCooldown extends Check implements PositionCheck {
    private final ConcurrentHashMap<ResourceLocation, CooldownData> itemCooldownMap = new ConcurrentHashMap<>();

    public CompensatedCooldown(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPositionUpdate(final PositionUpdate positionUpdate) {
        for (Iterator<Map.Entry<ResourceLocation, CooldownData>> it = itemCooldownMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ResourceLocation, CooldownData> entry = it.next();

            if (entry.getValue().getTransaction() < player.lastTransactionReceived.get()) {
                entry.getValue().tick();
            }

            if (entry.getValue().getTicksRemaining() <= 0) it.remove();
        }
    }

    public boolean hasItem(ItemStack item) {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_2)) {
            ItemUseCooldown cooldown = item.getComponentOr(ComponentTypes.USE_COOLDOWN, null);
            if (cooldown != null) {
                final Optional<ResourceLocation> cooldownGroup = cooldown.getCooldownGroup();
                if (cooldownGroup.isPresent()) {
                    return itemCooldownMap.containsKey(cooldownGroup.get());
                }
            }
        }

        return itemCooldownMap.containsKey(item.getType().getName());
    }

    public void addCooldown(ResourceLocation location, int cooldown, int transaction) {
        if (cooldown == 0) {
            removeCooldown(location);
            return;
        }

        itemCooldownMap.put(location, new CooldownData(cooldown, transaction));
    }

    public void removeCooldown(ResourceLocation location) {
        itemCooldownMap.remove(location);
    }
}
