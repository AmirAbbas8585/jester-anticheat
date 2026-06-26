package ac.jester.anticheat.checks.impl.chat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTabComplete;

@CheckData(name = "ChatA", experimental = true)
public class ChatA extends Check implements PacketCheck {

    public ChatA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.TAB_COMPLETE && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
            WrapperPlayClientTabComplete wrapper = new WrapperPlayClientTabComplete(event);
            String text = wrapper.getText();
            if (text.equals("/") || text.trim().isEmpty()) {
                if (flagAndAlert("")) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
