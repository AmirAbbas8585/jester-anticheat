package ac.jester.anticheat.events.packets.worldreader;

import ac.jester.anticheat.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_16.Chunk_v1_9;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;

import java.util.BitSet;

public class PacketWorldReaderEight extends BasePacketWorldReader {
    @Override
    public void handleMapChunkBulk(final GrimPlayer player, final PacketSendEvent event) {
        PacketWrapper<?> wrapper = new PacketWrapper<>(event);
        ByteBuf buffer = (ByteBuf) wrapper.getBuffer();

        boolean skylight = wrapper.readBoolean();
        int columns = wrapper.readVarInt();
        int[] x = new int[columns];
        int[] z = new int[columns];
        int[] mask = new int[columns];

        for (int column = 0; column < columns; column++) {
            x[column] = wrapper.readInt();
            z[column] = wrapper.readInt();
            mask[column] = wrapper.readUnsignedShort();
        }

        for (int column = 0; column < columns; column++) {
            BitSet bitset = BitSet.valueOf(new long[]{mask[column]});
            Chunk_v1_9[] chunkSections = new Chunk_v1_9[16];
            readChunk(buffer, chunkSections, bitset);

            int chunks = Integer.bitCount(mask[column]);
            buffer.readerIndex(buffer.readerIndex() + 256 + (chunks * 2048) + (skylight ? (chunks * 2048) : 0));

            addChunkToCache(event, player, chunkSections, true, x[column], z[column]);
        }
    }

    @Override
    public void handleMapChunk(final GrimPlayer player, final PacketSendEvent event) {
        PacketWrapper<?> wrapper = new PacketWrapper<>(event);

        final int chunkX = wrapper.readInt();
        final int chunkZ = wrapper.readInt();
        boolean groundUp = wrapper.readBoolean();

        BitSet mask = BitSet.valueOf(new long[]{(long) wrapper.readUnsignedShort()});
        int size = wrapper.readVarInt();

        final Chunk_v1_9[] chunks = new Chunk_v1_9[16];
        this.readChunk((ByteBuf) event.getByteBuf(), chunks, mask);

        this.addChunkToCache(event, player, chunks, groundUp, chunkX, chunkZ);

        event.setLastUsedWrapper(null);
    }

    private void readChunk(final ByteBuf buf, final Chunk_v1_9[] chunks, final BitSet set) {
        for (int ind = 0; ind < 16; ++ind) {
            if (set.get(ind)) {
                chunks[ind] = readChunk(buf);
            }
        }
    }

    public Chunk_v1_9 readChunk(final ByteBuf in) {
        ListPalette palette = new ListPalette(4);
        BitStorage storage = new BitStorage(4, 4096);
        DataPalette dataPalette = new DataPalette(palette, storage, PaletteType.CHUNK);

        palette.stateToId(0);

        int lastNext = -1;
        int lastID = -1;
        int blockCount = 0;

        for (int i = 0; i < 4096; ++i) {
            int next = in.readShort();

            if (next != 0) {
                blockCount++;
            }

            if (next != lastNext) {
                lastNext = next;
                next = (short) (((next & 0xFF00) >> 8) | (next << 8));
                dataPalette.set(i & 15, (i >> 8) & 15, (i >> 4) & 15, next);
                lastID = dataPalette.storage.get(i);
                continue;
            }

            dataPalette.storage.set(i, lastID);
        }

        return new Chunk_v1_9(blockCount, dataPalette);
    }
}
