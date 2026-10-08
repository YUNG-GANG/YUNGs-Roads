package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSettings;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSurfaceConfig;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.feature.RoadFeature;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.RoadCenter;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.RoadBlockWriter;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class AbstractRoadGenerator {
    /** How far, in blocks, edge roughness may move a bridge's edge in or out, at the maximum setting. */
    private static final double MAX_EDGE_ROUGHNESS = 1.0;

    /**
     * The furthest horizontal distance (along either axis) from a road center position that placement may modify: half
     * the widest road any road type can have, plus as far as edge roughness can move a bridge's edge out.
     */
    public static final int PLACEMENT_REACH = (int) Math.ceil(
            (RoadSetting.ROAD_WIDTH.max() + RoadSetting.WIDTH_VARIATION.max()) / 2 + MAX_EDGE_ROUGHNESS);

    /** The least distance, in blocks, a bridge extends past the last center that crosses the hole or dip it spans. */
    private static final int MIN_BRIDGE_MARGIN = 3;

    /** The furthest a bridge of any road type extends past the last center that crosses the hole or dip it spans. */
    private static final int MAX_BRIDGE_MARGIN = bridgeMargin(
            (RoadSetting.ROAD_WIDTH.max() + RoadSetting.WIDTH_VARIATION.max()) / 2);

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
    public static final int PLACEMENT_LOOKUP_REACH = PLACEMENT_REACH + MAX_BRIDGE_MARGIN + RAILING_REACH;

    static {
        // Roads are placed in the features step, when only the neighboring chunks are sure to have terrain. A chunk reads
        // the ground at road centers up to the lookup reach past its edges to find bridges, so the reach must stay within
        // its neighbors. Further out, a center could read as floating over a hole, giving bridges that depend on which
        // chunks happened to generate first. Capping road width keeps the reach in bounds.
        if (PLACEMENT_LOOKUP_REACH > 16) {
            throw new IllegalStateException("Road placement lookup reach " + PLACEMENT_LOOKUP_REACH
                    + " exceeds a chunk. Lower the max Road Width or Width Variation.");
        }
    }

    /** How many blocks of air a tunnel has above its road, away from its edges, where the ceiling is a block lower. */
    private static final int TUNNEL_HEIGHT = 4;

    /** The thinnest ceiling a tunnel is bored under. Where it would be thinner, the road is cut open to the sky. */
    private static final int MIN_TUNNEL_CEILING = 3;

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
     * @param roadType The road type the road is generated with.
     * @param terrain Terrain samples shared by all roads generated for the same region.
     * @return Road connecting the two positions, if one was successfully generated.
     */
    public abstract Optional<Road> generateRoad(BlockPos pos1, BlockPos pos2, RoadTypes.Choice roadType, TerrainCache terrain);

    /**
     * Places road blocks around each of the given road center positions, at the heights stored in their y.
     * Only blocks inside the given chunk are modified, so the result doesn't depend on chunk generation order.
     * Each column is placed once, at the height of its nearest center, so the road is level across its width.
     * <p>
     * Where the road crosses a hole or dip, it's carried on a bridge whose deck spans every gap beneath it. Whether a
     * center is on a bridge is decided from the centers around it, rather than from the ground across the road's width,
     * so a whole stretch of road bridges together, while steep drops beside a road resting on the ground are left alone.
     * <p>
     * Where the road runs deep below the ground, it's carried through a tunnel, whose walls are lined or made stable.
     * <p>
     * Each column is placed with the settings of its nearest center's road. Where roads overlap at the same position,
     * the position belongs to the road that comes first by {@link Road#compare}, so every chunk agrees.
     *
     * @param roadCenters The road center positions within {@link #PLACEMENT_LOOKUP_REACH} blocks of the chunk.
     * @param isLandBridge Whether a center position is part of a land bridge. See {@link Road#landBridges}.
     * @param isTunnel Whether a center position is part of a tunnel. See {@link Road#tunnels}.
     */
    public void placeRoadInChunk(RoadBlockWriter writer, RandomSource random, ChunkPos chunkPos, List<RoadCenter> roadCenters,
                                 Predicate<BlockPos> isLandBridge, Predicate<BlockPos> isTunnel) {
        Map<BlockPos, Road> roadAt = new LinkedHashMap<>();
        for (RoadCenter center : roadCenters) {
            roadAt.merge(center.pos(), center.road(), (a, b) -> Road.compare(a, b) <= 0 ? a : b);
        }
        List<BlockPos> centers = List.copyOf(roadAt.keySet());
        Function<BlockPos, RoadSettings> settingsAt = center -> roadAt.get(center).settings;

        // Per column of the chunk, indexed by (x & 15) << 4 | (z & 15)
        ColumnSurfaces surfaces = new ColumnSurfaces(writer, chunkPos);
        boolean[] isGroundRoad = new boolean[16 * 16];
        NearestCenters nearest = new NearestCenters(chunkPos);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (BlockPos center : centers) {
            RoadSettings settings = settingsAt.apply(center);
            double halfWidth = settings.halfWidth(widthNoise(center));
            // A center only reaches the columns its road could cover, as a bridge with the roughest edges, so where a
            // narrow road meets a wider one, the narrow road's centers don't claim the wider road's columns
            double reach = halfWidth + settings.landBridgeEdgeRoughness * MAX_EDGE_ROUGHNESS;
            int maxOffset = (int) Math.ceil(reach);

            for (int dx = -maxOffset; dx <= maxOffset; dx++) {
                for (int dz = -maxOffset; dz <= maxOffset; dz++) {
                    // Distances are kept as squared values as an optimization
                    int distSq = dx * dx + dz * dz;
                    if (distSq >= reach * reach) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    nearest.offer(x, z, center, distSq);
                    if (isInChunk(chunkPos, x, z) && distSq < halfWidth * halfWidth) {
                        isGroundRoad[(x & 15) << 4 | (z & 15)] = true;
                    }
                }
            }
        }

        // Decided before placing anything, since placing changes the ground the decisions read
        Set<BlockPos> tunnelCenters = new HashSet<>();
        for (BlockPos center : centers) {
            if (isTunnel.test(center)) {
                tunnelCenters.add(center);
            }
        }
        Set<BlockPos> bridgeCenters = findBridgeCenters(writer, centers, isLandBridge, tunnelCenters, settingsAt);
        BridgeDecks decks = new BridgeDecks(writer, nearest, bridgeCenters, settingsAt);
        boolean[] hasRailing = decks.findRailings(chunkPos);
        Tunnels tunnels = new Tunnels(nearest, tunnelCenters, settingsAt);

        for (int column = 0; column < 16 * 16; column++) {
            int x = chunkPos.getMinBlockX() + (column >> 4);
            int z = chunkPos.getMinBlockZ() + (column & 15);
            BlockPos center = nearest.center(x, z);
            if (center == null) {
                tunnels.placeWall(writer, random, x, z);
                continue;
            }
            RoadSettings settings = settingsAt.apply(center);
            RoadSurfaceConfig surface = surfaces.surface(column, settings);
            mutable.set(x, surfaces.groundHeight(column), z);

            if (bridgeCenters.contains(center)) {
                // A bridge's edge is measured from its center line alone, so it doesn't trace the ground far below
                if (decks.isDeck(x, z)) {
                    boolean decayed = decks.isDecayed(x, center.getY(), z);
                    placeBridgeColumn(writer, random, mutable, center.getY(), decayed, surface, settings);
                    if (hasRailing[nearest.index(x, z)]) {
                        placeRailing(writer, random, mutable.setY(center.getY()), nearest, hasRailing, settings);
                    }
                } else {
                    tunnels.placeWall(writer, random, x, z);
                }
            } else if (tunnels.isInterior(x, z)) {
                placeTunnelColumn(writer, random, mutable, center.getY(), tunnels.clearance(x, z), surface, settings);
            } else if (isGroundRoad[column]) {
                placeGroundColumn(writer, random, mutable, center.getY(), surface, settings);
            } else {
                tunnels.placeWall(writer, random, x, z);
            }
        }
    }

    /**
     * Subtly varies the road's width along its length to make its shape more interesting. Ranges from 0 to 1, how far
     * the road is widened by its width variation.
     */
    private double widthNoise(BlockPos center) {
        // Clamped, since the reach placement allows for assumes the road is never wider than its settings say
        return Mth.clamp((this.noise.GetNoise(center.getX(), center.getZ()) + 1) / 2, 0, 1);
    }

    /**
     * The distance from a bridge center within which its deck is placed, exclusive. The deck is as wide as the road
     * there, so it's as wide as the road leading onto it, and its width doesn't depend on what's at the bottom of the
     * hole. Edge roughness then moves the edge in or out per column, but never through the center line, so a narrow
     * road's bridge isn't broken.
     */
    private double bridgeHalfWidth(BlockPos center, int x, int z, RoadSettings settings) {
        double roughness = Mth.clamp(this.edgeNoise.GetNoise(x, z), -1, 1);
        return Math.max(0.5, halfWidth(center, settings) + settings.landBridgeEdgeRoughness * MAX_EDGE_ROUGHNESS * roughness);
    }

    /** The distance from a center within which its road is placed, exclusive. */
    private double halfWidth(BlockPos center, RoadSettings settings) {
        return settings.halfWidth(widthNoise(center));
    }

    /**
     * How far, in blocks, a bridge extends past the last center that crosses the hole or dip it spans. At least the
     * road's half-width, so where the road crosses a rim at an angle, it's bridged across its whole width.
     */
    private static int bridgeMargin(double maxHalfWidth) {
        return Math.max(MIN_BRIDGE_MARGIN, (int) Math.ceil(maxHalfWidth));
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
     * Finds the centers whose road is carried on a bridge: those within their road's {@link #bridgeMargin} of a center
     * that crosses a hole or is part of a land bridge. The margin carries the bridge past each rim, so where the road meets a
     * rim at an angle, the side of the road that's already over the hole is bridged too. Tunnels are never bridged, so a
     * tunnel can open straight onto a bridge.
     */
    private static Set<BlockPos> findBridgeCenters(RoadBlockWriter writer, List<BlockPos> centers,
                                                   Predicate<BlockPos> isLandBridge, Set<BlockPos> tunnelCenters,
                                                   Function<BlockPos, RoadSettings> settingsAt) {
        List<BlockPos> crossings = centers.stream()
                .filter(center -> isLandBridge.test(center) || crossesHole(writer, center, settingsAt.apply(center)))
                .toList();
        Set<BlockPos> bridgeCenters = new HashSet<>();
        for (BlockPos center : centers) {
            if (tunnelCenters.contains(center)) {
                continue;
            }
            int margin = bridgeMargin(settingsAt.apply(center).maxHalfWidth());
            for (BlockPos crossing : crossings) {
                int dx = crossing.getX() - center.getX();
                int dz = crossing.getZ() - center.getZ();
                if (dx * dx + dz * dz <= margin * margin) {
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
     * The center may be in a neighboring chunk that has already placed its roads, so a placed bridge is measured from
     * the ground under it, which placing it left unchanged. That keeps the result the same whichever chunk generates
     * first. A bridge's center line is never decayed, so its block is always there to find.
     * <p>
     * A bridge block alone doesn't mean a hole: a bridge extends past the holes it crosses, over gaps too small to
     * count as one. Counting those would let a chunk that generates later extend the bridge further than one that
     * generated first.
     */
    private static boolean crossesHole(RoadBlockWriter writer, BlockPos center, RoadSettings settings) {
        if (!writer.level().hasChunk(center.getX() >> 4, center.getZ() >> 4)) {
            // Unknown, which only happens next to unloaded chunks in a live world. Bridge any gaps to be safe.
            return true;
        }

        int groundHeight;
        if (isBridgeBlock(writer.getBlockState(center), settings)) {
            BlockPos.MutableBlockPos ground = center.mutable().move(Direction.DOWN);
            while (ground.getY() > writer.level().getMinBuildHeight() && RoadBlockWriter.isAboveGround(writer.getBlockState(ground))) {
                ground.move(Direction.DOWN);
            }
            groundHeight = ground.getY();
        } else {
            groundHeight = writer.surfaceHeight(center.getX(), center.getZ());
        }
        int gap = center.getY() - groundHeight - 1;
        boolean overWater = !writer.getBlockState(center.atY(groundHeight)).getFluidState().isEmpty();
        return overWater ? gap > 0 : gap > settings.maxFillDepth;
    }

    private static boolean isBridgeBlock(BlockState state, RoadSettings settings) {
        BlockStateRandomizer bridge = settings.blocks.bridgeBlockStates();
        return state == bridge.getDefaultBlockState() || bridge.getEntriesAsMap().containsKey(state);
    }

    /**
     * Places a railing on the bridge block at the given position, connected to the railings beside it at the same
     * height. Connections are set here rather than by block updates, so they reach railings in neighboring chunks
     * whichever chunk generates first, and the placed state is final.
     *
     * @param deck The deck block the railing stands on. Nothing is placed unless it's a bridge block.
     * @param settings The settings of the deck's road, which must have railings.
     */
    private static void placeRailing(RoadBlockWriter writer, RandomSource random, BlockPos deck, NearestCenters nearest,
                                     boolean[] hasRailing, RoadSettings settings) {
        if (!isBridgeBlock(writer.getBlockState(deck), settings)) {
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
        BlockState railing = settings.blocks.bridgeRailingBlockStates().orElseThrow().get(random);
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
     * Determines a road type's surface for the given ground block, based on the block and its biome's temperature.
     */
    private static RoadSurfaceConfig surfaceAt(RoadBlockWriter writer, BlockPos groundPos, RoadSettings settings) {
        for (RoadSurfaceConfig surface : settings.blocks.surfaces()) {
            if (surface.matches(writer.level(), groundPos)) {
                return surface;
            }
        }
        return RoadSurfaceConfig.FALLBACK;
    }

    /**
     * Places a road resting on the ground in one column at the given height, shaping the ground to meet it. Gaps below
     * the road up to the max fill depth are filled, and deeper drops left alone, so on steep hillsides the road narrows
     * to a ledge rather than sprouting stray bridge blocks. Over water, the road is always a bridge.
     *
     * @param ground The column's ground block, which may be water.
     * @param surface The road type's surface matching the ground block.
     */
    private static void placeGroundColumn(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight,
                                          RoadSurfaceConfig surface, RoadSettings settings) {
        if (!writer.getBlockState(ground).getFluidState().isEmpty()) {
            placeWaterBridge(writer, random, ground, roadHeight, settings);
        } else if (roadHeight - ground.getY() - 1 <= settings.maxFillDepth) {
            placeOnGround(writer, random, ground, roadHeight, surface, settings);
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
                                          boolean decayed, RoadSurfaceConfig surface, RoadSettings settings) {
        if (!writer.getBlockState(ground).getFluidState().isEmpty()) {
            placeWaterBridge(writer, random, ground, roadHeight, settings);
        } else if (roadHeight - ground.getY() - 1 > 0) {
            if (!decayed) {
                writer.setBlock(ground.atY(roadHeight), settings.blocks.bridgeBlockStates().get(random));
            }
        } else {
            placeOnGround(writer, random, ground, roadHeight, surface, settings);
        }
    }

    /**
     * Places one column of a tunnel at the given road height. Where the ground is no more than the max cut depth above
     * the road, as at a tunnel's mouth, the road is placed as on the ground. Deeper, the road is bored through, under a
     * ceiling that's lined or made stable, unless the ceiling would be thinner than {@link #MIN_TUNNEL_CEILING}, where
     * the road is cut open to the sky instead. Under water, it's always bored, so the ceiling holds the water back.
     * <p>
     * A bored road's surface matches the block it's bored into, rather than the ground far above.
     *
     * @param ground The column's ground block, which may be water.
     * @param clearance How many blocks of air a bored tunnel has above the road.
     * @param groundSurface The road type's surface matching the ground block, used where the road is placed as on the
     *                      ground.
     */
    private static void placeTunnelColumn(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight,
                                          int clearance, RoadSurfaceConfig groundSurface, RoadSettings settings) {
        if (ground.getY() - roadHeight <= settings.maxCutDepth) {
            placeGroundColumn(writer, random, ground, roadHeight, groundSurface, settings);
            return;
        }

        BlockPos road = ground.atY(roadHeight);
        RoadSurfaceConfig surface = surfaceAt(writer, road, settings);
        int ceilingY = roadHeight + clearance + 1;
        boolean bored = ground.getY() - ceilingY + 1 >= MIN_TUNNEL_CEILING
                || !writer.getBlockState(ground).getFluidState().isEmpty();
        if (!bored) {
            writer.clearVegetationAbove(ground);
        }
        int top = bored ? ceilingY - 1 : ground.getY();
        for (int y = roadHeight + 1; y <= top; y++) {
            BlockPos pos = ground.atY(y);
            if (!writer.getBlockState(pos).isAir()) {
                writer.setBlock(pos, Blocks.AIR.defaultBlockState());
            }
        }
        writer.setBlock(road, surface.pathBlockStates.get(random));
        if (bored) {
            secureTunnelBlock(writer, random, ground.atY(ceilingY), settings);
        }
    }

    /**
     * Makes a block of a tunnel's wall or ceiling the tunnel lining, if the tunnel's road type has one. Otherwise only
     * fluids and blocks that fall are replaced with solid blocks, since they would pour or collapse into the tunnel.
     */
    private static void secureTunnelBlock(RoadBlockWriter writer, RandomSource random, BlockPos pos, RoadSettings settings) {
        if (settings.blocks.tunnelLiningBlockStates().isPresent()) {
            writer.setBlock(pos, settings.blocks.tunnelLiningBlockStates().get().get(random));
            return;
        }
        BlockState state = writer.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            writer.setBlock(pos, Blocks.STONE.defaultBlockState());
        } else if (state.getBlock() instanceof FallingBlock) {
            writer.setBlock(pos, solidCounterpart(state));
        }
    }

    /** A solid block resembling the given block that falls, such as sandstone for sand. */
    private static BlockState solidCounterpart(BlockState fallingBlock) {
        if (fallingBlock.is(Blocks.SAND)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        if (fallingBlock.is(Blocks.RED_SAND)) {
            return Blocks.RED_SANDSTONE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    /**
     * Places a bridge block over water. A bridge never dips below the water's surface.
     */
    private static void placeWaterBridge(RoadBlockWriter writer, RandomSource random, BlockPos water, int roadHeight,
                                         RoadSettings settings) {
        writer.setBlock(water.atY(Math.max(roadHeight, water.getY())), settings.blocks.bridgeBlockStates().get(random));
    }

    /**
     * Places the road on solid ground at the given height. Ground above the road is cut away, up to the max cut depth,
     * beyond which the road rises with the ground instead. Any gap below the road is filled with the surface's fill
     * blocks, which also replace the ground block the road covers, so a surface layer like grass isn't left buried.
     * Surfaces without fill blocks copy the ground's own block instead.
     */
    private static void placeOnGround(RoadBlockWriter writer, RandomSource random, BlockPos ground, int roadHeight,
                                      RoadSurfaceConfig surface, RoadSettings settings) {
        BlockState groundState = writer.getBlockState(ground);
        writer.clearVegetationAbove(ground);
        int y = Math.max(roadHeight, ground.getY() - settings.maxCutDepth);
        for (int cutY = ground.getY(); cutY > y; cutY--) {
            writer.setBlock(ground.atY(cutY), Blocks.AIR.defaultBlockState());
        }
        if (surface.fillBlockStates.isPresent()) {
            BlockStateRandomizer fill = surface.fillBlockStates.get();
            for (int fillY = ground.getY(); fillY < y; fillY++) {
                writer.setBlock(ground.atY(fillY), fill.get(random));
            }
        } else {
            BlockState fillState = copiedFillState(writer, ground, groundState);
            for (int fillY = ground.getY() + 1; fillY < y; fillY++) {
                writer.setBlock(ground.atY(fillY), fillState);
            }
        }
        writer.setBlock(ground.atY(y), surface.pathBlockStates.get(random));
    }

    /**
     * The block to fill gaps under a road with when its surface has no fill blocks: the ground's own block, so a
     * raised road's sides blend in with the terrain, or dirt if the ground isn't a plain full block. Properties like
     * snow cover are reset, since the fill is covered by the road.
     */
    private static BlockState copiedFillState(RoadBlockWriter writer, BlockPos ground, BlockState groundState) {
        return groundState.isSolidRender(writer.level(), ground) && !groundState.hasBlockEntity()
                ? groundState.getBlock().defaultBlockState()
                : Blocks.DIRT.defaultBlockState();
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
     * <p>
     * Each deck column follows the settings of its nearest center's road.
     */
    private final class BridgeDecks {
        private final RoadBlockWriter writer;
        private final NearestCenters nearest;
        private final Set<BlockPos> bridgeCenters;
        private final Function<BlockPos, RoadSettings> settingsAt;
        private final long worldSeed;

        BridgeDecks(RoadBlockWriter writer, NearestCenters nearest, Set<BlockPos> bridgeCenters,
                    Function<BlockPos, RoadSettings> settingsAt) {
            this.writer = writer;
            this.nearest = nearest;
            this.bridgeCenters = bridgeCenters;
            this.settingsAt = settingsAt;
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
            double decayChance = this.settingsAt.apply(center).landBridgeDecay * Math.sqrt(this.nearest.distSq(x, z)) / halfWidth(center, x, z);
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
         * Whether the column gets a railing: it's a bridge block on the edge of a deck whose road type has railings,
         * beside a drop at least as deep as the railing drop setting. Railings are never on the center line, and they
         * decay like the deck, so one is also missing wherever the deck below it is. Of the columns that qualify, only
         * the railing chance setting's share get one.
         */
        private boolean hasRailing(int x, int z) {
            BlockPos center = this.nearest.center(x, z);
            if (center == null || this.nearest.distSq(x, z) == 0 || !isDeck(x, z)) {
                return false;
            }
            RoadSettings settings = this.settingsAt.apply(center);
            if (settings.blocks.bridgeRailingBlockStates().isEmpty()) {
                return false;
            }
            int y = center.getY();
            if (isDecayed(x, y, z) || isDecayed(x, y + 1, z)) {
                return false;
            }
            // Rolled separately from decay, which already rolls at the railing's position
            double chance = settings.landBridgeRailingChance / 100.0;
            if (chance < 1 && decayRoll(this.worldSeed ^ RAILING_CHANCE_SALT, x, y + 1, z) >= chance) {
                return false;
            }
            if (!hasBridgeBlock(x, y, z, settings)) {
                return false;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Diagonal neighbors count too, so the railings along a diagonal edge form a connected staircase
                    if ((dx != 0 || dz != 0) && isOffRoad(x + dx, z + dz) && hasDropBelow(x + dx, y, z + dz, settings.landBridgeRailingDrop)) {
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
        private boolean hasBridgeBlock(int x, int deckY, int z, RoadSettings settings) {
            if (!this.writer.level().hasChunk(x >> 4, z >> 4)) {
                return false;
            }
            if (isBridgeBlock(this.writer.getBlockState(new BlockPos(x, deckY, z)), settings)) {
                return true;
            }
            int groundY = this.writer.surfaceHeight(x, z);
            boolean overWater = !this.writer.getBlockState(new BlockPos(x, groundY, z)).getFluidState().isEmpty();
            // Over water, the bridge block is at the deck's height unless the water's surface is higher
            return overWater ? groundY <= deckY : deckY - groundY - 1 > 0;
        }

        /** Whether stepping off the deck into the column means falling at least the given railing drop. */
        private boolean hasDropBelow(int x, int deckY, int z, int railingDrop) {
            for (int y = deckY; y > deckY - railingDrop; y--) {
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
            return bridgeHalfWidth(center, x, z, this.settingsAt.apply(center));
        }
    }

    /**
     * The tunnels in and around a chunk: which columns are bored out, and which are the walls beside them.
     * <p>
     * Like bridge decks, a tunnel's shape is decided from the road centers alone, so a chunk agrees with its neighbors
     * on where the walls of their tunnels are, whichever generates first. A wall then reads only its own column.
     */
    private final class Tunnels {
        private final NearestCenters nearest;
        private final Set<BlockPos> tunnelCenters;
        private final Function<BlockPos, RoadSettings> settingsAt;

        Tunnels(NearestCenters nearest, Set<BlockPos> tunnelCenters, Function<BlockPos, RoadSettings> settingsAt) {
            this.nearest = nearest;
            this.tunnelCenters = tunnelCenters;
            this.settingsAt = settingsAt;
        }

        /**
         * Whether the column is bored out: nearest a tunnel center, within the road's width.
         */
        boolean isInterior(int x, int z) {
            BlockPos center = this.nearest.center(x, z);
            return center != null && this.tunnelCenters.contains(center)
                    && Math.sqrt(this.nearest.distSq(x, z)) < halfWidth(center, this.settingsAt.apply(center));
        }

        /**
         * How many blocks of air are bored above the road in an interior column. The ceiling is a block lower along the
         * tunnel's edges, for an arched look.
         */
        int clearance(int x, int z) {
            BlockPos center = this.nearest.center(x, z);
            return Math.sqrt(this.nearest.distSq(x, z)) < halfWidth(center, this.settingsAt.apply(center)) - 1
                    ? TUNNEL_HEIGHT
                    : TUNNEL_HEIGHT - 1;
        }

        /**
         * Secures the wall of any tunnel beside the column, which mustn't be bored out itself: the blocks from each
         * neighboring interior column's road up to its ceiling, below the column's own ground surface. Diagonal
         * neighbors count too, so nothing leaks in through a tunnel's corners. The wall is lined as the first
         * neighboring interior column's road type would line it.
         */
        void placeWall(RoadBlockWriter writer, RandomSource random, int x, int z) {
            if (this.tunnelCenters.isEmpty()) {
                return;
            }
            int bottom = Integer.MAX_VALUE;
            int top = Integer.MIN_VALUE;
            RoadSettings settings = null;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && isInterior(x + dx, z + dz)) {
                        BlockPos center = this.nearest.center(x + dx, z + dz);
                        bottom = Math.min(bottom, center.getY());
                        top = Math.max(top, center.getY() + clearance(x + dx, z + dz) + 1);
                        if (settings == null) {
                            settings = this.settingsAt.apply(center);
                        }
                    }
                }
            }
            if (settings == null) {
                return;
            }
            top = Math.min(top, writer.surfaceHeight(x, z));
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
            for (int y = bottom; y <= top; y++) {
                secureTunnelBlock(writer, random, pos.setY(y), settings);
            }
        }
    }

    /**
     * The ground height of each column in a chunk, and which of a road type's surfaces matches its ground. Both are
     * found when first needed, since only columns a road reaches need them.
     */
    private static final class ColumnSurfaces {
        private final RoadBlockWriter writer;
        private final ChunkPos chunkPos;
        private final int[] groundHeights = new int[16 * 16];
        private final boolean[] hasGroundHeight = new boolean[16 * 16];
        /** Per road type's settings, the surface of each column. Most chunks only see one road type. */
        private final Map<RoadSettings, RoadSurfaceConfig[]> surfaces = new IdentityHashMap<>();
        private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        ColumnSurfaces(RoadBlockWriter writer, ChunkPos chunkPos) {
            this.writer = writer;
            this.chunkPos = chunkPos;
        }

        /** The y of the column's ground block, which may be water. */
        int groundHeight(int column) {
            if (!this.hasGroundHeight[column]) {
                this.groundHeights[column] = this.writer.surfaceHeight(
                        this.chunkPos.getMinBlockX() + (column >> 4), this.chunkPos.getMinBlockZ() + (column & 15));
                this.hasGroundHeight[column] = true;
            }
            return this.groundHeights[column];
        }

        /** The road type's surface matching the column's ground block. */
        RoadSurfaceConfig surface(int column, RoadSettings settings) {
            RoadSurfaceConfig[] columnSurfaces = this.surfaces.computeIfAbsent(settings, s -> new RoadSurfaceConfig[16 * 16]);
            if (columnSurfaces[column] == null) {
                this.mutable.set(this.chunkPos.getMinBlockX() + (column >> 4), groundHeight(column), this.chunkPos.getMinBlockZ() + (column & 15));
                columnSurfaces[column] = surfaceAt(this.writer, this.mutable, settings);
            }
            return columnSurfaces[column];
        }
    }

    static boolean isInChunk(ChunkPos chunkPos, BlockPos blockPos) {
        return isInChunk(chunkPos, blockPos.getX(), blockPos.getZ());
    }

    static boolean isInChunk(ChunkPos chunkPos, int x, int z) {
        return (x >> 4) == chunkPos.x && (z >> 4) == chunkPos.z;
    }
}
