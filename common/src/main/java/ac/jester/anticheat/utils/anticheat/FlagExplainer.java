package ac.jester.anticheat.utils.anticheat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a check name + its verbose string into a short, Finglish (Persian
 * written with Latin letters) explanation of WHY this specific flag fired —
 * shown in the alert hover's "Info:" line. The static check description
 * ("Breaking blocks too quickly") doesn't say anything about THIS flag; this
 * parses the actual verbose data (ping, offset, block type, sneak/water/...)
 * so staff can tell a likely-false-positive from a likely-real one at a
 * glance, without having to paste the row into chat and ask.
 *
 * Best-effort: unrecognized verbose formats fall back to the check's own
 * description, translated to a generic Finglish hint.
 */
public final class FlagExplainer {

    private FlagExplainer() {
    }

    private static final Pattern PING_MS = Pattern.compile("ping=(\\d+)ms");
    private static final Pattern SIM_VERBOSE = Pattern.compile(
            "sneak=(true|false)(?:\\s+carpet=(true|false))?(?:\\s+bed=(true|false))?\\s+water=(true|false)");
    private static final Pattern FASTBREAK_DIFF = Pattern.compile("diff=([\\d.]+)ms.*type=(\\w+)");
    private static final Pattern FASTBREAK_DELAY = Pattern.compile("delay=([\\d.]+)ms.*type=(\\w+)");
    private static final Pattern FARBREAK = Pattern.compile("distance=([\\d.]+)\\s+max=([\\d.]+)");
    private static final Pattern NUKER_SIM = Pattern.compile("simultaneous_starts=(\\d+)");
    private static final Pattern NUKER_RATE = Pattern.compile("rate=([\\d.]+)/s");
    private static final Pattern AUTOCLICKER_CPS = Pattern.compile("cps=([\\d.]+)\\s+mean=([\\d.]+)ms");
    private static final Pattern AUTOCLICKER_CV = Pattern.compile("cv=([\\d.]+)\\s+cps=([\\d.]+)");
    private static final Pattern AUTOCLICKERB_ANIMS = Pattern.compile("anims=(\\d+) in one tick");
    private static final Pattern TRANSACTION_SKIPPED = Pattern.compile("skipped:\\s*(\\d+)");
    private static final Pattern GROUNDSPOOF = Pattern.compile("claimed (true|false)");

    public static String explain(String checkName, String verbose, double offsetOrVl, int ping, double tps) {
        String v = verbose == null ? "" : verbose;
        StringBuilder sb = new StringBuilder();

        // TPS context applies to almost every timing-sensitive check, so surface
        // it up front whenever the server wasn't at a clean ~20 TPS.
        boolean tpsLow = tps < 19.5 && tps > 0;

        switch (checkName) {
            case "Simulation" -> explainSimulation(v, tpsLow, sb);
            case "TimerA", "TimerLimit", "Timer" -> explainTimer(v, tpsLow, sb);
            case "FastBreak" -> explainFastBreak(v, tpsLow, sb);
            case "GroundSpoof" -> explainGroundSpoof(v, tpsLow, sb);
            case "Nuker", "NukerA", "NukerB" -> explainNuker(v, sb);
            case "FarBreak" -> explainFarBreak(v, ping, sb);
            case "AutoClicker", "AutoClickerA" -> explainAutoClickerA(v, sb);
            case "AutoClickerB" -> explainAutoClickerB(v, ping, sb);
            case "AirLiquidBreak", "AirLiquidPlace" -> sb.append("Talash baraye shekastan/gozashtan block ru ye"
                    + " jaiy ke server fekr mikone hava/ab/mayee ast (mamkene faghat ye desync mojaz bashe,"
                    + " masalan baad az shekastane sari'e ye block na kam-cheat).");
            case "TransactionOrder" -> explainTransactionOrder(v, ping, sb);
            case "SprintE" -> sb.append("Hamzaman sprint zade bood VA ba divar/gushe barkhord dashte —"
                    + " agar ja gir karde bashe (masalan gushe block) in tabiei hast, sor'at nemigire.");
            case "BadPacketsZ" -> sb.append("Bishtar az ye packet vorudi harekat (PLAYER_INPUT) too ye tick"
                    + " ferestade shode — agar dakhele vehicle (mesle ghayegh) bude, in tabiei hast.");
            case "AntiKB", "Knockback" -> sb.append("Offset knockback (ferestadeshode az server) ba chizi ke"
                    + " server entezar dasht farogh dashte — mamkene az ye plugin combat sefareshi"
                    + " (DeluxeCombat) bashe na cheat.");
            case "NoSlow" -> sb.append("Sor'ate harekat moqe' estefade az item (khordan/shield/kamaan) kamtar"
                    + " az hade entezar kam nashode bood.");
            case "NoFall" -> sb.append("Player edea kard ru zamin hast dar halike onGround vaghei mokhtalef bood —"
                    + " mamkene fall damage ro dor zade bashe.");
            default -> {
                sb.append("Check '").append(checkName).append("' in flag ro sabt karde");
                if (tpsLow) sb.append(" (TPS paeen bude moqe'e in flag: ").append(String.format("%.1f", tps)).append(")");
                sb.append(".");
            }
        }

        if (sb.length() == 0) {
            sb.append("Daliile daghigh moshakhas nist — verbose ro baraye jozaiyat bebin.");
        }
        return sb.toString();
    }

    private static void explainSimulation(String v, boolean tpsLow, StringBuilder sb) {
        Matcher m = SIM_VERBOSE.matcher(v);
        if (m.find()) {
            boolean sneak = "true".equals(m.group(1));
            boolean carpet = "true".equals(m.group(2));
            boolean bed = "true".equals(m.group(3));
            boolean water = "true".equals(m.group(4));

            if (sneak) {
                sb.append("Player neshaste bood (sneak) — harekate neshaste tabiei noisy hast,"
                        + " ehtemalan false flag.");
            } else if (carpet) {
                sb.append("Player ru farsh (carpet) bood — shape barkhorde farsh ba shabihsazi"
                        + " server farogh dare, in ye bug shenakhteshode hast, na cheat.");
            } else if (bed) {
                sb.append("Player ru ya kenare takht (bed) bood — shape barkhorde takht namotagharen"
                        + " hast, mamkene az parida ru takht bashe, na cheat.");
            } else if (water) {
                sb.append("Player too ab bood — fiziks shenagari (water) az piichidetarin chizaie"
                        + " shabihsazie vanilla hast, ehtemale false ziyade.");
            } else {
                sb.append("Hich kodum az sneak/carpet/bed/water fa'al nabudan — in ye offset");
                sb.append(" 'tamiz' hast, ehtemale cheat vaqei (speed/fly) bishtar az haalat-haie balaye.");
            }
            if (tpsLow) sb.append(" TPS paeen bud moqe'e in flag, ke khodesh mitoone delile asli bashe.");
        } else if (tpsLow) {
            sb.append("TPS paeen bud (lag-e server) moqe'e in flag — ehtemale ziyad false flag.");
        } else {
            sb.append("Pishbinie harekat ba chizi ke client ferestad farogh dasht — agar tekrari/posht"
                    + " sare ham nist, ehtemalan ye noise tabiei hast.");
        }
    }

    private static void explainTimer(String v, boolean tpsLow, StringBuilder sb) {
        Matcher m = PING_MS.matcher(v);
        if (m.find()) {
            int ping = Integer.parseInt(m.group(1));
            if (ping > 300) {
                sb.append("Ping kheili balas bud (").append(ping).append("ms) — in ehtemalan ye spike-e")
                        .append(" connection hast, na cheat (server age natoone packet ro be moqe' process kone false midad).");
            } else if (tpsLow) {
                sb.append("TPS paeen bud moqe'e in flag — lag-e server mitoone in timinge ro be ham bezane,")
                        .append(" na cheate vaqei.");
            } else {
                sb.append("Player sari'tar az hade majaz packet ferestad (ping: ").append(ping)
                        .append("ms) — agar fasele zamani ziyad nadasht ba flag ghabli, mamkene tabiei bashe.");
            }
        } else if (tpsLow) {
            sb.append("TPS paeen bud moqe'e in flag.");
        } else {
            sb.append("Player zudtar az ye tick-e vanilla (50ms) packet ferestad.");
        }
    }

    private static void explainFastBreak(String v, boolean tpsLow, StringBuilder sb) {
        Matcher diff = FASTBREAK_DIFF.matcher(v);
        Matcher delay = FASTBREAK_DELAY.matcher(v);
        if (diff.find()) {
            String ms = diff.group(1);
            String type = diff.group(2);
            sb.append("Block '").append(type).append("' sari'tar az pishbini shekaste shod (diff=")
                    .append(ms).append("ms).");
            if (tpsLow) {
                sb.append(" TPS paeen bud — in mitoone tamamen mas'ule in flag bashe (vaght-e processe");
                sb.append(" packet ha ru server be ham mikhore).");
            } else {
                sb.append(" Age diff kheili kam bud (~50ms, ye tick), ehtemalan rounding hast na cheat;");
                sb.append(" age sad ha ms ya bishtar bud, ehtemale FastBreak vaqei bishtare.");
            }
        } else if (delay.find()) {
            String ms = delay.group(1);
            String type = delay.group(2);
            sb.append("Fasele beine do bar shekastan-e block '").append(type).append("' kamtar az entezar")
                    .append(" bud (delay=").append(ms).append("ms) — age block instant-break bashe")
                    .append(" (masalan chaman/gol) ya WorldGuard deny karde bashe, in tabiei hast.");
        } else {
            sb.append("Sor'ate shekastane block az pishbinie vanilla sari'tar bud.");
        }
    }

    private static void explainGroundSpoof(String v, boolean tpsLow, StringBuilder sb) {
        Matcher m = GROUNDSPOOF.matcher(v);
        if (m.find()) {
            sb.append("Client edea kard onGround=").append(m.group(1)).append(", server chizi dige fekr mikard.");
            sb.append(" In mamkene az parida ru takht/farsh, GSit, ya teleport bashe — kheili vaght false hast.");
            if (tpsLow) sb.append(" TPS ham paeen bud moqe'e in flag.");
        } else {
            sb.append("Vaziate onGround-e client ba server farogh dasht.");
        }
    }

    private static void explainNuker(String v, StringBuilder sb) {
        Matcher sim = NUKER_SIM.matcher(v);
        Matcher rate = NUKER_RATE.matcher(v);
        if (sim.find()) {
            sb.append(sim.group(1)).append(" ta block-e MOJZA dar yek tick shekaste shodan — age hamashun");
            sb.append(" YE block-e sabet bashan (masalan WorldGuard deny karde), in ye bug-e shenakhteshode");
            sb.append(" hast na cheat. Age block-haye motafavet budan, ehtemale Nuker vaqei ziyade.");
        } else if (rate.find()) {
            sb.append("Sor'ate shekastane block (").append(rate.group(1)).append("/s) az hade tabiei bishtar bud.");
        } else {
            sb.append("Alguie shekastane block-ha moshkuk bud (chand block hamzaman ya kheili sari').");
        }
    }

    private static void explainFarBreak(String v, int ping, StringBuilder sb) {
        Matcher m = FARBREAK.matcher(v);
        if (m.find()) {
            double dist = Double.parseDouble(m.group(1));
            double max = Double.parseDouble(m.group(2));
            sb.append("Fasele shekastane block (").append(String.format("%.2f", dist)).append(") az hade")
                    .append(" majaz (").append(String.format("%.2f", max)).append(") bishtar bud.");
            double over = dist - max;
            if (over < 0.5) {
                sb.append(" Faseleye ezafe kam bud (").append(String.format("%.2f", over))
                        .append(") — ba ping=").append(ping).append("ms mamkene tabiei bashe, na reach-e vaqei.");
            } else {
                sb.append(" Faseleye ezafe ziyad bud — ehtemale reach hack vaqei bishtare.");
            }
        } else {
            sb.append("Player block-i ro az faseleye bishtar az hade majaz shekast.");
        }
    }

    private static void explainAutoClickerA(String v, StringBuilder sb) {
        Matcher cps = AUTOCLICKER_CPS.matcher(v);
        Matcher cv = AUTOCLICKER_CV.matcher(v);
        if (cps.find()) {
            double cpsVal = Double.parseDouble(cps.group(1));
            sb.append("Sor'ate click (CPS) ru miangin ye window-e 20-taei rasid be ").append(cpsVal).append(".");
            if (cpsVal < 22) {
                sb.append(" In kheili nazdike hade fiziki vaqei (20 CPS = ye click dar har tick) hast —");
                sb.append(" ehtemale ye click-zane kheili sari'e vaqei (jitter-click) bishtar az autoclicker.");
            } else {
                sb.append(" In az hade fiziki vaqei kheili bishtare, ehtemale autoclicker/macro ziyade.");
            }
        } else if (cv.find()) {
            sb.append("Fasele-haye beine click-ha kheili sabet/yeksan bud (cv=").append(cv.group(1))
                    .append(") — ensan-ha hatta vaght sari' click mikonan ye kam variance daran,");
            sb.append(" in alegoo bishtar shabihe macro hast.");
        } else {
            sb.append("Alguie click kardan moshkuk tashkhis dade shod.");
        }
    }

    private static void explainAutoClickerB(String v, int ping, StringBuilder sb) {
        Matcher m = AUTOCLICKERB_ANIMS.matcher(v);
        if (m.find()) {
            sb.append(m.group(1)).append(" ta packet swing too YEK tick-e client ferestade shod.");
            sb.append(" Age ping balas bashe ya connection (masalan mobile/PojavLauncher) jitter dashte");
            sb.append(" bashe, packet-ha mamkene dastei dastei beresan va in ro tabiei neshun bedan");
            sb.append(" (ping: ").append(ping).append("ms).");
        } else {
            sb.append("Chand ta packet swing too yek tick ferestade shod — shabihe interact-aura/macro.");
        }
    }

    private static void explainTransactionOrder(String v, int ping, StringBuilder sb) {
        Matcher m = TRANSACTION_SKIPPED.matcher(v);
        if (m.find()) {
            sb.append(m.group(1)).append(" ta packet-e transaction az dast raft — in ba az dast raftane");
            sb.append(" packet too network ettefagh mioftad (ping: ").append(ping).append("ms), tabiei");
            sb.append(" hast va in check az tarahi khodesh punish nemikone, faghat alert mide.");
        } else {
            sb.append("Tartibe packet-haye transaction be ham khord — mamkene az packet loss bashe.");
        }
    }
}
