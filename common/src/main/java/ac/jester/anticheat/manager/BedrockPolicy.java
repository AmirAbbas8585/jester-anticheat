package ac.jester.anticheat.manager;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BedrockPolicy {
    private BedrockPolicy() {
    }

    private static final List<String> DEFAULT_DISABLED = List.of(
            "MovementA", "Phase", "GroundSpoof", "NoFall", "NoSlow",
            "Timer", "TimerA", "TickTimer", "NegativeTimer", "VehicleTimer",
            "AutoParkour", "NoJumpDelay",
            "SprintA", "SprintB", "SprintC", "SprintD", "SprintE", "SprintF", "SprintG",
            "ElytraA", "ElytraB", "ElytraC", "ElytraD", "ElytraE", "ElytraF", "ElytraG",
            "ElytraH", "ElytraI", "FireworkBoost",
            "BoatFly", "BoatClip", "EntityFly", "EntitySpeed",
            "VehicleA", "VehicleB", "VehicleC", "VehicleD", "VehicleE", "VehicleF",
            "Knockback", "AntiKB", "Explosion", "AntiExplosion",
            "Reach", "ReachB", "Hitboxes",
            "KillAuraA", "KillAuraB", "KillAuraC", "KillAuraD", "TriggerBot",
            "AimA", "AimModulo360", "AimDuplicateLook",
            "RotationPlace", "RotationBreak", "DuplicateRotPlace",
            "PositionPlace", "PositionBreakA", "PositionBreakB",
            "AutoClickerA", "AutoClickerB", "AutoClickerC", "AutoClickerD",
            "NoHitDelay", "Multitask", "Criticals", "AttributeSwap",
            "MultiActionsA", "MultiActionsB", "MultiActionsC", "MultiActionsD",
            "MultiActionsE", "MultiActionsF", "MultiActionsG",
            "MultiInteractA", "MultiInteractB",
            "InventoryWalk", "NoSwingBreak",
            "Post", "TransactionOrder",
            "BadPacketsB", "BadPacketsD", "BadPacketsF", "BadPacketsG",
            "BadPacketsJ", "BadPacketsV", "BadPacketsX", "BadPacketsZ",
            "Tower", "ScaffoldGoDown", "Baritone",
            "PacketOrderA", "PacketOrderB", "PacketOrderC", "PacketOrderD", "PacketOrderE", "PacketOrderF", "PacketOrderG", "PacketOrderH", "PacketOrderI", "PacketOrderJ", "PacketOrderK", "PacketOrderL", "PacketOrderM", "PacketOrderN", "PacketOrderO", "PacketOrderP");

    private static volatile boolean enabled = true;
    private static volatile Set<String> disabled = lower(DEFAULT_DISABLED);

    public static void reload(ConfigManager config) {
        enabled = config.getBooleanElse("bedrock.enabled", true);
        List<String> configured = config.getStringListElse("bedrock.disabled-checks", List.of());
        disabled = configured.isEmpty() ? lower(DEFAULT_DISABLED) : lower(configured);
    }

    public static boolean shouldSkip(ac.jester.anticheat.player.GrimPlayer player, String configName) {
        if (!enabled || configName == null) return false;
        if (!player.isBedrock()) return false;
        return disabled.contains(configName.toLowerCase(Locale.ROOT));
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static Set<String> lower(List<String> names) {
        Set<String> out = new HashSet<>(names.size());
        for (String n : names) {
            if (n != null && !n.isBlank()) out.add(n.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
