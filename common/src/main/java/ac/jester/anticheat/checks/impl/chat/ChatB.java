package ac.jester.anticheat.checks.impl.chat;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;

@CheckData(name = "ChatB", description = "Invalid chat message")
public class ChatB extends Check implements PacketCheck {
    public ChatB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {
            String message = new WrapperPlayClientChatMessage(event).getMessage();
            if (checkChatMessage(message)) {
                event.setCancelled(true);
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
            String command = "/" + new WrapperPlayClientChatCommandUnsigned(event).getCommand();
            if (!command.stripTrailing().equals(command)) {
                if (flagAndAlert("command=" + command)) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
            String command = "/" + new WrapperPlayClientChatCommand(event).getCommand();
            if (!command.trim().equals(command)) {
                if (flagAndAlert("command=" + command)) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }

    public boolean checkChatMessage(String message) {
        if (message.isEmpty() || !message.trim().equals(message) || message.startsWith("/") && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) {
            if (flagAndAlert("message=" + message) && shouldModifyPackets()) {
                player.onPacketCancel();
                return true;
            }
        }
        return false;
    }
}
