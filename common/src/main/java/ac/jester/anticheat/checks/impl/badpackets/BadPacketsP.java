package ac.jester.anticheat.checks.impl.badpackets;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;

@CheckData(name = "BadPacketsP", description = "Invalid click packets", experimental = true)
public class BadPacketsP extends Check implements PacketCheck {
    private int containerType = -1;
    private int containerId = -1;
    private int consecutiveBad = 0;
    private static final int MIN_CONSECUTIVE = 2;

    public BadPacketsP(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            WrapperPlayServerOpenWindow window = new WrapperPlayServerOpenWindow(event);
            this.containerType = window.getType();
            this.containerId = window.getContainerId();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
            WindowClickType clickType = wrapper.getWindowClickType();
            int button = wrapper.getButton();

            boolean flag = switch (clickType) {
                case PICKUP, QUICK_MOVE, CLONE -> button > 2 || button < 0;
                case SWAP -> (button > 8 || button < 0) && button != 40;
                case THROW -> button != 0 && button != 1;
                case QUICK_CRAFT -> button == 3 || button == 7 || button > 10 || button < 0;
                case PICKUP_ALL -> button != 0;
                case UNKNOWN -> true;
            };

            if (flag) {
                consecutiveBad++;
                if (consecutiveBad >= MIN_CONSECUTIVE) {
                    if (flagAndAlert("clickType=" + clickType.toString().toLowerCase() + ", button=" + button + (wrapper.getWindowId() == containerId ? ", container=" + containerType : "") + ", consecutive=" + consecutiveBad) && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                    consecutiveBad = 0;
                }
            } else {
                consecutiveBad = 0;
            }
        }
    }
}
