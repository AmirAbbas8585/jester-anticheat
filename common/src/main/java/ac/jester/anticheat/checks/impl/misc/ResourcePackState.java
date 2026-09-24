package ac.jester.anticheat.checks.impl.misc;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientResourcePackStatus;

@CheckData(name = "ResourcePackState")
public final class ResourcePackState extends Check implements PacketCheck {
    private static final long MAX_LOADING_MS = 30_000L;
    private static final long LOAD_TAIL_MS = 5_000L;

    public ResourcePackState(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.RESOURCE_PACK_STATUS) return;

        String result = new WrapperPlayClientResourcePackStatus(event).getResult().name();
        if (result.equals("ACCEPTED") || result.equals("DOWNLOADED")) {
            player.resourcePackLoadingUntil = System.currentTimeMillis() + MAX_LOADING_MS;
        } else {
            player.resourcePackLoadingUntil = System.currentTimeMillis() + LOAD_TAIL_MS;
        }
    }
}
