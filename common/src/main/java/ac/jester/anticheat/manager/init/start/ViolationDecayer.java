package ac.jester.anticheat.manager.init.start;

import ac.grim.grimac.api.AbstractCheck;
import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.player.GrimPlayer;

/**
 * Decays every check's violation count once per second.
 *
 * The per-check config key is called <code>clear-per-second</code> and every
 * comment in config.yml describes it that way, but until now it was only ever
 * applied inside {@link Check#reward()} — i.e. when a check explicitly decided
 * the player looked fine on that tick. Whether violations actually decayed
 * therefore depended entirely on whether a given check bothered to call reward:
 *
 *  - The MultiActions checks never call it at all, so their violations were
 *    monotonic. A check with even a tiny false-positive rate would creep upward
 *    for the whole session and eventually alert, then keep alerting.
 *  - The Sprint checks only call it inside their own condition block (SprintB
 *    only while the player is actually sneaking), so a player who flagged once
 *    and then stopped sneaking kept that violation forever.
 *
 * That is what made these checks feel like they flag at random: an isolated
 * packet-ordering artefact never faded. Applying the decay on a timer makes the
 * setting mean what it says and lets one-off noise expire, while a real cheat —
 * which flags repeatedly and far faster than the decay — still climbs to its
 * threshold.
 */
public class ViolationDecayer implements StartableInitable {

    @Override
    public void start() {
        GrimAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(
                GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                    for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
                        for (AbstractCheck check : player.checkManager.allChecks.values()) {
                            if (check instanceof Check c) c.decayViolations();
                        }
                    }
                }, 20, 20);
    }
}
