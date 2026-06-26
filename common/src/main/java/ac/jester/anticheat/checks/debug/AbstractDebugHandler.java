package ac.jester.anticheat.checks.debug;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.player.GrimPlayer;

public abstract class AbstractDebugHandler extends Check {
    public AbstractDebugHandler(GrimPlayer player) {
        super(player);
    }

    public abstract void toggleListener(GrimPlayer player);

    public abstract boolean toggleConsoleOutput();
}
