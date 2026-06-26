package ac.jester.anticheat.utils.anticheat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a check name + its verbose string into a short, plain-English sentence
 * explaining WHY this specific flag fired — shown in the alert hover's "Info:"
 * line. The static check description ("Breaking blocks too quickly") says nothing
 * about THIS flag; this reads the actual verbose data (offset, ping, cps,
 * distance, block type, ...) plus the live ping/TPS so staff can judge a likely
 * false positive vs a likely real cheat at a glance.
 *
 * Always returns a concrete, non-empty sentence — every check has at least a
 * one-line reason, and a trailing context note is added when the ping was high
 * or the server TPS was low (the two most common false-positive causes).
 */
public final class FlagExplainer {

    private FlagExplainer() {
    }

    private static final Pattern PING_MS = Pattern.compile("ping=(\\d+)ms");
    private static final Pattern SIM_VERBOSE = Pattern.compile(
            "sneak=(true|false)(?:\\s+carpet=(true|false))?(?:\\s+bed=(true|false))?\\s+water=(true|false)");
    private static final Pattern OFFSET = Pattern.compile("^([\\d.eE+-]+)");
    private static final Pattern FASTBREAK_DIFF = Pattern.compile("diff=([\\d.]+)ms.*type=(\\w+)");
    private static final Pattern FASTBREAK_DELAY = Pattern.compile("delay=([\\d.]+)ms.*type=(\\w+)");
    private static final Pattern DISTANCE_MAX = Pattern.compile("distance=([\\d.]+)\\s+max=([\\d.]+)");
    private static final Pattern NUKER_SIM = Pattern.compile("simultaneous_starts=(\\d+)");
    private static final Pattern RATE_PER_S = Pattern.compile("rate=([\\d.]+)/s");
    private static final Pattern AUTOCLICKER_CPS = Pattern.compile("cps=([\\d.]+)\\s+mean=([\\d.]+)ms");
    private static final Pattern AUTOCLICKER_CV = Pattern.compile("cv=([\\d.]+)\\s+cps=([\\d.]+)");
    private static final Pattern AUTOCLICKERB_ANIMS = Pattern.compile("anims=(\\d+) in one tick");
    private static final Pattern TRANSACTION_SKIPPED = Pattern.compile("skipped:\\s*(\\d+)");
    private static final Pattern GROUNDSPOOF = Pattern.compile("claimed (true|false)");

    public static String explain(String checkName, String verbose, double offsetOrVl, int ping, double tps) {
        String v = verbose == null ? "" : verbose;
        StringBuilder sb = new StringBuilder();

        boolean tpsLow = tps > 0 && tps < 19.5;
        boolean pingHigh = ping >= 250;

        switch (checkName) {
            case "MovementA" -> explainMovementA(v, sb);
            case "TimerA", "PacketLimit", "Timer" -> explainTimer(v, sb);
            case "FastBreak" -> explainFastBreak(v, sb);
            case "GroundSpoof" -> explainGroundSpoof(v, sb);
            case "Nuker", "NukerA", "NukerB" -> explainNuker(v, sb);
            case "FarBreak", "Reach" -> explainDistance(checkName, v, sb);
            case "AutoClicker", "AutoClickerA" -> explainAutoClickerA(v, sb);
            case "AutoClickerB" -> explainAutoClickerB(v, sb);
            case "TransactionOrder" -> explainTransactionOrder(v, sb);
            default -> sb.append(describe(checkName));
        }

        // Trailing context: the two things most likely to turn a real-looking
        // flag into a false positive. Only appended when actually relevant.
        if (tpsLow) {
            sb.append(" The server TPS was low (")
              .append(String.format("%.1f", tps))
              .append(") when this fired — timing/movement checks misfire under lag.");
        } else if (pingHigh && wantsPingNote(checkName)) {
            sb.append(" Ping was high (").append(ping)
              .append("ms), which can produce this on its own without cheating.");
        }

        return sb.toString();
    }

    /** True for checks whose accuracy is meaningfully affected by latency. */
    private static boolean wantsPingNote(String check) {
        return switch (check) {
            case "Reach", "FarBreak", "TimerA", "PacketLimit", "Timer", "AutoClickerB",
                 "KillAuraD", "TriggerBot", "AutoBlock", "Criticals", "Knockback",
                 "AntiKB", "MovementA" -> true;
            default -> false;
        };
    }

    private static void explainMovementA(String v, StringBuilder sb) {
        sb.append("The server replayed this player's movement and it didn't match what they sent");
        String off = firstToken(v);
        if (off != null) sb.append(" (offset ").append(off).append(")");
        sb.append(". ");

        Matcher m = SIM_VERBOSE.matcher(v);
        if (m.find()) {
            boolean sneak = "true".equals(m.group(1));
            boolean carpet = "true".equals(m.group(2));
            boolean bed = "true".equals(m.group(3));
            boolean water = "true".equals(m.group(4));
            if (sneak) {
                sb.append("They were sneaking — crouch movement near block edges is naturally noisy, likely a false positive.");
            } else if (carpet) {
                sb.append("They were on a carpet — its thin collision shape is a known mis-simulation, not a speed exploit.");
            } else if (bed) {
                sb.append("They were on/next to a bed — its odd collision shape causes this when jumping on it, not cheating.");
            } else if (water) {
                sb.append("They were in water — buoyancy/swim physics is the most false-positive-prone movement to simulate.");
            } else {
                sb.append("None of sneak/carpet/bed/water applied — a 'clean' offset, so real speed/fly is more plausible here (check whether it repeats).");
            }
        } else {
            sb.append("If it's an isolated one-off it's most likely physics noise; a real fly/speed hack sustains the offset every tick.");
        }
    }

    private static void explainTimer(String v, StringBuilder sb) {
        sb.append("The player's game clock ran ahead of real time — they sent movement packets faster than vanilla allows");
        Integer ping = matchInt(PING_MS, v);
        if (ping != null) {
            sb.append(" (ping ").append(ping).append("ms). ");
            if (ping > 300) {
                sb.append("That ping is high, so this is more likely a connection spike than a timer hack.");
            } else {
                sb.append("If the gap from the previous flag was large, it can be normal packet bunching rather than a hack.");
            }
        } else {
            sb.append(". A real timer hack does this continuously; a single burst is usually a lag/packet-bunching artefact.");
        }
    }

    private static void explainFastBreak(String v, StringBuilder sb) {
        Matcher diff = FASTBREAK_DIFF.matcher(v);
        Matcher delay = FASTBREAK_DELAY.matcher(v);
        if (diff.find()) {
            sb.append("Block '").append(diff.group(2)).append("' broke ").append(diff.group(1))
              .append("ms faster than the server predicted. A tiny gap (~50ms = one tick) is usually rounding; hundreds of ms points to a real FastBreak.");
        } else if (delay.find()) {
            sb.append("Two breaks of '").append(delay.group(2)).append("' came only ").append(delay.group(1))
              .append("ms apart. If the block is instant-break (grass/flower) or a plugin denied it, this is normal.");
        } else {
            sb.append("A block broke faster than vanilla mining speed allows for that tool/block.");
        }
    }

    private static void explainGroundSpoof(String v, StringBuilder sb) {
        Matcher m = GROUNDSPOOF.matcher(v);
        sb.append("The client claimed an on-ground state the server disagreed with");
        if (m.find()) sb.append(" (client said onGround=").append(m.group(1)).append(")");
        sb.append(". Jumping on beds/carpets, GSit, or a teleport cause this far more often than a real ground-spoof cheat.");
    }

    private static void explainNuker(String v, StringBuilder sb) {
        Integer sim = matchInt(NUKER_SIM, v);
        Matcher rate = RATE_PER_S.matcher(v);
        if (sim != null) {
            sb.append(sim).append(" separate blocks started breaking in a single tick. If they were all the SAME block (e.g. a plugin denied the break) this is a known artefact; different blocks means a real Nuker is likely.");
        } else if (rate.find()) {
            sb.append("Blocks broke at ").append(rate.group(1)).append("/s, above the natural rate.");
        } else {
            sb.append("The block-breaking pattern looked automated (many blocks at once or far too fast).");
        }
    }

    private static void explainDistance(String check, String v, StringBuilder sb) {
        Matcher m = DISTANCE_MAX.matcher(v);
        boolean reach = check.equals("Reach");
        if (m.find()) {
            double dist = Double.parseDouble(m.group(1));
            double max = Double.parseDouble(m.group(2));
            double over = dist - max;
            sb.append(reach ? "Hit an entity from " : "Broke a block from ")
              .append(String.format("%.2f", dist)).append(" blocks away (limit ")
              .append(String.format("%.2f", max)).append("). ");
            if (over < 0.5) {
                sb.append("Only ").append(String.format("%.2f", over))
                  .append(" over the limit — within latency margin, likely legitimate.");
            } else {
                sb.append(String.format("%.2f", over)).append(" past the limit — a real reach hack is plausible.");
            }
        } else {
            sb.append(reach ? "Hit an entity from farther than the allowed reach distance."
                            : "Broke a block from farther than the allowed distance.");
        }
    }

    private static void explainAutoClickerA(String v, StringBuilder sb) {
        Matcher cps = AUTOCLICKER_CPS.matcher(v);
        Matcher cv = AUTOCLICKER_CV.matcher(v);
        if (cps.find()) {
            double c = Double.parseDouble(cps.group(1));
            sb.append("Average click speed reached ").append(c).append(" CPS over a 20-click window. ");
            if (c < 22) {
                sb.append("That's right at the vanilla ceiling (~20/s) — a very fast jitter-clicker can reach it, so treat with care.");
            } else {
                sb.append("That's well past what a human can sustain — an auto-clicker/macro is likely.");
            }
        } else if (cv.find()) {
            sb.append("Click intervals were unnaturally consistent (variation ").append(cv.group(1))
              .append("). Humans always have some jitter even when clicking fast; this regularity looks like a macro.");
        } else {
            sb.append("The clicking pattern was statistically too consistent to be human.");
        }
    }

    private static void explainAutoClickerB(String v, StringBuilder sb) {
        Integer anims = matchInt(AUTOCLICKERB_ANIMS, v);
        if (anims != null) {
            sb.append(anims).append(" attack-swing packets arrived inside a SINGLE client tick, which vanilla can't do. A jittery/mobile connection can deliver packets in bursts and mimic this, so repetition matters.");
        } else {
            sb.append("Multiple attack swings were packed into one tick — typical of interact-aura or a click macro.");
        }
    }

    private static void explainTransactionOrder(String v, StringBuilder sb) {
        Integer skipped = matchInt(TRANSACTION_SKIPPED, v);
        sb.append("The client's transaction replies skipped ");
        sb.append(skipped != null ? skipped.toString() : "one or more");
        sb.append(" pending IDs — this normally just means packet loss on the network, so this check only alerts and never punishes.");
    }

    /**
     * One-line "why it fired" for every check that doesn't carry rich verbose
     * data. Keeps the Info line meaningful instead of a generic placeholder.
     */
    private static String describe(String check) {
        return switch (check) {
            case "Knockback", "AntiKB" -> "The player took less knockback than the velocity the server sent them — could also be a custom-knockback combat plugin, not a cheat.";
            case "Phase" -> "The player moved through a solid block they should have collided with.";
            case "NoSlow" -> "The player kept full speed while using an item (eating, drinking, blocking, drawing a bow) instead of slowing down.";
            case "InventoryWalk" -> "The player kept walking while an inventory/container screen was open.";
            case "SelfInteract" -> "The player sent an attack on their OWN entity id, which is impossible in vanilla.";
            case "AutoBlock" -> "The player attacked while their shield was actively blocking.";
            case "Criticals" -> "The player faked a critical hit by spoofing the on-ground flag during the attack.";
            case "NoHitDelay" -> "Attacks arrived faster than one per tick, beyond normal click speed.";
            case "KillAuraA" -> "The player damaged an entity without sending the arm-swing that should accompany it (no-swing aura).";
            case "KillAuraB" -> "The player attacked several different entities within a single tick.";
            case "KillAuraC" -> "The player attacked while a container GUI was open — impossible in vanilla.";
            case "KillAuraD" -> "The player attacked an entity while looking well away from it (beyond a plausible angle).";
            case "Multitask" -> "The player attacked while actively using an item (eating/bow/potion).";
            case "FastUse" -> "An item was consumed faster than its vanilla use time.";
            case "BedAura", "AnchorAura" -> "The player interacted with explosive blocks (bed/anchor) faster than humanly possible.";
            case "AutoPot" -> "A healing/buff potion was thrown within an inhuman reaction time after taking damage.";
            case "AutoEat" -> "The player started eating within an inhuman reaction time after their hunger dropped, repeatedly.";
            case "AutoWeapon" -> "The player switched to the best weapon and attacked in the same tick, every time.";
            case "CrystalAura" -> "End-crystal place/attack interactions happened faster than a human can perform them.";
            case "VeinMiner" -> "Completed block breaks per second exceeded the natural mining rate.";
            case "FastBow" -> "A bow was released before its minimum draw time.";
            case "AirLiquidPlace" -> "A block was placed against air/liquid with no valid support — or a same-tick break+place desync.";
            case "AirLiquidBreak" -> "A block was broken where the server believed there was air/liquid — often a harmless break/desync.";
            case "FastPlace" -> "Blocks were placed faster than the vanilla place cooldown allows.";
            case "ScaffoldGoDown" -> "Blocks were placed underfoot in a descending-staircase pattern, automated-bridging style.";
            case "Tower" -> "Blocks were placed under the player while jumping upward faster than possible by hand.";
            case "RotationPlace" -> "A block was placed while the player wasn't looking at it (scaffold) — most false-prone on 1.8/Via clients.";
            case "NoJumpDelay" -> "The player re-jumped instantly on landing every time, with no jump cooldown.";
            case "AutoParkour" -> "Nearly every jump was a pixel-perfect edge jump, more consistent than a human.";
            case "BoatFly" -> "A vehicle kept rising in mid-air with no water/support.";
            case "BoatClip" -> "A vehicle moved a teleport-sized distance in one packet.";
            case "FireworkBoost" -> "Firework boosts while gliding were machine-uniform in timing.";
            case "ChestStealer" -> "Items were pulled from a container faster than humanly possible.";
            case "AutoFish" -> "The rod was reeled in within an inhuman reaction time after a bite.";
            case "AutoArmor" -> "Multiple armor pieces were equipped within a few milliseconds.";
            case "AutoRespawn" -> "The respawn packet was sent immediately after death, faster than a human.";
            case "AutoTotem" -> "A totem was moved back to the offhand faster than humanly possible after it popped.";
            case "AutoTool" -> "The player switched to the optimal tool the same tick they started mining, every time.";
            case "PacketMine" -> "A block's start- and finish-dig packets arrived in the same tick (instant break).";
            case "AimA" -> "Combat rotations weren't quantized to any mouse sensitivity step — the signature of computed (aimbot) aim.";
            case "TriggerBot" -> "The player attacked the exact tick their crosshair first landed on a target.";
            case "MeteorDetector" -> "The client's fingerprint matched a known hacked client (e.g. Meteor).";
            case "NoFall" -> "The player claimed to be on ground when they weren't, to avoid fall damage.";
            case "SprintE" -> "The player was sprinting while wall/corner-colliding — if they're wedged in a block corner this is normal and gains no speed.";
            case "BadPacketsZ" -> "More than one movement-input packet was sent in a tick — normal inside a vehicle (e.g. a boat).";
            default -> {
                if (check != null && check.startsWith("BadPackets")) {
                    yield "The client sent a malformed or impossible packet that a vanilla client never sends.";
                }
                yield "The '" + check + "' check detected behaviour outside what a vanilla client can produce.";
            }
        };
    }

    // ── small parse helpers ──────────────────────────────────────────────────
    private static Integer matchInt(Pattern p, String v) {
        Matcher m = p.matcher(v);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static String firstToken(String v) {
        Matcher m = OFFSET.matcher(v.trim());
        return m.find() ? m.group(1) : null;
    }
}
