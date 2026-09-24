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
    private double baseDecay;
    private boolean timeDecay;
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
            if (this.configName.equals("DEFAULT")) this.configName = this.checkName;
            this.decay = checkData.decay();
            this.baseDecay = this.decay;
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
                && !exemptPermission
                && !ac.jester.anticheat.manager.BedrockPolicy.shouldSkip(player, configName);
    }

    public final void updatePermissions() {
        if (configName == null || player.platformPlayer == null) return;
        final String id = configName.toLowerCase();
        exemptPermission = player.platformPlayer.hasPermission("jester.exempt." + id, false);
        noSetbackPermission = player.platformPlayer.hasPermission("jester.nosetback." + id, false);
        noModifyPacketPermission = player.platformPlayer.hasPermission("jester.nomodifypacket." + id, false);
    }

    private static final java.util.Map<String, Long> TPS_SUPPRESSION_WARNED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TPS_WARN_INTERVAL_MS = 300_000L;

    private static void warnSuppressedByTps(String checkName, double tps, double required) {
        if (checkName == null) return;
        long now = System.currentTimeMillis();
        Long last = TPS_SUPPRESSION_WARNED.get(checkName);
        if (last != null && now - last < TPS_WARN_INTERVAL_MS) return;
        TPS_SUPPRESSION_WARNED.put(checkName, now);
        ac.jester.anticheat.utils.anticheat.LogUtil.warn(String.format(
                "%s is NOT running: server TPS %.2f is below its minimum-tps of %.2f."
                        + " While this holds, %s cannot flag anything and a cheater using it"
                        + " will look clean. Lower checks.%s.minimum-tps or fix the lag.",
                checkName, tps, required, checkName, checkName));
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
            return false;

        ac.jester.anticheat.manager.JesterCheckConfig.CheckSettings checkCfg = configName == null
                ? null : ac.jester.anticheat.manager.JesterCheckConfig.get(configName);
        if (checkCfg != null && !checkCfg.enabled)
            return false;

        if (checkCfg != null) {
            double tps = GrimAPI.INSTANCE.getPlatformServer().getTPS();
            if (tps < checkCfg.minimumTps) {
                warnSuppressedByTps(configName, tps, checkCfg.minimumTps);
                return false;
            }
        }

        if (ac.jester.anticheat.hooks.ExemptionProvider.safe().isSitting(player))
            return false;

        if (ac.jester.anticheat.manager.BedrockPolicy.shouldSkip(player, configName))
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

    public final void decayViolations() {
        if (!timeDecay || violations <= 0) return;
        violations = Math.max(0, violations - decay);
    }

    @Override
    public final void reload(ConfigManager configuration) {
        double configured = configuration.getDoubleElse(configName + ".clear-per-second", Double.NaN);
        timeDecay = !Double.isNaN(configured);
        decay = timeDecay ? configured : baseDecay;
        setbackVL = configuration.getDoubleElse(configName + ".rubberband-at", setbackVL);
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

    public boolean hasPerTickMarker() {
        ClientVersion version = player.getClientVersion();
        return version.isOlderThanOrEquals(ClientVersion.V_1_8)
                || version.isNewerThanOrEquals(ClientVersion.V_1_21_2);
    }

    public boolean isTickPacketIncludingNonMovement(PacketTypeCommon packetType) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2)
                && !player.packetStateData.didSendMovementBeforeTickEnd) {
            if (packetType == PacketType.Play.Client.CLIENT_TICK_END) {
                return true;
            }
        }

        return isFlying(packetType);
    }

    public boolean canCancel(com.github.retrooper.packetevents.protocol.player.DiggingAction action) {
        return action != com.github.retrooper.packetevents.protocol.player.DiggingAction.RELEASE_USE_ITEM
                && (action != com.github.retrooper.packetevents.protocol.player.DiggingAction.DROP_ITEM
                        && action != com.github.retrooper.packetevents.protocol.player.DiggingAction.DROP_ITEM_STACK
                        || player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8));
    }
}
