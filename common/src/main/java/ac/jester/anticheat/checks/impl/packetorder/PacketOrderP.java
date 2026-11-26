package ac.jester.anticheat.checks.impl.packetorder;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@CheckData(name = "PacketOrderP", description = "Sent keepalive and transaction packets out of order", experimental = true)
public class PacketOrderP extends Check implements PacketCheck {
    private static final int DEFAULT_LIST_SIZE = 4;
    private static final int TARGET_MAX_LIST_SIZE = 8;

    private final ArrayList<KeepAliveData> pendingKeepAlives = new ArrayList<>(DEFAULT_LIST_SIZE);
    private boolean needsShrink;

    public PacketOrderP(@NotNull GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.KEEP_ALIVE) return;
        long id = new WrapperPlayClientKeepAlive(event).getId();

        for (int i = 0, size = pendingKeepAlives.size(); i < size; i++) {
            if (pendingKeepAlives.get(i).id == id) {
                pendingKeepAlives.remove(i);
                break;
            }
        }

        if (needsShrink && pendingKeepAlives.isEmpty()) {
            pendingKeepAlives.trimToSize();
            pendingKeepAlives.ensureCapacity(DEFAULT_LIST_SIZE);
            needsShrink = false;
        }
    }

    @Override
    public void onPacketSend(final @NotNull PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.KEEP_ALIVE) return;
        WrapperPlayServerKeepAlive packet = new WrapperPlayServerKeepAlive(event);

        KeepAliveData data = new KeepAliveData(packet.getId());

        pendingKeepAlives.add(data);

        player.addRealTimeTaskNext(() -> {
            if (pendingKeepAlives.remove(data)) {
                flagAndAlert();
            }
        });

        if (pendingKeepAlives.size() > TARGET_MAX_LIST_SIZE) {
            needsShrink = true;
        }

        event.getTasksAfterSend().add(player::sendTransaction);
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class KeepAliveData {
        public final long id;

        @Contract(pure = true)
        public KeepAliveData(long id) {
            this.id = id;
        }
    }
}
