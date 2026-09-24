package ac.jester.anticheat.utils.latency;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import ac.jester.anticheat.utils.anticheat.MessageUtil;
import ac.jester.anticheat.utils.common.arguments.CommonGrimArguments;
import ac.jester.anticheat.utils.data.Pair;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

public class LatencyUtils {
    private final LinkedList<Pair<Integer, Runnable>> transactionMap = new LinkedList<>();
    private final GrimPlayer player;

    private final ArrayList<Runnable> tasksToRun = new ArrayList<>();

    public LatencyUtils(GrimPlayer player) {
        this.player = player;
    }

    public void addRealTimeTask(int transaction, Runnable runnable) {
        addRealTimeTask(transaction, false, runnable);
    }

    public void addRealTimeTaskAsync(int transaction, Runnable runnable) {
        addRealTimeTask(transaction, true, runnable);
    }

    public void addRealTimeTask(int transaction, boolean async, Runnable runnable) {
        if (player.lastTransactionReceived.get() >= transaction) {
            if (async) {
                player.runSafely(runnable);
            } else {
                runnable.run();
            }
            return;
        }
        synchronized (this) {
            transactionMap.add(new Pair<>(transaction, runnable));
        }
    }

    public void handleNettySyncTransaction(int transaction) {
        synchronized (this) {
            tasksToRun.clear();

            ListIterator<Pair<Integer, Runnable>> iterator = transactionMap.listIterator();
            while (iterator.hasNext()) {
                Pair<Integer, Runnable> pair = iterator.next();

                if (transaction + 1 < pair.first())
                    break;

                if (transaction == pair.first() - 1)
                    continue;

                tasksToRun.add(pair.second());
                iterator.remove();
            }

            for (Runnable runnable : tasksToRun) {
                try {
                    runnable.run();
                } catch (Exception e) {
                    LogUtil.error("An error has occurred when running transactions for player: " + player.user.getName(), e);
                    if (CommonGrimArguments.KICK_ON_TRANSACTION_ERRORS.value()) {
                        player.disconnect(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(player, GrimAPI.INSTANCE.getConfigManager().getDisconnectPacketError())));
                    }
                }
            }
        }
    }
}
