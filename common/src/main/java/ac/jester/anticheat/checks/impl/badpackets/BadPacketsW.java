package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;

@CheckData(name = "BadPacketsW", description = "Interacted with non-existent entity", experimental = true)
public class BadPacketsW extends Check {
    public BadPacketsW(GrimPlayer player) {
        super(player);
    }
}
