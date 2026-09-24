package ac.jester.anticheat.utils.chunks;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;

public record Column(int x, int z, BaseChunk[] chunks, int transaction) {
    public void mergeChunks(BaseChunk[] toMerge) {
        for (int i = 0; i < 16; i++) {
            if (toMerge[i] != null) chunks[i] = toMerge[i];
        }
    }
}
