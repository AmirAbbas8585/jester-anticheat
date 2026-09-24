package ac.jester.anticheat.manager.tick.impl;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.manager.tick.Tickable;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;

public class ClientVersionSetter implements Tickable {
    @Override
    public void tick() {
        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            if (!ChannelHelper.isOpen(player.user.getChannel())) {
                GrimAPI.INSTANCE.getPlayerDataManager().onDisconnect(player.user);
                continue;
            }

            player.pollData();
        }
    }
}
