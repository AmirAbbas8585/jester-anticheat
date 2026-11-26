package ac.jester.anticheat.utils.data;

import ac.jester.anticheat.utils.math.GrimMath;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.Contract;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
public final class RotationData {
    private final float yaw;
    private final float pitch;
    private final boolean relativeYaw;
    private final boolean relativePitch;
    private final int transaction;
    private boolean isAccepted;

    public static RotationData fromServer(ClientVersion version, float yaw, float pitch, boolean relativeYaw, boolean relativePitch, int transaction) {
        if (!relativePitch) {
            if (version.isOlderThan(ClientVersion.V_1_21_9)) pitch %= 360.0F;
            pitch = GrimMath.clamp(pitch, -90.0F, 90.0F);
        }

        return new RotationData(yaw, pitch, relativeYaw, relativePitch, transaction);
    }

    @Contract(mutates = "this")
    public void accept() {
        this.isAccepted = true;
    }

    public boolean allowRotation(float yaw, float pitch) {
        return (this.relativeYaw || this.yaw == yaw) && (this.relativePitch || this.pitch == pitch);
    }
}
