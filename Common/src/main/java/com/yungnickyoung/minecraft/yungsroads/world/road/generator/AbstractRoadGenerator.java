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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public abstract class AbstractRoadGenerator {
    /**
     * The furthest horizontal distance (along either axis) from a road center position that placement may modify.
     */
    public static final int PLACEMENT_REACH = 2;

    /**
     * How far, in blocks, a bridge extends past the last center that crosses the hole or dip it spans. Covers the
     * placement reach, so a road crossing a rim at an angle is bridged across its whole width.
     */
    public static final int BRIDGE_MARGIN = 3;

    /**
     * The furthest horizontal distance (along either axis) from a chunk at which a road center position can affect
     * what's placed in it.
     */
    public static final int PLACEMENT_LOOKUP_REACH = PLACEMENT_REACH + BRIDGE_MARGIN;

    /** How far, in blocks, edge roughness may move a bridge's edge in or out, at the maximum setting. */
    private static final double MAX_EDGE_ROUGHNESS = 1.0;

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

    /** Varies the edges of bridges. Fine enough to make them ragged, so roughness reads as wear rather than waves. */
    private final FastNoise edgeNoise;

    public AbstractRoadGenerator() {
        this.noise = new FastNoise();
        this.noise.SetNoiseType(FastNoise.NoiseType.Simplex);
        this.noise.SetFrequency(.012f);
        this.noise.SetFractalOctaves(1);

        this.edgeNoise = new FastNoise();
        this.edgeNoise.SetNoiseType(FastNoise.NoiseType.Simplex);
        this.edgeNoise.SetFrequency(.3f);
        this.edgeNoise.SetFractalOctaves(1);
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
     * Places road blocks around each of the given road center positions, at the heights stored in their y.
     * Only blocks inside the given chunk are modified, so the result doesn't depend on chunk generation order.
     * Each column is placed once, at the height of its nearest center, so the road is level across its width.
     * <p>
     * Where the road crosses a hole or dip, it's carried on a bridge whose deck spans every gap beneath it. Whether a
     * center is on a bridge is decided from the centers around it, rather than from the ground across the road's width,
     * so a whole stretch of road bridges together, while steep drops beside a road resting on the ground are left alone.
     *
     * @param centers The road center positions within {@link #PLACEMENT_LOOKUP_REACH} blocks of the chunk.
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
        boolean[] isGroundRoad = new boolean[16 * 16];
        int[] nearestDistSq = new int[16 * 16];
        BlockPos[] nearestCenters = new BlockPos[16 * 16];
        Arrays.fill(nearestDistSq, Integer.MAX_VALUE);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (BlockPos center : centers) {
            double widthNoise = widthNoise(center);

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

                    // Distances are kept as squared values as an optimization
                    int distSq = dx * dx + dz * dz;
                    if (distSq < maxRoadDistSq(roadTypes[column], widthNoise)) {
                        isGroundRoad[column] = true;
                    }
                    if (distSq < nearestDistSq[column]) {
                        nearestDistSq[column] = distSq;
                        nearestCenters[column] = center;
                    }
                }
            }
        }

        // Decided before placing anything, since placing changes the ground the decision reads
        ConfigModule.Advanced settings = YungsRoadsCommon.CONFIG.advanced;
        Set<BlockPos> bridgeCenters = findBridgeCenters(writer, centers, isLandBridge, config, settings);

        long worldSeed = writer.level().getSeed();
        for (int column = 0; column < 16 * 16; column++) {
            BlockPos center = nearestCenters[column];
            if (center == null) {
                continue;
            }
            int x = chunkPos.getMinBlockX() + (column >> 4);
            int z = chunkPos.getMinBlockZ() + (column & 15);
            mutable.set(x, groundHeights[column], z);

            if (bridgeCenters.contains(center)) {
                // A bridge's edge is measured from its center line alone, so it doesn't trace the ground far below
                double dist = Math.sqrt(nearestDistSq[column]);
                double halfWidth = bridgeHalfWidth(center, x, z, config, settings);
                if (dist < halfWidth) {
                    // Decay is likelier toward the edges, and never breaks the center line
                    double decayChance = settings.landBridgeDecay * dist / halfWidth;
                    boolean decayed = decayChance > 0 && decayRoll(worldSeed, x, center.getY(), z) < decayChance;
                    placeBridgeColumn(writer, random, mutable, center.getY(), decayed, roadTypes[column], config, settings);
                }
            } else if (isGroundRoad[column]) {
                placeGroundColumn(writer, random, mutable, center.getY(), roadTypes[column], config, settings);
            }
        }
    }

    /**
     * Subtly varies the road's width along its length to make its shape more interesting. Ranges from 0 to 2.
     */
    private double widthNoise(BlockPos center) {
        return this.noise.GetNoise(center.getX(), center.getZ()) + 1;
    }

    /**
     * The squared distance from a center within which a road of the given type is placed, exclusive.
     */
    private static double maxRoadDistSq(RoadTypeConfig roadType, double widthNoise) {
        return roadType.roadSizeRadius * roadType.roadSizeRadius + widthNoise * roadType.roadSizeVariation;
    }

    /**
     * The distance from a bridge center within which its deck is placed, exclusive. The deck is as wide as the widest
     * road type would be there, so it's never narrower than the road leading onto it, and its width doesn't depend on
     * what's at the bottom of the hole. Edge roughness then moves the edge in or out per column.
     */
    private double bridgeHalfWidth(BlockPos center, int x, int z, RoadFeatureConfiguration config,
                                   ConfigModule.Advanced settings) {
        double widthNoise = widthNoise(center);
        double maxDistSq = config.roadTypes.isEmpty() ? maxRoadDistSq(DEFAULT_SETTINGS, widthNoise) : 0;
        for (RoadTypeConfig roadType : config.roadTypes) {
            maxDistSq = Math.max(maxDistSq, maxRoadDistSq(roadType, widthNoise));
        }
        return Math.sqrt(maxDistSq) + settings.landBridgeEdgeRoughness * MAX_EDGE_ROUGHNESS * this.edgeNoise.GetNoise(x, z);
    }

    /**
     * A uniform random number in [0, 1) for decaying the bridge block at the given position. It depends only on the
     * world seed and the position, so raising the decay only removes more blocks, and the result is the same whichever
     * chunk generates first.
     */
    private static double decayRoll(long worldSeed, int x, int y, int z) {
        return new XoroshiroRandomSource(worldSeed ^ Mth.getSeed(x, y, z)).nextDouble();
    }

    /**
     * Finds the centers whose road is carried on a bridge: those within {@link #BRIDGE_MARGIN} blocks of a center that
     * crosses a hole or is part of a land bridge. The margin carries the bridge past each rim, so where the road meets a
     * rim at an angle, the side of the road that's already over the hole is bridged too.
     */
    private static Set<BlockPos> findBridgeCenters(RoadBlockWriter writer, List<BlockPos> centers,
                                                   Predicate<BlockPos> isLandBridge, RoadFeatureConfiguration config,
                                                   ConfigModule.Advanced settings) {
        List<BlockPos> crossings = centers.stream()
                .filter(center -> isLandBridge.test(center) || crossesHole(writer, center, config, settings))
                .toList();
        Set<BlockPos> bridgeCenters = new HashSet<>();
        for (BlockPos center : centers) {
            for (BlockPos crossing : crossings) {
                int dx = crossing.getX() - center.getX();
                int dz = crossing.getZ() - center.getZ();
                if (dx * dx + dz * dz <= BRIDGE_MARGIN * BRIDGE_MARGIN) {
                    bridgeCenters.add(center);
                    break;
                }
            }
        }
        return bridgeCenters;
    }

    /**
     * Whether the road crosses a hole in the ground at the given center, such as a ravine, that routing didn't know
     * about. Over water, that's only where the road is above the water's surface.
     * <p>
     * The center may be in a neighboring chunk that has already placed its roads, so a placed bridge counts too. That
     * keeps the result the same whichever chunk generates first. A bridge's center line is never decayed, so its block
     * is always there to find.
     */
    private static boolean crossesHole(RoadBlockWriter writer, BlockPos center, RoadFeatureConfiguration config,
                                       ConfigModule.Advanced settings) {
        if (!writer.level().hasChunk(center.getX() >> 4, center.getZ() >> 4)) {
            // Unknown, which only happens next to unloaded chunks in a live world. Bridge any gaps to be safe.
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
     * Places a road resting on the ground in one column at the given height, shaping the ground to meet it. Gaps below
     * the road up to the max fill depth are filled, and deeper drops left alone, so on steep hillsides the road narrows
     * to a ledge rather than sprouting stray bridge blocks. Over water, the road is always a bridge.
     *
     * @param ground The column's ground block, which may be water.
     */
    private static void placeGroundColumn(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight,
                                          RoadTypeConfig roadType, RoadFeatureConfiguration config,
                                          ConfigModule.Advanced settings) {
        if (!writer.getBlockState(ground).getFluidState().isEmpty()) {
            placeWaterBridge(writer, random, ground, roadHeight, config);
        } else if (roadHeight - ground.getY() - 1 <= settings.maxFillDepth) {
            placeOnGround(writer, random, ground, roadHeight, roadType, settings);
        }
    }

    /**
     * Places one column of a bridge's deck at the given height: a bridge block over any gap beneath it, or the road
     * itself where the ground reaches the deck.
     *
     * @param ground The column's ground block, which may be water.
     * @param decayed Whether the deck is missing here. Only applies over land, so water bridges stay whole.
     */
    private static void placeBridgeColumn(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight,
                                          boolean decayed, RoadTypeConfig roadType, RoadFeatureConfiguration config,
                                          ConfigModule.Advanced settings) {
        if (!writer.getBlockState(ground).getFluidState().isEmpty()) {
            placeWaterBridge(writer, random, ground, roadHeight, config);
        } else if (roadHeight - ground.getY() - 1 > 0) {
            if (!decayed) {
                writer.setBlock(ground.atY(roadHeight), config.bridgeBlockStates.get(random));
            }
        } else {
            placeOnGround(writer, random, ground, roadHeight, roadType, settings);
        }
    }

    /**
     * Places a bridge block over water. A bridge never dips below the water's surface.
     */
    private static void placeWaterBridge(RoadBlockWriter writer, RandomSource random, BlockPos water, int roadHeight,
                                         RoadFeatureConfiguration config) {
        writer.setBlock(water.atY(Math.max(roadHeight, water.getY())), config.bridgeBlockStates.get(random));
    }

    /**
     * Places the road on solid ground at the given height. Ground above the road is cut away, up to the max cut depth,
     * beyond which the road rises with the ground instead. Any gap below the road is filled.
     */
    private static void placeOnGround(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight,
                                      RoadTypeConfig roadType, ConfigModule.Advanced settings) {
        BlockState groundState = writer.getBlockState(ground);
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
