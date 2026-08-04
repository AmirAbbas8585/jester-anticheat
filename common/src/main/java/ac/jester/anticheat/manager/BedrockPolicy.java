package ac.jester.anticheat.manager;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides which checks may run against a Bedrock (Geyser/Floodgate) player.
 *
 * Bedrock players were previously checked with exactly the same Java-Edition
 * physics assumptions as everyone else, which means they were not "exempt" —
 * they were being false-flagged and rubber-banded. The opposite extreme,
 * exempting them wholesale, is worse: a cheating Bedrock player would then be
 * untouchable. So checks are sorted by WHY they would misfire.
 *
 * The split is driven by what Geyser actually has to translate:
 *   - Prediction and physics: Bedrock's movement model is genuinely different,
 *     and Geyser re-emits movement on its own cadence rather than the Bedrock
 *     client's tick. A tolerance cannot paper over a different physics engine,
 *     so these are skipped rather than loosened.
 *   - Knockback and explosions: confirmed Geyser bugs make Bedrock players take
 *     visibly less knockback than the server predicts. AntiKB flags precisely on
 *     "took less knockback than predicted", so it would fire continuously.
 *   - Rotation-derived checks: for touch controls Geyser SYNTHESISES a look
 *     angle for interactions rather than sending the player's real view, so
 *     aim/reach/rotation analysis is measuring Geyser, not the player.
 *   - Click-timing checks: touch and controller input do not produce a human
 *     mouse distribution, and Geyser's touch handling does not always propagate
 *     the left click, so swing/attack pairing breaks.
 *
 * What deliberately still runs: packet-integrity and protocol-validity checks
 * (Crash*, Exploit*, malformed slot ids, out-of-range block faces, interacting
 * with yourself, spectating while not a spectator). Geyser never legitimately
 * emits those, so a Bedrock player who trips them is doing something a normal
 * Bedrock client cannot do. That is where real Bedrock cheating shows up.
 *
 * Everything here is configurable — a server that measures different behaviour
 * can move a check between lists without a code change.
 */
public final class BedrockPolicy {

    private BedrockPolicy() {
    }

    /**
     * Checks skipped for Bedrock players by default.
     *
     * These are the ones whose logic assumes Java movement, Java rotation input,
     * or Java click timing. Names match the check's configName.
     */
    private static final List<String> DEFAULT_DISABLED = List.of(
            // Prediction / movement — a different physics engine, not a tolerance gap
            "MovementA", "Phase", "GroundSpoof", "NoFall", "NoSlow",
            "Timer", "TimerA", "TickTimer", "NegativeTimer", "VehicleTimer",
            "AutoParkour", "NoJumpDelay",
            "SprintA", "SprintB", "SprintC", "SprintD", "SprintE", "SprintF", "SprintG",
            "ElytraA", "ElytraB", "ElytraC", "ElytraD", "ElytraE", "ElytraF", "ElytraG",
            "ElytraH", "ElytraI", "FireworkBoost",
            "BoatFly", "BoatClip", "EntityFly", "EntitySpeed",
            "VehicleA", "VehicleB", "VehicleC", "VehicleD", "VehicleE", "VehicleF",
            // Velocity — confirmed Geyser knockback/explosion differences
            "Knockback", "AntiKB", "Explosion", "AntiExplosion",
            // Rotation-derived — Geyser synthesises interaction rotation on touch
            "Reach", "ReachB", "Hitboxes",
            "KillAuraA", "KillAuraB", "KillAuraC", "KillAuraD", "TriggerBot",
            "AimA", "AimModulo360", "AimDuplicateLook",
            "RotationPlace", "RotationBreak", "DuplicateRotPlace",
            "PositionPlace", "PositionBreakA", "PositionBreakB",
            // Click/swing timing — touch and controller input, and Geyser's touch
            // handling not always propagating the left click
            "AutoClickerA", "AutoClickerB", "AutoClickerC", "AutoClickerD",
            "NoHitDelay", "Multitask", "Criticals", "AttributeSwap",
            "MultiActionsA", "MultiActionsB", "MultiActionsC", "MultiActionsD",
            "MultiActionsE", "MultiActionsF", "MultiActionsG",
            "MultiInteractA", "MultiInteractB",
            "InventoryWalk", "NoSwingBreak",
            // Packet ordering / rotation-bearing bad-packet checks that Geyser
            // legitimately trips while translating
            "Post", "TransactionOrder",
            "BadPacketsB", "BadPacketsD", "BadPacketsF", "BadPacketsG",
            "BadPacketsJ", "BadPacketsV", "BadPacketsX", "BadPacketsZ",
            // Scaffolding cadence
            "Tower", "ScaffoldGoDown", "Baritone");

    private static volatile boolean enabled = true;
    private static volatile Set<String> disabled = lower(DEFAULT_DISABLED);

    public static void reload(ConfigManager config) {
        enabled = config.getBooleanElse("bedrock.enabled", true);
        List<String> configured = config.getStringListElse("bedrock.disabled-checks", List.of());
        disabled = configured.isEmpty() ? lower(DEFAULT_DISABLED) : lower(configured);
    }

    /**
     * True if this check must not flag the given player.
     *
     * @param configName the check's config name, may be null for internal processors
     */
    public static boolean shouldSkip(ac.jester.anticheat.player.GrimPlayer player, String configName) {
        if (!enabled || configName == null) return false;
        if (!player.isBedrock()) return false;
        return disabled.contains(configName.toLowerCase(Locale.ROOT));
    }

    /** True if Bedrock handling is on at all (used to suppress setbacks). */
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
