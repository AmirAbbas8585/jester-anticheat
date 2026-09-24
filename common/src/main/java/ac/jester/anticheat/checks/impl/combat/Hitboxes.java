package ac.jester.anticheat.checks.impl.combat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;

@CheckData(name = "Hitboxes", setback = 10)
public class Hitboxes extends Check {
    public Hitboxes(GrimPlayer player) {
        super(player);
    }
}
