package ac.jester.anticheat.checks.impl.misc;

import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientResourcePackStatus;

/**
 * Not a detection — tracks the player's resource-pack download state so movement
 * and timer checks can grace it. While a pack is downloading the client can
 * freeze or stop sending movement (especially big ItemsAdder packs), which
 * otherwise desyncs the prediction and false-flags the player.
 *
 * A hard timeout caps how long the grace can last so it can't be abused by a
 * client that simply never reports the pack as loaded.
 */
@CheckData(name = "ResourcePackState")
public final class ResourcePackState extends Check implements PacketCheck {

    private static final long MAX_LOADING_MS = 30_000L;
    // After the pack finishes loading the client unfreezes and has to catch up
    // (replay buffered movement) — keep gracing briefly so that resync, and the
    // re-apply of a freshly generated ItemsAdder pack (/iazip), doesn't flag.
    private static final long LOAD_TAIL_MS = 5_000L;

    public ResourcePackState(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.RESOURCE_PACK_STATUS) return;

        // Use the enum name so this stays valid across PacketEvents versions that
        // add/rename result constants.
        String result = new WrapperPlayClientResourcePackStatus(event).getResult().name();
        if (result.equals("ACCEPTED") || result.equals("DOWNLOADED")) {
            // Player started downloading — open the (long) grace window
            player.resourcePackLoadingUntil = System.currentTimeMillis() + MAX_LOADING_MS;
        } else {
            // SUCCESSFULLY_LOADED / DECLINED / FAILED_* / DISCARDED / INVALID_URL
            // → finished, but keep a short tail so the just-unfrozen client can
            // resync without flagging
            player.resourcePackLoadingUntil = System.currentTimeMillis() + LOAD_TAIL_MS;
        }
    }
}
