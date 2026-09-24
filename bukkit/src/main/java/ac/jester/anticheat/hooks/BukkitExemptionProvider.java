package ac.jester.anticheat.hooks;

import ac.jester.anticheat.hooks.impl.AuraSkillsHook;
import ac.jester.anticheat.hooks.impl.DeluxeCombatHook;
import ac.jester.anticheat.hooks.impl.GSitHook;
import ac.jester.anticheat.hooks.impl.ItemsAdderHook;
import ac.jester.anticheat.hooks.impl.WorldGuardHook;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BukkitExemptionProvider extends ExemptionProvider {
    private final GSitHook gsit;
    private final WorldGuardHook worldGuard;
    private final AuraSkillsHook auraSkills;
    private final ItemsAdderHook itemsAdder;
    private final DeluxeCombatHook deluxeCombat;

    public BukkitExemptionProvider(GSitHook gsit, WorldGuardHook worldGuard, AuraSkillsHook auraSkills, ItemsAdderHook itemsAdder, DeluxeCombatHook deluxeCombat) {
        this.gsit = gsit;
        this.worldGuard = worldGuard;
        this.auraSkills = auraSkills;
        this.itemsAdder = itemsAdder;
        this.deluxeCombat = deluxeCombat;
    }

    private static boolean integ(String key) {
        return GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("integrations." + key, true);
    }

    private static final long STAND_UP_GRACE_MS = 3000L;
    private final Map<UUID, Long> lastSeated = new ConcurrentHashMap<>();

    @Override
    public boolean isSitting(GrimPlayer player) {
        if (!integ("gsit-exempt-movement") || !gsit.isAvailable() || player.uuid == null) return false;

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
        if (!integ("worldguard-fly-regions") || !worldGuard.isAvailable() || player.uuid == null) return false;
        Player bukkit = Bukkit.getPlayer(player.uuid);
        return bukkit != null && worldGuard.canFly(bukkit);
    }

    @Override
    public double getSpeedMultiplier(GrimPlayer player) {
        if (!integ("auraskills-movement-boost") || !auraSkills.isAvailable() || player.uuid == null) return 1.0;
        Player bukkit = Bukkit.getPlayer(player.uuid);
        return bukkit != null ? auraSkills.getSpeedMultiplier(bukkit) : 1.0;
    }

    @Override
    public double getJumpMultiplier(GrimPlayer player) {
        if (!integ("auraskills-movement-boost") || !auraSkills.isAvailable() || player.uuid == null) return 1.0;
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
        if (!integ("itemsadder-custom-blocks") || !itemsAdder.isAvailable() || player.uuid == null) return false;
        return itemsAdder.hasRecentCustomBreak(player.uuid);
    }

    @Override
    public boolean hasRecentCombatKnockback(GrimPlayer player) {
        if (!integ("deluxecombat-kb-tolerance") || !deluxeCombat.isAvailable() || player.uuid == null) return false;
        return deluxeCombat.hasRecentCustomKB(player.uuid);
    }
}
