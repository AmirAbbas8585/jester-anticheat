package ac.jester.anticheat.utils.payload;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook;
import org.jetbrains.annotations.NotNull;

public record PayloadBookEdit(@NotNull ItemStack itemStack) implements Payload {
    public PayloadBookEdit(byte[] data) {
        this(Payload.wrapper(data).readItemStack());
    }

    @Override
    public void write(PacketWrapper<?> wrapper) {
        wrapper.writeItemStack(itemStack);
    }
}
