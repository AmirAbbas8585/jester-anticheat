package ac.jester.anticheat.utils.payload;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;
import org.jetbrains.annotations.NotNull;

public record PayloadItemName(@NotNull String itemName) implements Payload {
    public PayloadItemName(byte[] data) {
        this(Payload.wrapper(data).readString());
    }

    @Override
    public void write(PacketWrapper<?> wrapper) {
        wrapper.writeString(itemName);
    }
}
