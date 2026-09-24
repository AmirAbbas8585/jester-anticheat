package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;

@CheckData(name = "BadPacketsN", setback = 0)
public class BadPacketsN extends Check {
    public BadPacketsN(final GrimPlayer player) {
        super(player);
    }
}
