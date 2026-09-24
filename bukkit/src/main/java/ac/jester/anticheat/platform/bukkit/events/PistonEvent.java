package ac.jester.anticheat.platform.bukkit.events;

import ac.jester.anticheat.GrimAPI;
import ac.jester.anticheat.platform.bukkit.utils.convert.BukkitConversionUtils;
import ac.jester.anticheat.player.GrimPlayer;
import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.PistonData;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.ArrayList;
import java.util.List;

public class PistonEvent implements Listener {
    private final Material SLIME_BLOCK = Material.getMaterial("SLIME_BLOCK");
    private final Material HONEY_BLOCK = Material.getMaterial("HONEY_BLOCK");

    private static final double MAX_HORIZONTAL_DISTANCE = 24.0;
    private static final double MAX_VERTICAL_DISTANCE = 64.0;

    private static boolean isCloseEnough(Vector3i vectorA, Vector3d vectorB) {
        return Math.abs(vectorA.getX() - vectorB.getX()) <= MAX_HORIZONTAL_DISTANCE
                && Math.abs(vectorA.getY() - vectorB.getY()) <= MAX_VERTICAL_DISTANCE
                && Math.abs(vectorA.getZ() - vectorB.getZ()) <= MAX_HORIZONTAL_DISTANCE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonPushEvent(BlockPistonExtendEvent event) {
        boolean hasSlimeBlock = false;
        boolean hasHoneyBlock = false;

        List<SimpleCollisionBox> boxes = new ArrayList<>();
        for (Block block : event.getBlocks()) {
            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(block.getX(),
                            block.getY(),
                            block.getZ()));
            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(block.getX() + event.getDirection().getModX(),
                            block.getY() + event.getDirection().getModY(),
                            block.getZ() + event.getDirection().getModZ()));

            if (block.getType() == SLIME_BLOCK) {
                hasSlimeBlock = true;
            }

            if (block.getType() == HONEY_BLOCK) {
                hasHoneyBlock = true;
            }
        }

        Block piston = event.getBlock();

        boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                .offset(piston.getX() + event.getDirection().getModX(),
                        piston.getY() + event.getDirection().getModY(),
                        piston.getZ() + event.getDirection().getModZ()));

        final int chunkX = event.getBlock().getX() >> 4;
        final int chunkZ = event.getBlock().getZ() >> 4;
        final BlockFace blockFace = BukkitConversionUtils.fromBukkitFace(event.getDirection());
        final Vector3i sourcePos = new Vector3i(piston.getX(), piston.getY(), piston.getZ());

        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            if (isCloseEnough(sourcePos, player.compensatedEntities.self.trackedServerPosition.getPos()) && player.compensatedWorld.isChunkLoaded(chunkX, chunkZ)) {
                final int lastTrans = player.lastTransactionSent.get();
                PistonData data = new PistonData(blockFace, boxes, lastTrans, true, hasSlimeBlock, hasHoneyBlock);
                player.latencyUtils.addRealTimeTaskAsync(lastTrans, () -> player.compensatedWorld.activePistons.add(data));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetractEvent(BlockPistonRetractEvent event) {
        boolean hasSlimeBlock = false;
        boolean hasHoneyBlock = false;

        List<SimpleCollisionBox> boxes = new ArrayList<>();
        BlockFace face = BukkitConversionUtils.fromBukkitFace(event.getDirection());

        if (event.getBlocks().isEmpty()) {
            Block piston = event.getBlock();

            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(piston.getX() + face.getModX(),
                            piston.getY() + face.getModY(),
                            piston.getZ() + face.getModZ()));
        }

        for (Block block : event.getBlocks()) {
            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(block.getX(), block.getY(), block.getZ()));
            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(block.getX() + face.getModX(), block.getY() + face.getModY(), block.getZ() + face.getModZ()));

            if (block.getType() == SLIME_BLOCK) {
                hasSlimeBlock = true;
            }

            if (block.getType() == HONEY_BLOCK) {
                hasHoneyBlock = true;
            }
        }

        final int chunkX = event.getBlock().getX() >> 4;
        final int chunkZ = event.getBlock().getZ() >> 4;
        Vector3i sourcePos = new Vector3i(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());

        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            if (isCloseEnough(sourcePos, player.compensatedEntities.self.trackedServerPosition.getPos()) && player.compensatedWorld.isChunkLoaded(chunkX, chunkZ)) {
                int lastTrans = player.lastTransactionSent.get();
                PistonData data = new PistonData(face, boxes, lastTrans, false, hasSlimeBlock, hasHoneyBlock);
                player.latencyUtils.addRealTimeTaskAsync(lastTrans, () -> player.compensatedWorld.activePistons.add(data));
            }
        }
    }
}
