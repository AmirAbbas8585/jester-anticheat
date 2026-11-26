package ac.jester.anticheat.manager;

import ac.grim.grimac.api.config.ConfigManager;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class BedrockPolicy {
    private BedrockPolicy() {
    }

    private static final List<String> DEFAULT_DISABLED = List.of(
            "MovementA", "Fly", "Phase", "GroundSpoof", "NoFall", "NoSlow",
            "Timer", "TimerA", "TickTimer", "NegativeTimer", "VehicleTimer", "PacketLimit",
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
            "NoHitDelay", "Multitask", "Criticals", "AttributeSwap", "AutoBlock", "TeleportHit",
            "MultiActionsA", "MultiActionsB", "MultiActionsC", "MultiActionsD",
            "MultiActionsE", "MultiActionsF", "MultiActionsG",
            "MultiInteractA", "MultiInteractB",
            "InventoryWalk", "NoSwingBreak", "ChatC",
            "FarBreak", "FastBreak", "MultiBreak", "WrongBreak", "AirLiquidBreak", "PacketMine",
            "AirLiquidPlace", "FabricatedPlace", "FarPlace", "FastPlace", "MultiPlace", "GhostBlock", "InvalidPlaceA",
            "Post", "TransactionOrder",
            "BadPacketsB", "BadPacketsD", "BadPacketsF", "BadPacketsG",
            "BadPacketsH", "BadPacketsJ", "BadPacketsN", "BadPacketsP",
            "BadPacketsR", "BadPacketsT", "BadPacketsU",
            "BadPacketsV", "BadPacketsX", "BadPacketsZ",
            "Tower", "ScaffoldGoDown", "Baritone",
            "PacketOrderA", "PacketOrderB", "PacketOrderC", "PacketOrderD", "PacketOrderE", "PacketOrderF", "PacketOrderG", "PacketOrderH", "PacketOrderI", "PacketOrderJ", "PacketOrderK", "PacketOrderL", "PacketOrderM", "PacketOrderN", "PacketOrderO", "PacketOrderP");

    private static volatile boolean enabled = true;
    private static volatile boolean disableAll = false;
    private static volatile Set<String> disabled = lower(DEFAULT_DISABLED);

    private static volatile boolean apisChecked = false;
    private static Method floodgateGetInstance;
    private static Method floodgateIsPlayer;
    private static Method geyserApi;
    private static Method geyserIsBedrock;

    public static void reload(ConfigManager config) {
        enabled = config.getBooleanElse("bedrock.enabled", true);
        List<String> configured = config.getStringListElse("bedrock.disabled-checks", List.of());
        Set<String> resolved = configured.isEmpty() ? lower(DEFAULT_DISABLED) : lower(configured);
        disableAll = resolved.contains("*") || resolved.contains("all");
        disabled = resolved;
    }

    public static boolean detectBedrock(UUID uuid) {
        if (uuid == null) return false;
        if (uuid.getMostSignificantBits() == 0L) return true;

        ensureApisChecked();
        if (floodgateGetInstance != null && floodgateIsPlayer != null) {
            try {
                Object api = floodgateGetInstance.invoke(null);
                if (api != null && Boolean.TRUE.equals(floodgateIsPlayer.invoke(api, uuid))) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (geyserApi != null && geyserIsBedrock != null) {
            try {
                Object api = geyserApi.invoke(null);
                if (api != null && Boolean.TRUE.equals(geyserIsBedrock.invoke(api, uuid))) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void ensureApisChecked() {
        if (apisChecked) return;
        synchronized (BedrockPolicy.class) {
            if (apisChecked) return;
            try {
                Class<?> fg = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateGetInstance = fg.getMethod("getInstance");
                floodgateIsPlayer = fg.getMethod("isFloodgatePlayer", UUID.class);
            } catch (Throwable ignored) {
                floodgateGetInstance = null;
                floodgateIsPlayer = null;
            }
            try {
                Class<?> gy = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = gy.getMethod("api");
                geyserIsBedrock = gy.getMethod("isBedrockPlayer", UUID.class);
            } catch (Throwable ignored) {
                geyserApi = null;
                geyserIsBedrock = null;
            }
            apisChecked = true;
        }
    }

    public static boolean shouldSkip(ac.jester.anticheat.player.GrimPlayer player, String configName) {
        if (!enabled || configName == null) return false;
        if (!player.isBedrock()) return false;
        if (disableAll) return true;
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
