package ac.jester.anticheat.checks;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying.isFlying;

// Class from https://github.com/Tecnio/AntiCheatBase/blob/master/src/main/java/me/tecnio/anticheat/check/Check.java
@Getter
public class Check extends GrimProcessor implements AbstractCheck {
    protected @NotNull final GrimPlayer player;

    public double violations;
    private double decay;
    private double setbackVL;

    private String checkName;
    private String configName;
    private String alternativeName;
    private String displayName;
    private String description;

    private boolean experimental;
    private @Setter boolean isEnabled;

    private boolean exemptPermission;
    private boolean noSetbackPermission;
    private boolean noModifyPacketPermission;
    private long lastViolationTime;

    public Check(final @NotNull GrimPlayer player) {
        this.player = Objects.requireNonNull(player);

        final CheckData checkData = this.getClass().getAnnotation(CheckData.class);
        if (checkData != null) {
            this.checkName = checkData.name();
            this.configName = checkData.configName();
            // Fall back to check name
            if (this.configName.equals("DEFAULT")) this.configName = this.checkName;
            this.decay = checkData.decay();
            this.setbackVL = checkData.setback();
            this.alternativeName = checkData.alternativeName();
            this.experimental = checkData.experimental();
            this.description = checkData.description();
            this.displayName = this.checkName;
        }

        reload();
    }

    public boolean shouldModifyPackets() {
        return isEnabled
                && !player.disableGrim
                && !player.noModifyPacketPermission
                && !noModifyPacketPermission
                && !exemptPermission;
    }

    public final void updatePermissions() {
        if (configName == null || player.platformPlayer == null) return;
        final String id = configName.toLowerCase();
        // defaultIfUnset=false is critical: these per-check nodes are not
        // registered in plugin.yml, and Bukkit's default for unregistered
        // permissions is OP — without it every OP was silently exempt from
        // every check (no flags, no alerts)
        exemptPermission = player.platformPlayer.hasPermission("jester.exempt." + id, false)
                || player.platformPlayer.hasPermission("jester.exempt." + id, false);
        noSetbackPermission = player.platformPlayer.hasPermission("jester.nosetback." + id, false)
                || player.platformPlayer.hasPermission("jester.nosetback." + id, false);
        noModifyPacketPermission = player.platformPlayer.hasPermission("jester.nomodifypacket." + id, false)
                || player.platformPlayer.hasPermission("jester.nomodifypacket." + id, false);
    }

    public final boolean flagAndAlert(String verbose) {
        if (flag(verbose)) {
            alert(verbose);
            return true;
        }
        return false;
    }

    public final boolean flagAndAlert() {
        return flagAndAlert("");
    }

    public final boolean flag() {
        return flag("");
    }

    public final boolean flag(String verbose) {
        if (player.disableGrim || (experimental && !player.isExperimentalChecks()) || exemptPermission)
            return false; // Avoid calling event if disabled

        // Respect the per-check `enabled` config — a check set to enabled: false
        // truly stops flagging (no violation, no alert, no setback), not just
        // silenced alerts. Cached lookup, so this is cheap.
        ac.jester.anticheat.manager.JesterCheckConfig.CheckSettings checkCfg = configName == null
                ? null : ac.jester.anticheat.manager.JesterCheckConfig.get(configName);
        if (checkCfg != null && !checkCfg.enabled)
            return false;

        // minimum-tps was documented in every check's config block and read into
        // CheckSettings, but nothing ever actually compared it against the live
        // TPS — a server lag spike (packets processed late/bunched) distorts
        // wall-clock-based timing checks like FastBreak with no way to suppress
        // it. Below this TPS, treat it as noise rather than a violation.
        if (checkCfg != null
                && GrimAPI.INSTANCE.getPlatformServer().getTPS() < checkCfg.minimumTps)
            return false;

        // GSit seats, crawling, and player-sit stacks distort position, pose,
        // ground state, and hitboxes — suppress all flags while seated and
        // shortly after standing up (the dismount teleport)
        if (ac.jester.anticheat.hooks.ExemptionProvider.safe().isSitting(player))
            return false;

        FlagEvent event = new FlagEvent(player, this, verbose);
        GrimAPI.INSTANCE.getEventBus().post(event);
        if (event.isCancelled()) return false;

        player.punishmentManager.handleViolation(this);
        lastViolationTime = System.currentTimeMillis();
        violations++;
        return true;
    }

    public final boolean flagWithSetback() {
        return flagWithSetback("");
    }

    public final boolean flagWithSetback(String verbose) {
        if (flag(verbose)) {
            setbackIfAboveSetbackVL();
            return true;
        }
        return false;
    }

    public final boolean flagAndAlertWithSetback() {
        return flagAndAlertWithSetback("");
    }

    public final boolean flagAndAlertWithSetback(String verbose) {
        if (flagAndAlert(verbose)) {
            setbackIfAboveSetbackVL();
            return true;
        }
        return false;
    }

    public final void reward() {
        violations = Math.max(0, violations - decay);
    }

    @Override
    public final void reload(ConfigManager configuration) {
        decay = configuration.getDoubleElse(configName + ".decay", decay);
        setbackVL = configuration.getDoubleElse(configName + ".setbackvl", setbackVL);
        displayName = configuration.getStringElse(configName + ".displayname", checkName);
        description = configuration.getStringElse(configName + ".description", description);

        if (setbackVL == -1) setbackVL = Double.MAX_VALUE;
        onReload(configuration);
    }

    @Override
    public void onReload(ConfigManager config) {

    }

    public boolean alert(String verbose) {
        return player.punishmentManager.handleAlert(player, verbose, this);
    }

    public boolean setbackIfAboveSetbackVL() {
        if (shouldSetback()) {
            return player.getSetbackTeleportUtil().executeViolationSetback();
        }
        return false;
    }

    public boolean shouldSetback() {
        return !noSetbackPermission && violations > setbackVL;
    }

    public String formatOffset(double offset) {
        return offset > 0.001 ? String.format("%.5f", offset) : String.format("%.2E", offset);
    }

    public static boolean isTransaction(PacketTypeCommon packetType) {
        return packetType == PacketType.Play.Client.PONG ||
                packetType == PacketType.Play.Client.WINDOW_CONFIRMATION;
    }

    public static boolean isAsync(PacketTypeCommon packetType) {
        return packetType == PacketType.Play.Client.KEEP_ALIVE
                || packetType == PacketType.Play.Client.CHUNK_BATCH_ACK;
    }

    public boolean isUpdate(PacketTypeCommon packetType) {
        return isFlying(packetType)
                || packetType == PacketType.Play.Client.CLIENT_TICK_END
                || isTransaction(packetType);
    }

    public boolean isTickPacket(PacketTypeCommon packetType) {
        if (isTickPacketIncludingNonMovement(packetType)) {
            if (isFlying(packetType)) {
                return !player.packetStateData.lastPacketWasTeleport && !player.packetStateData.lastPacketWasOnePointSeventeenDuplicate;
            }
            return true;
        }
        return false;
    }

    public boolean isTickPacketIncludingNonMovement(PacketTypeCommon packetType) {
        // On 1.21.2+ fall back to the TICK_END packet IF the player did not send a movement packet for their tick
        // TickTimer checks to see if player did not send a tick end packet before new flying packet is sent
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2)
                && !player.packetStateData.didSendMovementBeforeTickEnd) {
            if (packetType == PacketType.Play.Client.CLIENT_TICK_END) {
                return true;
            }
        }

        return isFlying(packetType);
    }

    // Backported from upstream Grim (2.0 branch, "fix noslow with invalid drop
    // item packets"): cancelling a DROP_ITEM/DROP_ITEM_STACK packet leaves the
    // client thinking it dropped an item it didn't actually drop — desyncs
    // inventory state and can itself look like a NoSlow false. RELEASE_USE_ITEM
    // is excluded for the same kind of reason (was already excluded before this).
    public boolean canCancel(com.github.retrooper.packetevents.protocol.player.DiggingAction action) {
        return action != com.github.retrooper.packetevents.protocol.player.DiggingAction.RELEASE_USE_ITEM
                // 1.8- doesn't predict dropping items, so it's safe to cancel them there.
                && (action != com.github.retrooper.packetevents.protocol.player.DiggingAction.DROP_ITEM
                        && action != com.github.retrooper.packetevents.protocol.player.DiggingAction.DROP_ITEM_STACK
                        || player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8));
    }

}
