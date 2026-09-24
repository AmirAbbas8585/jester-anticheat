package ac.jester.anticheat.utils.blockplace;

import ac.jester.anticheat.events.packets.CheckManagerListener;
import ac.jester.anticheat.utils.anticheat.update.BlockPlace;
import ac.jester.anticheat.utils.blockstate.helper.BlockFaceHelper;
import ac.jester.anticheat.utils.collisions.CollisionData;
import ac.jester.anticheat.utils.collisions.datatypes.CollisionBox;
import ac.jester.anticheat.utils.latency.CompensatedWorld;
import ac.jester.anticheat.utils.math.Vector3dm;
import ac.jester.anticheat.utils.nmsutil.Dripstone;
import ac.jester.anticheat.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags;
import com.github.retrooper.packetevents.protocol.world.states.enums.*;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import com.github.retrooper.packetevents.util.Vector3i;

import java.util.*;

public enum BlockPlaceResult {
    ANVIL((player, place) -> {
        WrappedBlockState data = place.material.createBlockState(CompensatedWorld.blockVersion);
        data.setFacing(BlockFaceHelper.getClockWise(place.getPlayerFacing()));
        place.set(data);
    }, ItemTags.ANVIL),

    BED((player, place) -> {
        if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_12_2)) return;

        BlockFace facing = place.getPlayerFacing();
        if (place.isBlockFaceOpen(facing)) {
            place.set(place.material);
        }
    }, ItemTags.BEDS),

    SNOW((player, place) -> {
        Vector3i against = place.position;
        WrappedBlockState blockState = place.getExistingBlockData();
        int layers = 0;
        if (blockState.getType() == StateTypes.SNOW) {
            layers = blockState.getLayers();
        }

        WrappedBlockState below = place.getBelowState();

        if (!BlockTags.ICE.contains(below.getType()) && below.getType() != StateTypes.BARRIER) {
            boolean set = false;
            if (below.getType() != StateTypes.HONEY_BLOCK && below.getType() != StateTypes.SOUL_SAND) {
                if (place.isFullFace(BlockFace.DOWN)) {
                    set = true;
                }
            } else {
                set = true;
            }

            if (set) {
                if (blockState.getType() == StateTypes.SNOW) {
                    WrappedBlockState snow = StateTypes.SNOW.createBlockState(CompensatedWorld.blockVersion);
                    snow.setLayers(Math.min(8, layers + 1));
                    place.set(against, snow);
                } else {
                    place.set();
                }
            }
        }
    }, ItemTypes.SNOW),

    SLAB((player, place) -> {
        Vector3dm clickedPos = place.getClickedLocation();
        WrappedBlockState slabData = place.material.createBlockState(CompensatedWorld.blockVersion);
        WrappedBlockState existing = place.getExistingBlockData();

        if (BlockTags.SLABS.contains(existing.getType())) {
            slabData.setTypeData(Type.DOUBLE);
            place.set(place.position, slabData);
        } else {
            BlockFace direction = place.getFace();
            boolean clickedTop = direction != BlockFace.DOWN && (direction == BlockFace.UP || !(clickedPos.getY() > 0.5D));
            slabData.setTypeData(clickedTop ? Type.BOTTOM : Type.TOP);
            place.set(slabData);
        }
    }, ItemTags.SLABS),

    STAIRS((player, place) -> {
        BlockFace direction = place.getFace();
        WrappedBlockState stair = place.material.createBlockState(CompensatedWorld.blockVersion);
        stair.setFacing(place.getPlayerFacing());

        Half half = (direction != BlockFace.DOWN && (direction == BlockFace.UP || place.getClickedLocation().getY() < 0.5D)) ? Half.BOTTOM : Half.TOP;
        stair.setHalf(half);
        place.set(stair);
    }, ItemTags.STAIRS),

    END_ROD((player, place) -> {
        WrappedBlockState endRod = place.material.createBlockState(CompensatedWorld.blockVersion);
        endRod.setFacing(place.getFace());
        place.set(endRod);
    }, ItemTypes.END_ROD, ItemTypes.LIGHTNING_ROD),

    LADDER((player, place) -> {
        if (!place.replaceClicked) {
            WrappedBlockState existing = player.compensatedWorld.getBlock(place.position);
            if (existing.getType() == StateTypes.LADDER && existing.getFacing() == place.getFace()) {
                return;
            }
        }

        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (BlockFaceHelper.isFaceHorizontal(face) && place.isFullFace(face)) {
                WrappedBlockState ladder = place.material.createBlockState(CompensatedWorld.blockVersion);
                ladder.setFacing(face.getOppositeFace());
                place.set(ladder);
                return;
            }
        }
    }, ItemTypes.LADDER),

    FARM_BLOCK((player, place) -> {
        WrappedBlockState above = place.getAboveState();
        if (!above.getType().isBlocking() && !BlockTags.FENCE_GATES.contains(above.getType()) && above.getType() != StateTypes.MOVING_PISTON) {
            place.set(place.material);
        }
    }, ItemTypes.FARMLAND),

    AMETHYST_CLUSTER((player, place) -> {
        WrappedBlockState amethyst = place.material.createBlockState(CompensatedWorld.blockVersion);
        amethyst.setFacing(place.getFace());
        if (place.isFullFace(place.getFace().getOppositeFace())) place.set(amethyst);
    }, ItemTypes.AMETHYST_CLUSTER, ItemTypes.SMALL_AMETHYST_BUD, ItemTypes.MEDIUM_AMETHYST_BUD, ItemTypes.LARGE_AMETHYST_BUD),

    BAMBOO((player, place) -> {
        Vector3i clicked = place.position;
        if (player.compensatedWorld.getFluidLevelAt(clicked.getX(), clicked.getY(), clicked.getZ()) > 0)
            return;

        WrappedBlockState below = place.getBelowState();
        if (BlockTags.BAMBOO_PLANTABLE_ON.contains(below.getType())) {
            if (below.getType() == StateTypes.BAMBOO_SAPLING || below.getType() == StateTypes.BAMBOO) {
                place.set(StateTypes.BAMBOO);
            } else {
                WrappedBlockState above = place.getBelowState();
                if (above.getType() == StateTypes.BAMBOO_SAPLING || above.getType() == StateTypes.BAMBOO) {
                    place.set(StateTypes.BAMBOO);
                } else {
                    place.set(StateTypes.BAMBOO_SAPLING);
                }
            }
        }
    }, ItemTypes.BAMBOO),

    BELL((player, place) -> {
        BlockFace direction = place.getFace();
        WrappedBlockState bell = place.material.createBlockState(CompensatedWorld.blockVersion);

        boolean canSurvive = !BlockTags.FENCE_GATES.contains(place.getPlacedAgainstMaterial());
        if (!canSurvive) return;

        if (place.isFaceVertical()) {
            if (direction == BlockFace.DOWN) {
                bell.setAttachment(Attachment.CEILING);
                canSurvive = place.isFaceFullCenter(BlockFace.UP);
            }
            if (direction == BlockFace.UP) {
                bell.setAttachment(Attachment.FLOOR);
                canSurvive = place.isFullFace(BlockFace.DOWN);
            }
            bell.setFacing(place.getPlayerFacing());
        } else {
            boolean flag = place.isXAxis()
                    && place.isFullFace(BlockFace.EAST)
                    && place.isFullFace(BlockFace.WEST)

                    || place.isZAxis()
                    && place.isFullFace(BlockFace.SOUTH)
                    && place.isFullFace(BlockFace.NORTH);

            bell.setFacing(place.getFace().getOppositeFace());
            bell.setAttachment(flag ? Attachment.DOUBLE_WALL : Attachment.SINGLE_WALL);
            canSurvive = place.isFullFace(place.getFace().getOppositeFace());

            if (canSurvive) {
                place.set(bell);
                return;
            }

            boolean flag1 = place.isFullFace(BlockFace.DOWN);
            bell.setAttachment(flag1 ? Attachment.FLOOR : Attachment.CEILING);
            canSurvive = place.isFullFace(flag1 ? BlockFace.DOWN : BlockFace.UP);
        }
        if (canSurvive) place.set(bell);
    }, ItemTypes.BELL),

    CANDLE((player, place) -> {
        WrappedBlockState existing = place.getExistingBlockData();
        WrappedBlockState candle = place.material.createBlockState(CompensatedWorld.blockVersion);

        if (BlockTags.CANDLES.contains(existing.getType())) {
            if (existing.getCandles() == 4) return;
            candle.setCandles(existing.getCandles() + 1);
        }

        if (place.isFaceFullCenter(BlockFace.DOWN)) {
            place.set(candle);
        }
    }, ItemTags.CANDLES),

    SEA_PICKLE((player, place) -> {
        WrappedBlockState existing = place.getExistingBlockData();

        if (!place.isFullFace(BlockFace.DOWN) && !place.isFaceEmpty(BlockFace.DOWN)) return;

        if (existing.getType() == StateTypes.SEA_PICKLE) {
            if (existing.getPickles() == 4) return;
            existing.setPickles(existing.getPickles() + 1);
        } else {
            existing = StateTypes.SEA_PICKLE.createBlockState(CompensatedWorld.blockVersion);
        }

        place.set(existing);
    }, ItemTypes.SEA_PICKLE),

    CHAIN((player, place) -> {
        WrappedBlockState chain = place.material.createBlockState(CompensatedWorld.blockVersion);
        BlockFace face = place.getFace();

        switch (face) {
            case EAST:
            case WEST:
                chain.setAxis(Axis.X);
                break;
            case NORTH:
            case SOUTH:
                chain.setAxis(Axis.Z);
                break;
            case UP:
            case DOWN:
                chain.setAxis(Axis.Y);
                break;
        }

        place.set(chain);
    }, ItemTypes.CHAIN),

    COCOA((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (BlockFaceHelper.isFaceVertical(face)) continue;
            StateType mat = place.getDirectionalState(face).getType();
            if (mat == StateTypes.JUNGLE_LOG || mat == StateTypes.STRIPPED_JUNGLE_LOG || mat == StateTypes.JUNGLE_WOOD) {
                WrappedBlockState data = place.material.createBlockState(CompensatedWorld.blockVersion);
                data.setFacing(face);
                place.set(face, data);
                break;
            }
        }
    }, ItemTypes.COCOA_BEANS),

    DIRT_PATH((player, place) -> {
        WrappedBlockState state = place.getDirectionalState(BlockFace.UP);
        if (!state.getType().isBlocking() || BlockTags.FENCE_GATES.contains(state.getType())) {
            place.set(place.material);
        } else {
            place.set(StateTypes.DIRT);
        }
    }, ItemTypes.DIRT_PATH),

    HOPPER((player, place) -> {
        BlockFace opposite = place.getFace().getOppositeFace();
        WrappedBlockState hopper = place.material.createBlockState(CompensatedWorld.blockVersion);
        hopper.setFacing(place.isFaceVertical() ? BlockFace.DOWN : opposite);
        place.set(hopper);
    }, ItemTypes.HOPPER),

    LANTERN((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (BlockFaceHelper.isFaceHorizontal(face)) continue;
            WrappedBlockState lantern = place.material.createBlockState(CompensatedWorld.blockVersion);

            boolean isHanging = face == BlockFace.UP;
            lantern.setHanging(isHanging);

            boolean canSurvive = place.isFaceFullCenter(isHanging ? BlockFace.UP : BlockFace.DOWN) && !BlockTags.FENCE_GATES.contains(place.getPlacedAgainstMaterial());
            if (!canSurvive) continue;

            place.set(lantern);
            return;
        }
    }, ItemTypes.LANTERN, ItemTypes.SOUL_LANTERN),

    POINTED_DRIPSTONE((player, place) -> {
        BlockFace primaryDir = place.getNearestVerticalDirection().getOppositeFace();
        WrappedBlockState typePlacingOn = place.getDirectionalState(primaryDir.getOppositeFace());

        boolean primarySameType = typePlacingOn.hasProperty(StateValue.VERTICAL_DIRECTION) && typePlacingOn.getVerticalDirection().name().equals(primaryDir.name());
        boolean primaryValid = place.isFullFace(primaryDir.getOppositeFace()) || primarySameType;

        if (!primaryValid) {
            BlockFace secondaryDirection = primaryDir.getOppositeFace();
            WrappedBlockState secondaryType = place.getDirectionalState(secondaryDirection.getOppositeFace());
            boolean secondarySameType = secondaryType.hasProperty(StateValue.VERTICAL_DIRECTION) && secondaryType.getVerticalDirection().name().equals(primaryDir.name());

            primaryDir = secondaryDirection;
            primaryValid = place.isFullFace(secondaryDirection.getOppositeFace()) || secondarySameType;
        }

        if (!primaryValid) return;

        WrappedBlockState toPlace = StateTypes.POINTED_DRIPSTONE.createBlockState(CompensatedWorld.blockVersion);
        toPlace.setVerticalDirection(VerticalDirection.valueOf(primaryDir.name()));

        Vector3i placedPos = place.getPlacedBlockPos();
        Dripstone.update(player, toPlace, placedPos.getX(), placedPos.getY(), placedPos.getZ(), place.isSecondaryUse());

        place.set(toPlace);
    }, ItemTypes.POINTED_DRIPSTONE),

    CACTUS((player, place) -> {
        for (BlockFace face : BlockPlace.BY_2D) {
            if (place.isSolidBlocking(face) || place.isLava(face)) {
                return;
            }
        }

        if (place.isOn(StateTypes.CACTUS, StateTypes.SAND, StateTypes.RED_SAND) && !place.isLava(BlockFace.UP)) {
            place.set();
        }
    }, ItemTypes.CACTUS),

    CAKE((player, place) -> {
        if (place.isSolidBlocking(BlockFace.DOWN)) {
            place.set();
        }
    }, ItemTypes.CAKE),

    CANDLE_CAKE((player, place) -> {
        if (place.isSolidBlocking(BlockFace.DOWN)) {
            place.set();
        }
    }, ItemTypes.values().stream().filter(mat -> mat.getName().getKey().contains("candle_cake"))
            .toList().toArray(new ItemType[0])),

    PISTON_BASE((player, place) -> {
        WrappedBlockState piston = place.material.createBlockState(CompensatedWorld.blockVersion);
        piston.setFacing(place.getNearestVerticalDirection().getOppositeFace());
        place.set(piston);
    }, ItemTypes.PISTON, ItemTypes.STICKY_PISTON),

    AZALEA((player, place) -> {
        WrappedBlockState below = place.getBelowState();
        if (place.isOnDirt() || below.getType() == StateTypes.FARMLAND || below.getType() == StateTypes.CLAY) {
            place.set(place.material);
        }
    }, ItemTypes.AZALEA, ItemTypes.FLOWERING_AZALEA),

    CROP((player, place) -> {
        WrappedBlockState below = place.getBelowState();
        if (below.getType() == StateTypes.FARMLAND) {
            place.set();
        }
    }, ItemTypes.CARROT, ItemTypes.BEETROOT, ItemTypes.POTATO,
            ItemTypes.PUMPKIN_SEEDS, ItemTypes.MELON_SEEDS, ItemTypes.WHEAT_SEEDS, ItemTypes.TORCHFLOWER_SEEDS),

    SUGARCANE((player, place) -> {
        if (place.isOn(StateTypes.SUGAR_CANE)) {
            place.set();
            return;
        }

        if (place.isOnDirt() || place.isOn(StateTypes.SAND, StateTypes.RED_SAND)) {
            Vector3i pos = place.getPlacedBlockPos();
            pos = pos.withY(pos.getY() - 1);

            for (BlockFace direction : BlockPlace.BY_2D) {
                Vector3i toSearchPos = pos;
                toSearchPos = toSearchPos.withX(toSearchPos.getX() + direction.getModX());
                toSearchPos = toSearchPos.withZ(toSearchPos.getZ() + direction.getModZ());

                WrappedBlockState directional = player.compensatedWorld.getBlock(toSearchPos);
                if (Materials.isWater(player.getClientVersion(), directional) || directional.getType() == StateTypes.FROSTED_ICE) {
                    place.set();
                    return;
                }
            }
        }
    }, ItemTypes.SUGAR_CANE),

    CARPET((player, place) -> {
        if (!place.getBelowMaterial().isAir()) {
            place.set();
        }
    }, ItemTags.WOOL_CARPETS),

    MOSS_CARPET(CARPET.data, ItemTypes.MOSS_CARPET, ItemTypes.PALE_MOSS_CARPET),

    CHORUS_FLOWER((player, place) -> {
        WrappedBlockState blockstate = place.getBelowState();
        if (blockstate.getType() != StateTypes.CHORUS_PLANT && blockstate.getType() != StateTypes.END_STONE) {
            if (blockstate.getType().isAir()) {
                boolean flag = false;

                for (BlockFace direction : BlockPlace.BY_2D) {
                    WrappedBlockState blockstate1 = place.getDirectionalState(direction);
                    if (blockstate1.getType() == StateTypes.CHORUS_PLANT) {
                        if (flag) {
                            return;
                        }

                        flag = true;
                    } else if (!blockstate.getType().isAir()) {
                        return;
                    }
                }

                if (flag) {
                    place.set();
                }
            }
        } else {
            place.set();
        }
    }, ItemTypes.CHORUS_FLOWER),

    CHORUS_PLANT((player, place) -> {
        WrappedBlockState blockstate = place.getBelowState();
        boolean flag = !place.getAboveState().getType().isAir() && !blockstate.getType().isAir();

        for (BlockFace direction : BlockPlace.BY_2D) {
            WrappedBlockState blockstate1 = place.getDirectionalState(direction);
            if (blockstate1.getType() == StateTypes.CHORUS_PLANT) {
                if (flag) {
                    return;
                }

                Vector3i placedPos = place.getPlacedBlockPos();
                placedPos = placedPos.add(direction.getModX(), -1, direction.getModZ());

                WrappedBlockState blockstate2 = player.compensatedWorld.getBlock(placedPos);
                if (blockstate2.getType() == StateTypes.CHORUS_PLANT || blockstate2.getType() == StateTypes.END_STONE) {
                    place.set();
                }
            }
        }

        if (blockstate.getType() == StateTypes.CHORUS_PLANT || blockstate.getType() == StateTypes.END_STONE) {
            place.set();
        }
    }, ItemTypes.CHORUS_PLANT),

    DEAD_BUSH((player, place) -> {
        WrappedBlockState below = place.getBelowState();
        if (below.getType() == StateTypes.SAND || below.getType() == StateTypes.RED_SAND ||
                BlockTags.TERRACOTTA.contains(below.getType()) || place.isOnDirt()) {
            place.set(place.material);
        }
    }, ItemTypes.DEAD_BUSH),

    DIODE((player, place) -> {
        if (place.isFaceRigid(BlockFace.DOWN)) {
            place.set();
        }
    }, ItemTypes.REPEATER, ItemTypes.COMPARATOR, ItemTypes.REDSTONE),

    FUNGUS((player, place) -> {
        if (place.isOn(StateTypes.CRIMSON_NYLIUM, StateTypes.WARPED_NYLIUM, StateTypes.MYCELIUM, StateTypes.SOUL_SOIL, StateTypes.FARMLAND) || place.isOnDirt()) {
            place.set();
        }
    }, ItemTypes.CRIMSON_FUNGUS, ItemTypes.WARPED_FUNGUS),

    SPROUTS((player, place) -> {
        if (place.isOn(StateTypes.CRIMSON_NYLIUM, StateTypes.WARPED_NYLIUM, StateTypes.SOUL_SOIL, StateTypes.FARMLAND) || place.isOnDirt()) {
            place.set();
        }
    }, ItemTypes.NETHER_SPROUTS, ItemTypes.WARPED_ROOTS, ItemTypes.CRIMSON_ROOTS),

    NETHER_WART((player, place) -> {
        if (place.isOn(StateTypes.SOUL_SAND)) {
            place.set();
        }
    }, ItemTypes.NETHER_WART),

    WATERLILY((player, place) -> {
        WrappedBlockState below = place.getDirectionalState(BlockFace.DOWN);
        if (!place.isInLiquid() && (Materials.isWater(player.getClientVersion(), below) || place.isOn(StateTypes.ICE, StateTypes.FROSTED_ICE))) {
            place.set();
        }
    }, ItemTypes.LILY_PAD),

    WITHER_ROSE((player, place) -> {
        if (place.isOn(StateTypes.NETHERRACK, StateTypes.SOUL_SAND, StateTypes.SOUL_SOIL, StateTypes.FARMLAND) || place.isOnDirt()) {
            place.set();
        }
    }, ItemTypes.WITHER_ROSE),

    TORCH_OR_HEAD((player, place) -> {
        WrappedBlockState dir;
        boolean isTorch = place.material.getName().contains("torch");
        boolean isHead = place.material.getName().contains("head") || place.material.getName().contains("skull");
        boolean isWallSign = !isTorch && !isHead;

        if (isHead && player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_12_2))
            return;

        if (isTorch) {
            dir = StateTypes.WALL_TORCH.createBlockState(CompensatedWorld.blockVersion);
        } else if (isHead) {
            dir = StateTypes.PLAYER_WALL_HEAD.createBlockState(CompensatedWorld.blockVersion);
        } else {
            dir = StateTypes.OAK_WALL_SIGN.createBlockState(CompensatedWorld.blockVersion);
        }

        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (face != BlockFace.UP) {
                if (BlockFaceHelper.isFaceHorizontal(face)) {
                    boolean canPlace = isHead || ((isWallSign || place.isFullFace(face)) && (isTorch || place.isSolidBlocking(face)));
                    if (canPlace && face != BlockFace.UP) {
                        dir.setFacing(face.getOppositeFace());
                        place.set(dir);
                        return;
                    }
                } else {
                    boolean canPlace = isHead || ((isWallSign || place.isFaceFullCenter(face)) && (isTorch || place.isSolidBlocking(face)));
                    if (canPlace) {
                        place.set(place.material);
                        return;
                    }
                }
            }
        }
    }, ItemTypes.values().stream().filter(mat ->
                    mat.getName().getKey().contains("torch")
                            || (mat.getName().getKey().contains("head") || mat.getName().getKey().contains("skull")) && !mat.getName().getKey().contains("piston")
                            || mat.getName().getKey().contains("sign"))
            .toArray(ItemType[]::new)),

    MULTI_FACE_BLOCK((player, place) -> {
        StateType placedType = place.material;

        WrappedBlockState multiFace = place.getExistingBlockData();
        if (multiFace.getType() != placedType) {
            multiFace = placedType.createBlockState(CompensatedWorld.blockVersion);
        }

        for (BlockFace face : place.getNearestPlacingDirections()) {
            switch (face) {
                case UP:
                    if (multiFace.isUp()) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setUp(true);
                        break;
                    }
                    continue;
                case DOWN:
                    if (multiFace.isDown()) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setDown(true);
                        break;
                    }
                    continue;
                case NORTH:
                    if (multiFace.getNorth() == North.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setNorth(North.TRUE);
                        break;
                    }
                    continue;
                case SOUTH:
                    if (multiFace.getSouth() == South.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setSouth(South.TRUE);
                        break;
                    }
                    continue;
                case EAST:
                    if (multiFace.getEast() == East.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setEast(East.TRUE);
                        return;
                    }
                    continue;
                case WEST:
                    if (multiFace.getWest() == West.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setWest(West.TRUE);
                        break;
                    }
            }
        }

        place.set(multiFace);
    }, ItemTypes.GLOW_LICHEN, ItemTypes.SCULK_VEIN),

    FACE_ATTACHED_HORIZONTAL_DIRECTIONAL((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (place.isFullFace(face)) {
                place.set(place.material);
                return;
            }
        }
    }, ItemTypes.values().stream().filter(mat -> mat.getName().getKey().contains("button")
                    || mat.getName().getKey().contains("lever"))
            .toArray(ItemType[]::new)),

    GRINDSTONE((player, place) -> {
        WrappedBlockState stone = place.material.createBlockState(CompensatedWorld.blockVersion);
        if (place.isFaceVertical()) {
            stone.setFace(place.getPlayerFacing() == BlockFace.UP ? Face.CEILING : Face.FLOOR);
        } else {
            stone.setFace(Face.WALL);
        }
        stone.setFacing(place.getPlayerFacing());
        place.set(stone);
    }, ItemTypes.GRINDSTONE),

    BANNER((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (place.isSolidBlocking(face) && face != BlockFace.UP) {
                if (BlockFaceHelper.isFaceHorizontal(face)) {
                    WrappedBlockState dir = StateTypes.BLACK_WALL_BANNER.createBlockState(CompensatedWorld.blockVersion);
                    dir.setFacing(face.getOppositeFace());
                    place.set(dir);
                } else {
                    place.set(place.material);
                }
                break;
            }
        }
    }, ItemTags.BANNERS),

    BIG_DRIPLEAF((player, place) -> {
        WrappedBlockState existing = place.getDirectionalState(BlockFace.DOWN);
        if (place.isFullFace(BlockFace.DOWN) || existing.getType() == StateTypes.BIG_DRIPLEAF || existing.getType() == StateTypes.BIG_DRIPLEAF_STEM) {
            place.set(place.material);
        }
    }, ItemTypes.BIG_DRIPLEAF),

    SMALL_DRIPLEAF((player, place) -> {
        WrappedBlockState existing = place.getDirectionalState(BlockFace.DOWN);
        if (place.isBlockFaceOpen(BlockFace.UP) && BlockTags.SMALL_DRIPLEAF_PLACEABLE.contains(existing.getType()) || (place.isInWater() && (place.isOnDirt() || existing.getType() == StateTypes.FARMLAND))) {
            place.set(place.material);
        }
    }, ItemTypes.SMALL_DRIPLEAF),

    SEAGRASS((player, place) -> {
        WrappedBlockState existing = place.getDirectionalState(BlockFace.DOWN);
        if (place.isInWater() && place.isFullFace(BlockFace.DOWN) && existing.getType() != StateTypes.MAGMA_BLOCK) {
            place.set(place.material);
        }
    }, ItemTypes.SEAGRASS),

    HANGING_ROOT((player, place) -> {
        if (place.isFullFace(BlockFace.UP)) {
            place.set(place.material);
        }
    }, ItemTypes.HANGING_ROOTS),

    SPORE_BLOSSOM((player, place) -> {
        if (place.isFullFace(BlockFace.UP) && !place.isInWater()) {
            place.set();
        }
    }, ItemTypes.SPORE_BLOSSOM),

    FIRE((player, place) -> {
        if (place.isInLiquid()) return;

        boolean byFlammable = false;
        for (BlockFace face : BlockFace.values()) {
            byFlammable = true;
        }

        if (byFlammable || place.isFullFace(BlockFace.DOWN)) {
            place.set(place.material);
        }
    }, ItemTypes.FLINT_AND_STEEL, ItemTypes.FIRE_CHARGE),

    TRIPWIRE_HOOK((player, place) -> {
        if (place.isFaceHorizontal() && place.isFullFace(place.getFace().getOppositeFace())) {
            place.set(place.material);
        }
    }, ItemTypes.TRIPWIRE_HOOK),

    CORAL_PLANT((player, place) -> {
        if (place.isFullFace(BlockFace.DOWN)) {
            place.set(place.material);
        }
    }, ItemTypes.values().stream().filter(mat -> (mat.getName().getKey().contains("coral")
                    && !mat.getName().getKey().contains("block") && !mat.getName().getKey().contains("fan")))
            .toArray(ItemType[]::new)),

    CORAL_FAN((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (face != BlockFace.UP) {
                boolean canPlace = place.isFullFace(face);
                if (BlockFaceHelper.isFaceHorizontal(face)) {
                    if (canPlace) {
                        WrappedBlockState coralFan = StateTypes.FIRE_CORAL_WALL_FAN.createBlockState(CompensatedWorld.blockVersion);
                        coralFan.setFacing(face);
                        place.set(coralFan);
                        return;
                    }
                } else if (place.isFaceFullCenter(BlockFace.DOWN) && canPlace) {
                    place.set(place.material);
                    return;
                }
            }
        }
    }, ItemTypes.values().stream().filter(mat -> (mat.getName().getKey().contains("coral")
                    && !mat.getName().getKey().contains("block") && mat.getName().getKey().contains("fan")))
            .toArray(ItemType[]::new)),

    PRESSURE_PLATE((player, place) -> {
        if (place.isFullFace(BlockFace.DOWN) || place.isFaceFullCenter(BlockFace.DOWN)) {
            place.set();
        }
    }, ItemTypes.values().stream().filter(mat -> (mat.getName().getKey().contains("plate")))
            .toArray(ItemType[]::new)),

    RAIL((player, place) -> {
        if (place.isFaceRigid(BlockFace.DOWN)) {
            place.set(place.material);
        }
    }, ItemTags.RAILS),

    KELP((player, place) -> {
        StateType below = place.getDirectionalState(BlockFace.DOWN).getType();
        WrappedBlockState existing = place.getExistingBlockData();

        double fluidLevel = 0;
        if (Materials.isWater(player.getClientVersion(), existing)) {
            if (existing.getType() == StateTypes.WATER) {
                int level = existing.getLevel();
                fluidLevel = ((level & 0x8) == 8) ? (8.0 / 9.0f) : (8 - level) / 9.0f;
            } else {
                fluidLevel = 1.0;
            }
        }

        if (below != StateTypes.MAGMA_BLOCK && (place.isFullFace(BlockFace.DOWN) || below == StateTypes.KELP || below == StateTypes.KELP_PLANT) && fluidLevel >= 8 / 9d) {
            place.set(place.material);
        }
    }, ItemTypes.KELP),

    CAVE_VINE((player, place) -> {
        StateType below = place.getDirectionalState(BlockFace.UP).getType();
        if (place.isFullFace(BlockFace.DOWN) || below == StateTypes.CAVE_VINES || below == StateTypes.CAVE_VINES_PLANT) {
            place.set(place.material);
        }
    }, ItemTypes.GLOW_BERRIES),

    WEEPING_VINE((player, place) -> {
        StateType below = place.getDirectionalState(BlockFace.UP).getType();
        if (place.isFullFace(BlockFace.UP) || below == StateTypes.WEEPING_VINES || below == StateTypes.WEEPING_VINES_PLANT) {
            place.set(place.material);
        }
    }, ItemTypes.WEEPING_VINES),

    TWISTED_VINE((player, place) -> {
        StateType below = place.getDirectionalState(BlockFace.DOWN).getType();
        if (place.isFullFace(BlockFace.DOWN) || below == StateTypes.TWISTING_VINES || below == StateTypes.TWISTING_VINES_PLANT) {
            place.set(place.material);
        }
    }, ItemTypes.TWISTING_VINES),

    VINE((player, place) -> {
        if (place.getAboveState().getType() == StateTypes.VINE) {
            place.set();
            return;
        }

        for (BlockFace face : BlockPlace.BY_2D) {
            if (place.isSolidBlocking(face)) {
                place.set();
                return;
            }
        }
    }, ItemTypes.VINE),

    LECTERN((player, place) -> {
        WrappedBlockState lectern = place.material.createBlockState(CompensatedWorld.blockVersion);
        lectern.setFacing(place.getPlayerFacing().getOppositeFace());
        place.set(lectern);
    }, ItemTypes.LECTERN),

    FENCE_GATE((player, place) -> {
        WrappedBlockState gate = place.material.createBlockState(CompensatedWorld.blockVersion);
        gate.setFacing(place.getPlayerFacing());

        if (place.isBlockPlacedPowered()) {
            gate.setOpen(true);
        }

        place.set(gate);
    }, BlockTags.FENCE_GATES),

    TRAPDOOR((player, place) -> {
        WrappedBlockState door = place.material.createBlockState(CompensatedWorld.blockVersion);

        BlockFace direction = place.getFace();
        if (!place.replaceClicked && BlockFaceHelper.isFaceHorizontal(direction)) {
            door.setFacing(direction);
            boolean clickedTop = place.getClickedLocation().getY() > 0.5;
            Half half = clickedTop ? Half.TOP : Half.BOTTOM;
            door.setHalf(half);
        } else if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            door.setFacing(place.getPlayerFacing().getOppositeFace());
            Half half = direction == BlockFace.UP ? Half.BOTTOM : Half.TOP;
            door.setHalf(half);
        }

        if (place.isBlockPlacedPowered()) {
            door.setOpen(true);
        }

        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
            WrappedBlockState dirState = place.getDirectionalState(door.getFacing().getOppositeFace());
            boolean fullFace = CollisionData.getData(dirState.getType()).getMovementCollisionBox(player, player.getClientVersion(), dirState).isFullBlock();
            boolean blacklisted = BlockTags.ICE.contains(dirState.getType()) || BlockTags.GLASS_BLOCKS.contains(dirState.getType()) ||
                    dirState.getType() == StateTypes.TNT || BlockTags.LEAVES.contains(dirState.getType()) ||
                    dirState.getType() == StateTypes.SNOW || dirState.getType() == StateTypes.CACTUS;
            boolean whitelisted = dirState.getType() == StateTypes.GLOWSTONE || BlockTags.SLABS.contains(dirState.getType()) ||
                    BlockTags.STAIRS.contains(dirState.getType());

            if (!((dirState.getType().isBlocking() && !blacklisted && fullFace) || whitelisted)) {
                return;
            }
        }

        place.set(door);
    }, ItemTags.TRAPDOORS),

    DOOR((player, place) -> {
        if (place.isFullFace(BlockFace.DOWN) && place.isBlockFaceOpen(BlockFace.UP)) {
            WrappedBlockState door = place.material.createBlockState(CompensatedWorld.blockVersion);
            door.setFacing(place.getPlayerFacing());

            BlockFace playerFacing = place.getPlayerFacing();

            BlockFace ccw = BlockFaceHelper.getCounterClockwise(playerFacing);
            WrappedBlockState ccwState = place.getDirectionalState(ccw);
            CollisionBox ccwBox = CollisionData.getData(ccwState.getType()).getMovementCollisionBox(player, player.getClientVersion(), ccwState);

            Vector3dm aboveCCWPos = place.getClickedLocation().add(new Vector3dm(ccw.getModX(), ccw.getModY(), ccw.getModZ())).add(new Vector3dm(0, 1, 0));
            WrappedBlockState aboveCCWState = player.compensatedWorld.getBlock(aboveCCWPos);
            CollisionBox aboveCCWBox = CollisionData.getData(aboveCCWState.getType()).getMovementCollisionBox(player, player.getClientVersion(), aboveCCWState);

            BlockFace cw = BlockFaceHelper.getPEClockWise(playerFacing);
            WrappedBlockState cwState = place.getDirectionalState(cw);
            CollisionBox cwBox = CollisionData.getData(cwState.getType()).getMovementCollisionBox(player, player.getClientVersion(), cwState);

            Vector3dm aboveCWPos = place.getClickedLocation().add(new Vector3dm(cw.getModX(), cw.getModY(), cw.getModZ())).add(new Vector3dm(0, 1, 0));
            WrappedBlockState aboveCWState = player.compensatedWorld.getBlock(aboveCWPos);
            CollisionBox aboveCWBox = CollisionData.getData(aboveCWState.getType()).getMovementCollisionBox(player, player.getClientVersion(), aboveCWState);

            int i = (ccwBox.isFullBlock() ? -1 : 0) + (aboveCCWBox.isFullBlock() ? -1 : 0) + (cwBox.isFullBlock() ? 1 : 0) + (aboveCWBox.isFullBlock() ? 1 : 0);

            boolean isCCWLower = false;
            if (BlockTags.DOORS.contains(ccwState.getType()))
                isCCWLower = ccwState.getHalf() == Half.LOWER;

            boolean isCWLower = false;
            if (BlockTags.DOORS.contains(cwState.getType()))
                isCWLower = ccwState.getHalf() == Half.LOWER;

            Hinge hinge;
            if ((!isCCWLower || isCWLower) && i <= 0) {
                if ((!isCWLower || isCCWLower) && i >= 0) {
                    int j = playerFacing.getModX();
                    int k = playerFacing.getModZ();
                    Vector3dm vec3 = place.getClickedLocation();
                    double d0 = vec3.getX();
                    double d1 = vec3.getY();
                    hinge = (j >= 0 || d1 >= 0.5D) && (j <= 0 || d1 <= 0.5D) && (k >= 0 || d0 <= 0.5D) && (k <= 0 || d0 >= 0.5D) ? Hinge.LEFT : Hinge.RIGHT;
                } else {
                    hinge = Hinge.LEFT;
                }
            } else {
                hinge = Hinge.RIGHT;
            }

            if (place.isBlockPlacedPowered()) {
                door.setOpen(true);
            }

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                door.setHinge(hinge);
            }

            door.setHalf(Half.LOWER);
            place.set(door);

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                door.setHalf(Half.UPPER);
                place.setAbove(door);
            } else {
                WrappedBlockState above = WrappedBlockState.getByString(CompensatedWorld.blockVersion, "minecraft:" + place.material.getName().toLowerCase(Locale.ROOT) + "[half=upper,hinge=" + hinge.toString().toLowerCase(Locale.ROOT) + "]");
                place.setAbove(above);
            }
        }
    }, ItemTags.DOORS),

    SCAFFOLDING((player, place) -> {
        place.replaceClicked = false;

        if (place.getPlacedAgainstMaterial() == StateTypes.SCAFFOLDING) {
            BlockFace direction;
            if (place.isSecondaryUse()) {
                direction = place.isInside ? place.getFace().getOppositeFace() : place.getFace();
            } else {
                direction = place.getFace() == BlockFace.UP ? place.getPlayerFacing() : BlockFace.UP;
            }

            place.setFace(direction);

            int i = 0;

            Vector3i starting = new Vector3i(place.position.x + direction.getModX(), place.position.y + direction.getModY(), place.position.z + direction.getModZ());
            while (i < 7) {
                if (player.compensatedWorld.getBlock(starting).getType() != StateTypes.SCAFFOLDING) {
                    if (player.compensatedWorld.getBlock(starting).getType().isReplaceable()) {
                        place.position = starting;
                        place.replaceClicked = true;
                        break;
                    }
                    return;
                }

                starting = new Vector3i(starting.x + direction.getModX(), starting.y + direction.getModY(), starting.z + direction.getModZ());
                if (BlockFaceHelper.isFaceHorizontal(direction)) {
                    i++;
                }
            }
            if (i == 7) return;
        }

        boolean sturdyBelow = place.isFullFace(BlockFace.DOWN);
        boolean isBelowScaffolding = place.getBelowMaterial() == StateTypes.SCAFFOLDING;
        boolean isBottom = !sturdyBelow && !isBelowScaffolding;

        WrappedBlockState scaffolding = StateTypes.SCAFFOLDING.createBlockState(CompensatedWorld.blockVersion);
        scaffolding.setBottom(isBottom);

        place.set(scaffolding);
    }, ItemTypes.SCAFFOLDING),

    DOUBLE_PLANT((player, place) -> {
        if (place.isBlockFaceOpen(BlockFace.UP) && place.isOnDirt() || place.isOn(StateTypes.FARMLAND)) {
            place.set();
            place.setAbove();
        }
    }, ItemTypes.TALL_GRASS, ItemTypes.LARGE_FERN, ItemTypes.SUNFLOWER,
            ItemTypes.LILAC, ItemTypes.ROSE_BUSH, ItemTypes.PEONY),

    MUSHROOM((player, place) -> {
        if (BlockTags.MUSHROOM_GROW_BLOCK.contains(place.getBelowMaterial())) {
            place.set();
        } else if (place.isFullFace(BlockFace.DOWN)) {
            Vector3i placedPos = place.getPlacedBlockPos();
            place.set();
        }
    }, ItemTypes.BROWN_MUSHROOM, ItemTypes.RED_MUSHROOM),

    MANGROVE_PROPAGULE((player, place) -> {
        if (place.getAboveState().getType() != StateTypes.MANGROVE_LEAVES) return;
        if (place.isOnDirt() || place.isOn(StateTypes.FARMLAND)) {
            place.set();
        }
    }, ItemTypes.MANGROVE_PROPAGULE),

    FROGSPAWN((player, place) -> {
        if (Materials.isWater(player.getClientVersion(), place.getExistingBlockData()) && Materials.isWater(player.getClientVersion(), place.getAboveState())) {
            place.set();
        }
    }, ItemTypes.FROGSPAWN),

    BUSH_BLOCK_TYPE((player, place) -> {
        if (place.isOnDirt() || place.isOn(StateTypes.FARMLAND)) {
            place.set();
        }
    }, ItemTypes.SPRUCE_SAPLING, ItemTypes.ACACIA_SAPLING,
            ItemTypes.BIRCH_SAPLING, ItemTypes.DARK_OAK_SAPLING,
            ItemTypes.OAK_SAPLING, ItemTypes.JUNGLE_SAPLING,
            ItemTypes.SWEET_BERRIES, ItemTypes.DANDELION,
            ItemTypes.POPPY, ItemTypes.BLUE_ORCHID,
            ItemTypes.ALLIUM, ItemTypes.AZURE_BLUET,
            ItemTypes.RED_TULIP, ItemTypes.ORANGE_TULIP,
            ItemTypes.WHITE_TULIP, ItemTypes.PINK_TULIP,
            ItemTypes.OXEYE_DAISY, ItemTypes.CORNFLOWER,
            ItemTypes.LILY_OF_THE_VALLEY, ItemTypes.PINK_PETALS,
            ItemTypes.SHORT_GRASS),

    POWDER_SNOW_BUCKET((player, place) -> {
        place.set();
        CheckManagerListener.setPlayerItem(player, place.hand, ItemTypes.BUCKET);
    }, ItemTypes.POWDER_SNOW_BUCKET),

    GAME_MASTER((player, place) -> {
        if (player.canPlaceGameMasterBlocks()) {
            place.set();
        }
    }, ItemTypes.COMMAND_BLOCK, ItemTypes.CHAIN_COMMAND_BLOCK, ItemTypes.REPEATING_COMMAND_BLOCK,
            ItemTypes.JIGSAW, ItemTypes.STRUCTURE_BLOCK),

    NO_DATA((player, place) -> place.set(place.material), ItemTypes.AIR);

    private static final Map<ItemType, BlockPlaceResult> lookupMap = new HashMap<>();

    static {
        for (BlockPlaceResult data : values()) {
            for (ItemType type : data.materials) {
                lookupMap.put(type, data);
            }
        }
    }

    private final BlockPlaceFactory data;
    private final ItemType[] materials;

    BlockPlaceResult(BlockPlaceFactory data, ItemType... materials) {
        this.data = data;
        Set<ItemType> mList = new HashSet<>(Arrays.asList(materials));
        mList.remove(null);
        this.materials = mList.toArray(new ItemType[0]);
    }

    BlockPlaceResult(BlockPlaceFactory data, ItemTags tags) {
        this(data, tags.getStates().toArray(new ItemType[0]));
    }

    BlockPlaceResult(BlockPlaceFactory data, BlockTags tag) {
        List<ItemType> types = new ArrayList<>(tag.getStates().size());
        for (StateType state : tag.getStates()) {
            types.add(ItemTypes.getTypePlacingState(state));
        }

        this.data = data;
        this.materials = types.toArray(new ItemType[0]);
    }

    public static BlockPlaceFactory getMaterialData(ItemType placed) {
        return lookupMap.getOrDefault(placed, NO_DATA).data;
    }
}
