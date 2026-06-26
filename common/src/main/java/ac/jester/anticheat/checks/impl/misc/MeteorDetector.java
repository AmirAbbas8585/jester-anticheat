package ac.jester.anticheat.checks.impl.misc;

import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.checks.Check;
import ac.jester.anticheat.checks.CheckData;
import ac.jester.anticheat.checks.type.PacketCheck;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;

/**
 * MeteorDetector — identifies players running Meteor Client.
 *
 * Detection method A: Plugin channel registration fingerprint.
 *   Meteor Client (Fabric) registers plugin channels during the play phase
 *   via a minecraft:register PLUGIN_MESSAGE. If any registered channel
 *   starts with "meteor-client:", Meteor Client is confirmed.
 *
 * Detection method B: Translation fingerprint (ZenithDetector technique).
 *   1. Server sends a fake OAK_SIGN block just above the player (client-only,
 *      not placed in the real world).
 *   2. Server sets the sign's text to a Meteor translation key JSON component
 *      via BLOCK_ENTITY_DATA.
 *   3. Server sends OPEN_SIGN_EDITOR to force-open the sign editor.
 *   4. When the client sends UPDATE_SIGN, the text reveals whether Meteor
 *      translated the key:
 *        - Vanilla / other clients: raw key, e.g. "meteor-client.gui.title"
 *        - Meteor Client: translated text, e.g. "Meteor Client"
 *   5. Server sends BLOCK_CHANGE to restore the fake block to air.
 *
 * This check is configured as non-punishable by default — it only alerts staff.
 * A single confirmed detection is reliable, but operators may choose to act
 * manually based on context (Meteor has legitimate non-combat uses).
 *
 * Known Meteor translation key:
 *   meteor-client.gui.title → "Meteor Client" (present in all recent versions)
 *
 * IMPORTANT — method B is opt-in (MeteorDetector.sign-check: false by default):
 * force-opening the sign editor is VISIBLE — every joining player briefly sees
 * a sign-edit screen pop up. Method A (channel fingerprint) is fully passive
 * and always active. Enable sign-check only if the popup is acceptable.
 */
@CheckData(name = "MeteorDetector", description = "Meteor Client fingerprint (plugin channel or translation)")
public final class MeteorDetector extends Check implements PacketCheck {

    // Translation key confirmed in Meteor Client source code
    private static final String METEOR_KEY = "meteor-client.gui.title";
    private static final String METEOR_KEY_TRANSLATED = "Meteor Client";

    // Channel prefix used by Meteor Client's network features
    private static final String METEOR_CHANNEL_PREFIX = "meteor-client:";

    // OAK_WALL_SIGN facing south, unwaterlogged — block state IDs are version-specific.
    // We use a pre-1.20.5 value (3706) as baseline; for other versions we skip method B.
    // This ID is never used to modify the real world — it is sent only to the client.
    private static final int OAK_WALL_SIGN_STATE_ID = 3706;

    private boolean alreadyDetected = false;
    private boolean signCheckTriggered = false;
    private boolean waitingForSignResponse = false;
    private Vector3i fakeSignPos = null;

    // Method B is intrusive (visible popup) — disabled unless explicitly enabled
    private boolean signCheckEnabled = false;

    public MeteorDetector(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        signCheckEnabled = config.getBooleanElse("MeteorDetector.sign-check", false);
    }

    // ── Method A: channel fingerprint ────────────────────────────────────────

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (alreadyDetected) return;

        // Listen for UPDATE_SIGN response (method B)
        if (waitingForSignResponse && event.getPacketType() == PacketType.Play.Client.UPDATE_SIGN) {
            WrapperPlayClientUpdateSign sign = new WrapperPlayClientUpdateSign(event);
            // Only intercept the response for OUR fake sign — a real sign edit
            // happening to occur while waiting must pass through untouched
            if (fakeSignPos != null && fakeSignPos.equals(sign.getBlockPosition())) {
                // Cancel so the server doesn't warn about a non-existent sign
                event.setCancelled(true);
                handleUpdateSign(sign);
            }
            return;
        }

        // Channel registration check (method A)
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage msg = new WrapperPlayClientPluginMessage(event);
            checkChannels(msg.getChannelName(), msg.getData());
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage msg = new WrapperConfigClientPluginMessage(event);
            checkChannels(msg.getChannelName(), msg.getData());
        }
    }

    private void checkChannels(String channel, byte[] data) {
        // minecraft:register contains null-separated channel names
        if (!channel.equals("minecraft:register") && !channel.equals("REGISTER")) return;

        String payload = new String(data);
        for (String ch : payload.split("\0")) {
            if (ch.startsWith(METEOR_CHANNEL_PREFIX)) {
                confirm("channel=" + ch);
                return;
            }
        }
    }

    // ── Method B: translation fingerprint ────────────────────────────────────

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!signCheckEnabled || alreadyDetected || signCheckTriggered) return;
        // Trigger once after the first position packet the player receives
        // (ensures the world has loaded enough for our fake block to appear correctly)
        if (event.getPacketType() != PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) return;

        // Wait for stable ticking before consuming the one-shot trigger,
        // otherwise an early position packet permanently skips method B
        if (!player.isTickingReliablyFor(3)) return;

        signCheckTriggered = true;
        triggerSignCheck();
    }

    private void triggerSignCheck() {
        // Place the fake sign 5 blocks above the player's current head position
        int x = (int) Math.floor(player.x);
        int y = (int) Math.floor(player.y) + 5;
        int z = (int) Math.floor(player.z);
        fakeSignPos = new Vector3i(x, y, z);

        try {
            // 1. Send a fake OAK_WALL_SIGN block to the client (not placed in world)
            player.user.sendPacket(new WrapperPlayServerBlockChange(fakeSignPos, OAK_WALL_SIGN_STATE_ID));

            // 2. Set sign text to a Meteor translation key via BLOCK_ENTITY_DATA
            NBTCompound signNbt = buildSignNbt();
            player.user.sendPacket(new WrapperPlayServerBlockEntityData(
                    fakeSignPos, BlockEntityTypes.SIGN, signNbt));

            // 3. Force-open the sign editor
            player.user.sendPacket(new WrapperPlayServerOpenSignEditor(fakeSignPos, true));

            waitingForSignResponse = true;

        } catch (Exception e) {
            // If anything fails (wrong version, missing wrapper, etc.), silently skip method B
            cleanupFakeSign();
        }
    }

    private void handleUpdateSign(WrapperPlayClientUpdateSign sign) {
        waitingForSignResponse = false;
        cleanupFakeSign();

        // Check if any line contains the translated Meteor key
        for (String line : sign.getTextLines()) {
            if (line != null && line.contains(METEOR_KEY_TRANSLATED)) {
                confirm("translation_match=" + line.trim());
                return;
            }
        }
        // No match: just a player who closed the editor without Meteor translating
    }

    private void cleanupFakeSign() {
        if (fakeSignPos == null) return;
        try {
            // Restore block to air (state ID 0)
            player.user.sendPacket(new WrapperPlayServerBlockChange(fakeSignPos, 0));
        } catch (Exception ignored) {
        }
        fakeSignPos = null;
    }

    private NBTCompound buildSignNbt() {
        // Build the sign NBT: front_text.messages contains 4 component lines as JSON strings
        String translateJson = "{\"translate\":\"" + METEOR_KEY + "\"}";

        NBTList<NBTString> messages = new NBTList<>(NBTType.STRING);
        messages.addTag(new NBTString(translateJson));
        messages.addTag(new NBTString("{\"text\":\"\"}"));
        messages.addTag(new NBTString("{\"text\":\"\"}"));
        messages.addTag(new NBTString("{\"text\":\"\"}"));

        NBTCompound frontText = new NBTCompound();
        frontText.setTag("messages", messages);
        frontText.setTag("has_glowing_text", new com.github.retrooper.packetevents.protocol.nbt.NBTByte((byte) 0));
        frontText.setTag("color", new NBTString("black"));

        NBTCompound nbt = new NBTCompound();
        nbt.setTag("front_text", frontText);
        nbt.setTag("is_waxed", new com.github.retrooper.packetevents.protocol.nbt.NBTByte((byte) 0));
        return nbt;
    }

    // ── Confirm detection ─────────────────────────────────────────────────────

    private void confirm(String reason) {
        if (alreadyDetected) return;
        alreadyDetected = true;
        flagAndAlert("Meteor Client detected: " + reason);
    }
}
