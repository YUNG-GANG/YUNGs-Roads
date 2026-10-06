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
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import javax.annotation.Nullable;
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
     * How far, in blocks, past a chunk's edges the shape of bridge decks is worked out to decide its railings. A chunk
     * needs the railings one block past its edges, which its own railings connect to, and those need the deck's shape
     * one block further out.
     */
    private static final int RAILING_REACH = 2;

    /** Mixed into the world seed when rolling for the railing chance, so the roll is independent of decay's. */
    private static final long RAILING_CHANCE_SALT = 0x5DEECE66DL;

    /**
     * The furthest horizontal distance (along either axis) from a chunk at which a road center position can affect
     * what's placed in it.
     */
    public static final int PLACEMENT_LOOKUP_REACH = PLACEMENT_REACH + BRIDGE_MARGIN + RAILING_REACH;

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
            Optional.of(new BlockStateRandomizer(Blocks.DIRT.defaultBlockState())),
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
        NearestCenters nearest = new NearestCenters(chunkPos);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (BlockPos center : centers) {
            double widthNoise = widthNoise(center);

            for (int dx = -PLACEMENT_REACH; dx <= PLACEMENT_REACH; dx++) {
                for (int dz = -PLACEMENT_REACH; dz <= PLACEMENT_REACH; dz++) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    // Distances are kept as squared values as an optimization
                    int distSq = dx * dx + dz * dz;
                    nearest.offer(x, z, center, distSq);
                    if (!isInChunk(chunkPos, x, z)) {
                        continue;
                    }

                    int column = (x & 15) << 4 | (z & 15);
                    if (roadTypes[column] == null) {
                        groundHeights[column] = writer.surfaceHeight(x, z);
                        roadTypes[column] = getRoadTypeAt(writer, mutable.set(x, groundHeights[column], z), config);
                    }
                    if (distSq < maxRoadDistSq(roadTypes[column], widthNoise)) {
                        isGroundRoad[column] = true;
                    }
                }
            }
        }

        // Decided before placing anything, since placing changes the ground the decisions read
        ConfigModule.Advanced settings = YungsRoadsCommon.CONFIG.advanced;
        Set<BlockPos> bridgeCenters = findBridgeCenters(writer, centers, isLandBridge, config, settings);
        BridgeDecks decks = new BridgeDecks(writer, nearest, bridgeCenters, config, settings);
        boolean[] hasRailing = config.bridgeRailingBlockStates.isPresent()
                ? decks.findRailings(chunkPos)
                : new boolean[NearestCenters.SIZE * NearestCenters.SIZE];

        for (int column = 0; column < 16 * 16; column++) {
            int x = chunkPos.getMinBlockX() + (column >> 4);
            int z = chunkPos.getMinBlockZ() + (column & 15);
            BlockPos center = nearest.center(x, z);
            if (center == null) {
                continue;
            }
            mutable.set(x, groundHeights[column], z);

            if (bridgeCenters.contains(center)) {
                // A bridge's edge is measured from its center line alone, so it doesn't trace the ground far below
                if (decks.isDeck(x, z)) {
                    boolean decayed = decks.isDecayed(x, center.getY(), z);
                    placeBridgeColumn(writer, random, mutable, center.getY(), decayed, roadTypes[column], config, settings);
                    if (hasRailing[nearest.index(x, z)]) {
                        placeRailing(writer, random, mutable.setY(center.getY()), nearest, hasRailing, config);
                    }
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
     * Places a railing on the bridge block at the given position, connected to the railings beside it at the same
     * height. Connections are set here rather than by block updates, so they reach railings in neighboring chunks
     * whichever chunk generates first, and the placed state is final.
     *
     * @param deck The deck block the railing stands on. Nothing is placed unless it's a bridge block.
     */
    private static void placeRailing(RoadBlockWriter writer, RandomSource random, BlockPos deck, NearestCenters nearest,
                                     boolean[] hasRailing, RoadFeatureConfiguration config) {
        if (!isBridgeBlock(writer.getBlockState(deck), config)) {
            return;
        }
        boolean[] connected = new boolean[4];
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int x = deck.getX() + direction.getStepX();
            int z = deck.getZ() + direction.getStepZ();
            BlockPos neighborCenter = nearest.center(x, z);
            connected[direction.get2DDataValue()] = hasRailing[nearest.index(x, z)]
                    && neighborCenter != null && neighborCenter.getY() == deck.getY();
        }
        BlockState railing = config.bridgeRailingBlockStates.orElseThrow().get(random);
        writer.setBlock(deck.above(), connectRailing(railing, connected));
    }

    /**
     * Sets which sides of a fence, wall, pane, or bars block connect to its neighbors. Other blocks are left as is.
     *
     * @param connected Whether each horizontal side connects, indexed by {@link Direction#get2DDataValue}.
     */
    private static BlockState connectRailing(BlockState railing, boolean[] connected) {
        boolean north = connected[Direction.NORTH.get2DDataValue()];
        boolean east = connected[Direction.EAST.get2DDataValue()];
        boolean south = connected[Direction.SOUTH.get2DDataValue()];
        boolean west = connected[Direction.WEST.get2DDataValue()];
        if (railing.getBlock() instanceof CrossCollisionBlock) {
            return railing
                    .setValue(CrossCollisionBlock.NORTH, north)
                    .setValue(CrossCollisionBlock.EAST, east)
                    .setValue(CrossCollisionBlock.SOUTH, south)
                    .setValue(CrossCollisionBlock.WEST, west);
        }
        if (railing.getBlock() instanceof WallBlock) {
            // Like vanilla walls, a post shows everywhere except along a straight run
            boolean straight = (north && south && !east && !west) || (east && west && !north && !south);
            return railing
                    .setValue(WallBlock.NORTH_WALL, wallSide(north))
                    .setValue(WallBlock.EAST_WALL, wallSide(east))
                    .setValue(WallBlock.SOUTH_WALL, wallSide(south))
                    .setValue(WallBlock.WEST_WALL, wallSide(west))
                    .setValue(WallBlock.UP, !straight);
        }
        return railing;
    }

    private static WallSide wallSide(boolean connected) {
        return connected ? WallSide.LOW : WallSide.NONE;
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
     * beyond which the road rises with the ground instead. Any gap below the road is filled with the road type's fill
     * blocks, which also replace the ground block the road covers, so a surface layer like grass isn't left buried.
     * Road types without fill blocks copy the ground's own block instead.
     */
    private static void placeOnGround(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight,
                                      RoadTypeConfig roadType, ConfigModule.Advanced settings) {
        BlockState groundState = writer.getBlockState(ground);
        writer.clearVegetationAbove(ground);
        int y = Math.max(roadHeight, ground.getY() - settings.maxCutDepth);
        for (int cutY = ground.getY(); cutY > y; cutY--) {
            writer.setBlock(ground.atY(cutY), Blocks.AIR.defaultBlockState());
        }
        if (roadType.fillBlockStates.isPresent()) {
            BlockStateRandomizer fill = roadType.fillBlockStates.get();
            for (int fillY = ground.getY(); fillY < y; fillY++) {
                writer.setBlock(ground.atY(fillY), fill.get(random));
            }
        } else {
            BlockState fillState = copiedFillState(writer, ground, groundState);
            for (int fillY = ground.getY() + 1; fillY < y; fillY++) {
                writer.setBlock(ground.atY(fillY), fillState);
            }
        }
        writer.setBlock(ground.atY(y), roadType.pathBlockStates.get(random));
    }

    /**
     * The block to fill gaps under a road with when its road type has no fill blocks: the ground's own block, so a
     * raised road's sides blend in with the terrain, or dirt if the ground isn't a plain full block. Properties like
     * snow cover are reset, since the fill is covered by the road.
     */
    private static BlockState copiedFillState(RoadBlockWriter writer, BlockPos ground, BlockState groundState) {
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

    /**
     * The nearest road center to each column in a chunk and up to {@link #RAILING_REACH} blocks past its edges, out of
     * the centers within {@link #PLACEMENT_REACH} of the column. A column is placed by its nearest center alone. Ties
     * go to the lowest position, so every chunk agrees on a column's center, whatever order it gets the centers in.
     */
    private static final class NearestCenters {
        static final int SIZE = 16 + 2 * RAILING_REACH;

        private final int minX;
        private final int minZ;
        private final int[] distSq = new int[SIZE * SIZE];
        private final BlockPos[] centers = new BlockPos[SIZE * SIZE];

        NearestCenters(ChunkPos chunkPos) {
            this.minX = chunkPos.getMinBlockX() - RAILING_REACH;
            this.minZ = chunkPos.getMinBlockZ() - RAILING_REACH;
            Arrays.fill(this.distSq, Integer.MAX_VALUE);
        }

        /** Records the center as the column's nearest, if it's nearer than the nearest so far. */
        void offer(int x, int z, BlockPos center, int distSq) {
            int i = index(x, z);
            if (i < 0) {
                return;
            }
            if (distSq < this.distSq[i] || (distSq == this.distSq[i] && center.asLong() < this.centers[i].asLong())) {
                this.distSq[i] = distSq;
                this.centers[i] = center;
            }
        }

        /** The column's nearest center, or null if no center reaches it. */
        @Nullable
        BlockPos center(int x, int z) {
            return this.centers[index(x, z)];
        }

        /** The squared horizontal distance from the column to its nearest center. */
        int distSq(int x, int z) {
            return this.distSq[index(x, z)];
        }

        /** The column's index in per-column arrays of length SIZE², or -1 if it isn't covered. */
        int index(int x, int z) {
            int gridX = x - this.minX;
            int gridZ = z - this.minZ;
            if (gridX < 0 || gridX >= SIZE || gridZ < 0 || gridZ >= SIZE) {
                return -1;
            }
            return gridX * SIZE + gridZ;
        }
    }

    /**
     * The shape of the bridge decks in and around a chunk, and where their railings go.
     * <p>
     * Railings are decided from the road centers, and from blocks that road placement never changes: the block under a
     * deck, and the columns beside it. So a chunk decides the railings just past its edges the same way their own chunk
     * does, whichever generates first, which lets railings connect across chunk borders.
     */
    private final class BridgeDecks {
        private final RoadBlockWriter writer;
        private final NearestCenters nearest;
        private final Set<BlockPos> bridgeCenters;
        private final RoadFeatureConfiguration config;
        private final ConfigModule.Advanced settings;
        private final long worldSeed;

        BridgeDecks(RoadBlockWriter writer, NearestCenters nearest, Set<BlockPos> bridgeCenters,
                    RoadFeatureConfiguration config, ConfigModule.Advanced settings) {
            this.writer = writer;
            this.nearest = nearest;
            this.bridgeCenters = bridgeCenters;
            this.config = config;
            this.settings = settings;
            this.worldSeed = writer.level().getSeed();
        }

        /** Whether the column is part of a bridge's deck, decayed or not. */
        boolean isDeck(int x, int z) {
            BlockPos center = this.nearest.center(x, z);
            return center != null && this.bridgeCenters.contains(center)
                    && Math.sqrt(this.nearest.distSq(x, z)) < halfWidth(center, x, z);
        }

        /**
         * Whether the column is beside the road rather than on it: past the edge of a bridge's deck, or out of reach of
         * every center. Columns nearest a center on the ground count as road, so railings don't cross a bridge's ends.
         */
        boolean isOffRoad(int x, int z) {
            BlockPos center = this.nearest.center(x, z);
            return center == null || (this.bridgeCenters.contains(center)
                    && Math.sqrt(this.nearest.distSq(x, z)) >= halfWidth(center, x, z));
        }

        /**
         * Whether the block at the given height in a deck column has decayed away. Decay is likelier toward the deck's
         * edges, and never breaks the center line.
         */
        boolean isDecayed(int x, int y, int z) {
            BlockPos center = this.nearest.center(x, z);
            double decayChance = this.settings.landBridgeDecay * Math.sqrt(this.nearest.distSq(x, z)) / halfWidth(center, x, z);
            return decayChance > 0 && decayRoll(this.worldSeed, x, y, z) < decayChance;
        }

        /**
         * Finds the railings in the chunk and one block past its edges.
         *
         * @return Whether each column has a railing, indexed by {@link NearestCenters#index}.
         */
        boolean[] findRailings(ChunkPos chunkPos) {
            boolean[] hasRailing = new boolean[NearestCenters.SIZE * NearestCenters.SIZE];
            for (int x = chunkPos.getMinBlockX() - 1; x <= chunkPos.getMaxBlockX() + 1; x++) {
                for (int z = chunkPos.getMinBlockZ() - 1; z <= chunkPos.getMaxBlockZ() + 1; z++) {
                    hasRailing[this.nearest.index(x, z)] = hasRailing(x, z);
                }
            }
            return hasRailing;
        }

        /**
         * Whether the column gets a railing: it's a bridge block on the edge of a deck, beside a drop at least as deep
         * as the railing drop setting. Railings are never on the center line, and they decay like the deck, so one is
         * also missing wherever the deck below it is. Of the columns that qualify, only the railing chance setting's
         * share get one.
         */
        private boolean hasRailing(int x, int z) {
            BlockPos center = this.nearest.center(x, z);
            if (center == null || this.nearest.distSq(x, z) == 0 || !isDeck(x, z)) {
                return false;
            }
            int y = center.getY();
            if (isDecayed(x, y, z) || isDecayed(x, y + 1, z)) {
                return false;
            }
            // Rolled separately from decay, which already rolls at the railing's position
            double chance = this.settings.landBridgeRailingChance / 100.0;
            if (chance < 1 && decayRoll(this.worldSeed ^ RAILING_CHANCE_SALT, x, y + 1, z) >= chance) {
                return false;
            }
            if (!hasBridgeBlock(x, y, z)) {
                return false;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Diagonal neighbors count too, so the railings along a diagonal edge form a connected staircase
                    if ((dx != 0 || dz != 0) && isOffRoad(x + dx, z + dz) && hasDropBelow(x + dx, y, z + dz)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Whether the undecayed deck column is a bridge block, rather than road resting on the ground, decided the same
         * way {@link #placeBridgeColumn} does. A bridge block that's already there counts, in case the column's own
         * chunk has placed its roads, which changes the ground the decision reads.
         */
        private boolean hasBridgeBlock(int x, int deckY, int z) {
            if (!this.writer.level().hasChunk(x >> 4, z >> 4)) {
                return false;
            }
            if (isBridgeBlock(this.writer.getBlockState(new BlockPos(x, deckY, z)), this.config)) {
                return true;
            }
            int groundY = this.writer.surfaceHeight(x, z);
            boolean overWater = !this.writer.getBlockState(new BlockPos(x, groundY, z)).getFluidState().isEmpty();
            // Over water, the bridge block is at the deck's height unless the water's surface is higher
            return overWater ? groundY <= deckY : deckY - groundY - 1 > 0;
        }

        /** Whether stepping off the deck into the column means falling at least the railing drop setting. */
        private boolean hasDropBelow(int x, int deckY, int z) {
            for (int y = deckY; y > deckY - this.settings.landBridgeRailingDrop; y--) {
                if (!isOpen(x, y, z)) {
                    return false;
                }
            }
            return true;
        }

        /** Whether the block is above the ground, such as air or plants. Blocks in unloaded chunks count as ground. */
        private boolean isOpen(int x, int y, int z) {
            return this.writer.level().hasChunk(x >> 4, z >> 4)
                    && RoadBlockWriter.isAboveGround(this.writer.getBlockState(new BlockPos(x, y, z)));
        }

        private double halfWidth(BlockPos center, int x, int z) {
            return bridgeHalfWidth(center, x, z, this.config, this.settings);
        }
    }

    static boolean isInChunk(ChunkPos chunkPos, BlockPos blockPos) {
        return isInChunk(chunkPos, blockPos.getX(), blockPos.getZ());
    }

    static boolean isInChunk(ChunkPos chunkPos, int x, int z) {
        return (x >> 4) == chunkPos.x && (z >> 4) == chunkPos.z;
    }
}
