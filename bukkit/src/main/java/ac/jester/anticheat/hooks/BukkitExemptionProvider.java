package ac.jester.anticheat.hooks;

import ac.jester.anticheat.hooks.impl.AuraSkillsHook;
import ac.jester.anticheat.hooks.impl.CrazyEnchantmentsHook;
import ac.jester.anticheat.hooks.impl.DeluxeCombatHook;
import ac.jester.anticheat.hooks.impl.GSitHook;
import ac.jester.anticheat.hooks.impl.ItemsAdderHook;
import ac.jester.anticheat.hooks.impl.WorldGuardHook;
import ac.jester.anticheat.player.GrimPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit implementation of ExemptionProvider.
 * Bridges hook singletons into the platform-agnostic interface.
 */
public final class BukkitExemptionProvider extends ExemptionProvider {

    private final GSitHook gsit;
    private final WorldGuardHook worldGuard;
    private final AuraSkillsHook auraSkills;
    private final ItemsAdderHook itemsAdder;
    private final DeluxeCombatHook deluxeCombat;
    private final CrazyEnchantmentsHook crazyEnchantments;

    public BukkitExemptionProvider(GSitHook gsit, WorldGuardHook worldGuard, AuraSkillsHook auraSkills, ItemsAdderHook itemsAdder, DeluxeCombatHook deluxeCombat, CrazyEnchantmentsHook crazyEnchantments) {
        this.gsit = gsit;
        this.worldGuard = worldGuard;
        this.auraSkills = auraSkills;
        this.itemsAdder = itemsAdder;
        this.deluxeCombat = deluxeCombat;
        this.crazyEnchantments = crazyEnchantments;
    }

    // GSit dismounting teleports the player — without a grace window the first
    // ticks after standing up false flag movement checks. 1.5s wasn't always
    // enough for the prediction engine's bounding box to fully resettle before
    // the player's first post-stand-up jump, which has now also been fixed at
    // the OffsetHandler level (see MovementA.advantageGained reset), but keep
    // this generously long since it's cheap and other checks rely on it too.
    private static final long STAND_UP_GRACE_MS = 3000L;
    private final Map<UUID, Long> lastSeated = new ConcurrentHashMap<>();

    @Override
    public boolean isSitting(GrimPlayer player) {
        if (!gsit.isAvailable() || player.uuid == null) return false;

        // Look up by NAME (not UUID): in offline/proxy setups grim's player.uuid
        // and the Bukkit UUID the hook keys by can differ. gsit.isSittingByName
        // is a lock-free set read — safe from the netty thread, unlike Bukkit
        // entity access.
        String name = player.user != null ? player.user.getName() : null;
        if (name != null && gsit.isSittingByName(name)) {
            lastSeated.put(player.uuid, System.currentTimeMillis());
            return true;
        }
        Long last = lastSeated.get(player.uuid);
        if (last != null) {
            if (System.currentTimeMillis() - last < STAND_UP_GRACE_MS) return true;
            lastSeated.remove(player.uuid);
        }
        return false;
    }

    @Override
    public boolean canFly(GrimPlayer player) {
        if (!worldGuard.isAvailable() || player.uuid == null) return false;
        Player bukkit = Bukkit.getPlayer(player.uuid);
        return bukkit != null && worldGuard.canFly(bukkit);
    }

    @Override
    public double getSpeedMultiplier(GrimPlayer player) {
        if (!auraSkills.isAvailable() || player.uuid == null) return 1.0;
        Player bukkit = Bukkit.getPlayer(player.uuid);
        return bukkit != null ? auraSkills.getSpeedMultiplier(bukkit) : 1.0;
    }

    @Override
    public double getJumpMultiplier(GrimPlayer player) {
        if (!auraSkills.isAvailable() || player.uuid == null) return 1.0;
        Player bukkit = Bukkit.getPlayer(player.uuid);
        return bukkit != null ? auraSkills.getJumpMultiplier(bukkit) : 1.0;
    }

    @Override
    public boolean isPvpDisabled(GrimPlayer player) {
        if (!worldGuard.isAvailable() || player.uuid == null) return false;
        Player bukkit = Bukkit.getPlayer(player.uuid);
        if (bukkit == null) return false;
        Location loc = bukkit.getLocation();
        return worldGuard.isPvpDisabled(loc);
    }

    @Override
    public boolean hasRecentCustomBlockBreak(GrimPlayer player) {
        if (!itemsAdder.isAvailable() || player.uuid == null) return false;
        return itemsAdder.hasRecentCustomBreak(player.uuid);
    }

    @Override
    public boolean hasRecentCombatKnockback(GrimPlayer player) {
        if (!deluxeCombat.isAvailable() || player.uuid == null) return false;
        return deluxeCombat.hasRecentCustomKB(player.uuid);
    }

    @Override
    public int getCustomMiningSpeedLevel(GrimPlayer player) {
        if (!crazyEnchantments.isAvailable() || player.uuid == null) return 0;
        return crazyEnchantments.getMiningLevel(player.uuid);
    }

    @Override
    public boolean hasAreaBreakEnchant(GrimPlayer player) {
        if (!crazyEnchantments.isAvailable() || player.uuid == null) return false;
        return crazyEnchantments.hasAreaBreak(player.uuid);
    }
}
