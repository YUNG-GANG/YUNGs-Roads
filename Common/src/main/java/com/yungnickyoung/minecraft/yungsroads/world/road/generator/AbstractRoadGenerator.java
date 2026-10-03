package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypeConfig;
import com.yungnickyoung.minecraft.yungsroads.world.config.TempEnum;
import com.yungnickyoung.minecraft.yungsroads.world.feature.RoadFeature;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.RoadBlockWriter;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class AbstractRoadGenerator {
    /**
     * The furthest horizontal distance (along either axis) from a road center position that placement may modify.
     */
    public static final int PLACEMENT_REACH = 2;

    private static final RoadTypeConfig DEFAULT_SETTINGS = new RoadTypeConfig(
            List.of(Blocks.DIRT.defaultBlockState(),
                    Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.PODZOL.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState()),
            TempEnum.ANY,
            new BlockStateRandomizer(Blocks.DIRT_PATH.defaultBlockState())
                    .addBlock(Blocks.GRASS_BLOCK.defaultBlockState(), 0.05f),
            1.5f,
            2.0f,
            List.of());

    private final FastNoise noise;

    public AbstractRoadGenerator() {
        this.noise = new FastNoise();
        this.noise.SetNoiseType(FastNoise.NoiseType.Simplex);
        this.noise.SetFrequency(.012f);
        this.noise.SetFractalOctaves(1);
    }

    /**
     * Attempts to generate a {@link Road} connecting two positions.<br />
     * Note that this simply constructs the {@link Road} object. Blocks are not actually placed until
     * {@link RoadFeature#place(FeaturePlaceContext)}.
     *
     * @param terrain Terrain samples shared by all roads generated for the same region.
     * @return Road connecting the two positions, if one was successfully generated.
     */
    public abstract Optional<Road> generateRoad(BlockPos pos1, BlockPos pos2, TerrainCache terrain);

    /**
     * Places debug markers for the given {@link Road}, as enabled in the debug config.
     * Only blocks within the given chunk are modified.
     */
    public abstract void placeDebugMarkers(Road road, RoadBlockWriter writer, ChunkPos chunkPos);

    /**
     * How a road is held up at a center position, which decides what's placed over gaps in the ground beneath it.
     */
    private enum Footing {
        /** Resting on the ground. Gaps up to the max fill depth are filled, and deeper drops beside it left alone. */
        GROUND,
        /**
         * Crossing a hole found while placing, such as a ravine. Gaps up to the max fill depth are filled, and deeper
         * ones bridged.
         */
        HOLE,
        /** Crossing a dip that routing chose to bridge. Every gap is bridged, so the deck is unbroken from rim to rim. */
        LAND_BRIDGE
    }

    /**
     * Places road blocks around each of the given road center positions, at the heights stored in their y.
     * Only blocks inside the given chunk are modified, so the result doesn't depend on chunk generation order.
     * Each column is placed once, at the height of its nearest center, so the road is level across its width.
     *
     * @param isLandBridge Whether a center position is part of a land bridge. See {@link Road#landBridges}.
     */
    public void placeRoadInChunk(RoadBlockWriter writer, RandomSource random, ChunkPos chunkPos, List<BlockPos> centers,
                                 Predicate<BlockPos> isLandBridge, RoadFeatureConfiguration config) {
        if (YungsRoadsCommon.CONFIG.debug.placeDebugPaths) {
            for (BlockPos center : centers) {
                placeDebugBlock(writer, chunkPos, center, Blocks.DIAMOND_BLOCK.defaultBlockState());
            }
            return;
        }

        // Per column of the chunk, indexed by (x & 15) << 4 | (z & 15). A column's ground and road type are found
        // when a center first reaches it.
        int[] groundHeights = new int[16 * 16];
        RoadTypeConfig[] roadTypes = new RoadTypeConfig[16 * 16];
        boolean[] isRoad = new boolean[16 * 16];
        int[] nearestDistSq = new int[16 * 16];
        BlockPos[] nearestCenters = new BlockPos[16 * 16];
        Arrays.fill(nearestDistSq, Integer.MAX_VALUE);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (BlockPos center : centers) {
            // Subtly vary the road's width along its length to make its shape more interesting
            double widthNoise = this.noise.GetNoise(center.getX(), center.getZ()) + 1;

            for (int dx = -PLACEMENT_REACH; dx <= PLACEMENT_REACH; dx++) {
                for (int dz = -PLACEMENT_REACH; dz <= PLACEMENT_REACH; dz++) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    if (!isInChunk(chunkPos, x, z)) {
                        continue;
                    }

                    int column = (x & 15) << 4 | (z & 15);
                    if (roadTypes[column] == null) {
                        groundHeights[column] = writer.surfaceHeight(x, z);
                        roadTypes[column] = getRoadTypeAt(writer, mutable.set(x, groundHeights[column], z), config);
                    }
                    RoadTypeConfig roadType = roadTypes[column];

                    // Distances are kept as squared values as an optimization
                    int distSq = dx * dx + dz * dz;
                    double maxRoadDistSq = roadType.roadSizeRadius * roadType.roadSizeRadius + widthNoise * roadType.roadSizeVariation;
                    if (distSq < maxRoadDistSq) {
                        isRoad[column] = true;
                    }
                    if (distSq < nearestDistSq[column]) {
                        nearestDistSq[column] = distSq;
                        nearestCenters[column] = center;
                    }
                }
            }
        }

        // Decided per center rather than per column, so a road's whole width bridges a hole together. Decided before
        // placing anything, since placing changes the ground the decision reads.
        ConfigModule.Advanced settings = YungsRoadsCommon.CONFIG.advanced;
        Map<BlockPos, Footing> footings = new HashMap<>();
        for (int column = 0; column < 16 * 16; column++) {
            if (isRoad[column]) {
                footings.computeIfAbsent(nearestCenters[column], center -> isLandBridge.test(center)
                        ? Footing.LAND_BRIDGE
                        : crossesHole(writer, center, config, settings) ? Footing.HOLE : Footing.GROUND);
            }
        }

        for (int column = 0; column < 16 * 16; column++) {
            if (isRoad[column]) {
                BlockPos center = nearestCenters[column];
                mutable.set(chunkPos.getMinBlockX() + (column >> 4), groundHeights[column], chunkPos.getMinBlockZ() + (column & 15));
                placeColumn(writer, random, mutable, center.getY(), footings.get(center), roadTypes[column], config, settings);
            }
        }
    }

    /**
     * Whether the road crosses a hole in the ground at the given center, such as a ravine, that routing didn't know
     * about. Over water, that's only where the road is above the water's surface.
     * <p>
     * The center may be in a neighboring chunk that has already placed its roads, so a placed bridge counts too. That
     * keeps the result the same whichever chunk generates first.
     */
    private static boolean crossesHole(RoadBlockWriter writer, BlockPos center, RoadFeatureConfiguration config,
                                       ConfigModule.Advanced settings) {
        if (!writer.level().hasChunk(center.getX() >> 4, center.getZ() >> 4)) {
            // Unknown, which only happens next to unloaded chunks in a live world. Let each column decide for itself.
            return true;
        }

        BlockState atRoad = writer.getBlockState(center);
        if (isBridgeBlock(atRoad, config)) {
            // Already placed. A bridge resting on the water's surface doesn't cross a hole.
            return writer.getBlockState(center.below()).getFluidState().isEmpty();
        }

        int groundHeight = writer.surfaceHeight(center.getX(), center.getZ());
        int gap = center.getY() - groundHeight - 1;
        boolean overWater = !writer.getBlockState(center.atY(groundHeight)).getFluidState().isEmpty();
        return overWater ? gap > 0 : gap > settings.maxFillDepth;
    }

    private static boolean isBridgeBlock(BlockState state, RoadFeatureConfiguration config) {
        return state == config.bridgeBlockStates.getDefaultBlockState() || config.bridgeBlockStates.getEntriesAsMap().containsKey(state);
    }

    /**
     * Determines the road type for the given surface block, based on the block and its biome's temperature.
     */
    private RoadTypeConfig getRoadTypeAt(RoadBlockWriter writer, BlockPos surfacePos, RoadFeatureConfiguration config) {
        for (RoadTypeConfig roadType : config.roadTypes) {
            if (roadType.matches(writer.level(), surfacePos)) {
                return roadType;
            }
        }
        return DEFAULT_SETTINGS;
    }

    /**
     * Places the road in one column at the given height, shaping the ground to meet it. Ground above the road is cut
     * away, up to the max cut depth, beyond which the road rises with the ground instead. Gaps below the road are
     * filled or bridged, depending on how the road is held up at the column's nearest center. Over water, the road is
     * always a bridge.
     *
     * @param ground The column's ground block, which may be water.
     */
    private void placeColumn(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight, Footing footing,
                             RoadTypeConfig roadType, RoadFeatureConfiguration config, ConfigModule.Advanced settings) {
        BlockState groundState = writer.getBlockState(ground);
        if (!groundState.getFluidState().isEmpty()) {
            // A bridge never dips below the water's surface
            writer.setBlock(ground.atY(Math.max(roadHeight, ground.getY())), config.bridgeBlockStates.get(random));
            return;
        }

        int gap = roadHeight - ground.getY() - 1;
        int maxFillDepth = footing == Footing.LAND_BRIDGE ? 0 : settings.maxFillDepth;
        if (gap > maxFillDepth) {
            // Beside a road resting on the ground, such a drop is left alone, so on steep hillsides the road narrows to
            // a ledge rather than sprouting stray bridge blocks
            if (footing != Footing.GROUND) {
                writer.setBlock(ground.atY(roadHeight), config.bridgeBlockStates.get(random));
            }
            return;
        }

        writer.clearVegetationAbove(ground);
        int y = Math.max(roadHeight, ground.getY() - settings.maxCutDepth);
        for (int cutY = ground.getY(); cutY > y; cutY--) {
            writer.setBlock(ground.atY(cutY), Blocks.AIR.defaultBlockState());
        }
        BlockState fillState = fillState(writer, ground, groundState);
        for (int fillY = ground.getY() + 1; fillY < y; fillY++) {
            writer.setBlock(ground.atY(fillY), fillState);
        }
        writer.setBlock(ground.atY(y), roadType.pathBlockStates.get(random));
    }

    /**
     * The block to fill gaps under a road with: the ground's own block, so a raised road's sides blend in with the
     * terrain, or dirt if the ground isn't a plain full block. Properties like snow cover are reset, since the fill is
     * covered by the road.
     */
    private static BlockState fillState(RoadBlockWriter writer, BlockPos ground, BlockState groundState) {
        return groundState.isSolidRender(writer.level(), ground) && !groundState.hasBlockEntity()
                ? groundState.getBlock().defaultBlockState()
                : Blocks.DIRT.defaultBlockState();
    }

    /**
     * Places a single debug block at the surface, if the position is inside the given chunk.
     */
    void placeDebugBlock(RoadBlockWriter writer, ChunkPos chunkPos, BlockPos pos, BlockState blockState) {
        if (!isInChunk(chunkPos, pos)) {
            return;
        }
        writer.setBlock(new BlockPos(pos.getX(), writer.surfaceHeight(pos.getX(), pos.getZ()), pos.getZ()), blockState);
    }

    /**
     * Places a 10-block tall debug marker tower above the surface, if the position is inside the given chunk.
     */
    void placeDebugMarker(RoadBlockWriter writer, ChunkPos chunkPos, BlockPos blockPos, BlockState markerBlock) {
        if (!isInChunk(chunkPos, blockPos)) {
            return;
        }

        BlockPos.MutableBlockPos mutable = blockPos.mutable();
        mutable.setY(writer.surfaceHeight(mutable.getX(), mutable.getZ()));

        for (int y = 0; y < 10; y++) {
            mutable.move(Direction.UP);
            if (writer.getBlockState(mutable).isAir()) {
                writer.setBlock(mutable, markerBlock);
            }
        }
    }

    static boolean isInChunk(ChunkPos chunkPos, BlockPos blockPos) {
        return isInChunk(chunkPos, blockPos.getX(), blockPos.getZ());
    }

    static boolean isInChunk(ChunkPos chunkPos, int x, int z) {
        return (x >> 4) == chunkPos.x && (z >> 4) == chunkPos.z;
    }
}
