package ac.jester.anticheat.checks.impl.misc;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.player.GrimPlayer;

@CheckData(name = "TransactionOrder")
public class TransactionOrder extends Check {
    public TransactionOrder(GrimPlayer player) {
        super(player);
    }
}
