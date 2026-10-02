package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypeConfig;
import com.yungnickyoung.minecraft.yungsroads.world.config.TempEnum;
import com.yungnickyoung.minecraft.yungsroads.world.feature.RoadFeature;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Optional;

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
    public abstract void placeDebugMarkers(Road road, WorldGenLevel level, ChunkPos chunkPos);

    /**
     * Places road blocks around each of the given road center positions.
     * Only blocks inside the given chunk are modified, so the result doesn't depend on chunk generation order.
     * Each column is placed at most once, so overlapping road circles don't re-roll an already placed block.
     */
    public void placeRoadInChunk(WorldGenLevel level, RandomSource random, ChunkPos chunkPos, List<BlockPos> centers,
                                 RoadFeatureConfiguration config) {
        boolean[] placedColumns = new boolean[16 * 16];
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (BlockPos center : centers) {
            if (YungsRoadsCommon.CONFIG.debug.placeDebugPaths) {
                placeDebugBlock(level, chunkPos, center, Blocks.DIAMOND_BLOCK.defaultBlockState());
                continue;
            }

            // Subtly vary the road's width along its length to make its shape more interesting
            double widthNoise = this.noise.GetNoise(center.getX(), center.getZ()) + 1;

            for (int dx = -PLACEMENT_REACH; dx <= PLACEMENT_REACH; dx++) {
                for (int dz = -PLACEMENT_REACH; dz <= PLACEMENT_REACH; dz++) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    if (!isInChunk(chunkPos, x, z)) {
                        continue;
                    }

                    int columnIndex = (x & 15) << 4 | (z & 15);
                    if (placedColumns[columnIndex]) {
                        continue;
                    }

                    mutable.set(x, getSurfaceHeight(level, x, z), z);
                    RoadTypeConfig roadType = getRoadTypeAt(level, mutable, config);

                    // Distances are kept as squared values as an optimization
                    double maxRoadDistSq = roadType.roadSizeRadius * roadType.roadSizeRadius + widthNoise * roadType.roadSizeVariation;
                    if (dx * dx + dz * dz >= maxRoadDistSq) {
                        continue;
                    }

                    placePathBlock(level, random, mutable, roadType, config);
                    placedColumns[columnIndex] = true;
                }
            }
        }
    }

    /**
     * Determines the road type for the given surface block, based on the block and its biome's temperature.
     */
    private RoadTypeConfig getRoadTypeAt(WorldGenLevel level, BlockPos surfacePos, RoadFeatureConfiguration config) {
        for (RoadTypeConfig roadType : config.roadTypes) {
            if (roadType.matches(level, surfacePos)) {
                return roadType;
            }
        }
        return DEFAULT_SETTINGS;
    }

    /**
     * Places a single road block, using a bridge block if the position holds fluid.
     */
    private void placePathBlock(WorldGenLevel level, RandomSource random, BlockPos pos, RoadTypeConfig roadType,
                                RoadFeatureConfiguration config) {
        BlockState currState = level.getBlockState(pos);
        BlockState newState = currState.getFluidState().is(Fluids.EMPTY)
                ? roadType.pathBlockStates.get(random)
                : config.bridgeBlockStates.get(random);
        level.setBlock(pos, newState, 2);
    }

    /**
     * Places a single debug block at the surface, if the position is inside the given chunk.
     */
    void placeDebugBlock(WorldGenLevel level, ChunkPos chunkPos, BlockPos pos, BlockState blockState) {
        if (!isInChunk(chunkPos, pos)) {
            return;
        }
        level.setBlock(new BlockPos(pos.getX(), getSurfaceHeight(level, pos.getX(), pos.getZ()), pos.getZ()), blockState, 2);
    }

    /**
     * Places a 10-block tall debug marker tower above the surface, if the position is inside the given chunk.
     */
    void placeDebugMarker(WorldGenLevel level, ChunkPos chunkPos, BlockPos blockPos, BlockState markerBlock) {
        if (!isInChunk(chunkPos, blockPos)) {
            return;
        }

        BlockPos.MutableBlockPos mutable = blockPos.mutable();
        mutable.setY(getSurfaceHeight(level, mutable.getX(), mutable.getZ()));

        for (int y = 0; y < 10; y++) {
            mutable.move(Direction.UP);
            if (level.getBlockState(mutable).isAir()) {
                level.setBlock(mutable, markerBlock, 2);
            }
        }
    }

    static boolean isInChunk(ChunkPos chunkPos, BlockPos blockPos) {
        return isInChunk(chunkPos, blockPos.getX(), blockPos.getZ());
    }

    static boolean isInChunk(ChunkPos chunkPos, int x, int z) {
        return (x >> 4) == chunkPos.x && (z >> 4) == chunkPos.z;
    }

    static int getSurfaceHeight(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
    }
}
