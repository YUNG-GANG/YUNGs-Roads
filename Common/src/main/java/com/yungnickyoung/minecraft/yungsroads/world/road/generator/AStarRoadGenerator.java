package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.util.BlockLines;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * Generates roads routed by {@link LatticePathfinder}. The route's nodes are joined into a center line of block
 * positions, which is jittered so the road looks less straight, and whose heights are set by {@link RoadProfile}.
 * <p>
 * Steps that turn out to cross water too narrow for the lattice to see, such as streams, are made bridges after
 * routing, so that every water crossing is straight.
 */
public class AStarRoadGenerator extends AbstractRoadGenerator {
    /** How far apart, in blocks, the jittered center line is sampled between nodes. */
    private static final double JITTER_SAMPLE_SPACING = 3;

    @Override
    public Optional<Road> generateRoad(BlockPos pos1, BlockPos pos2, RoadTypes.Choice roadType, TerrainCache terrain) {
        Road road = new Road(pos1, pos2, roadType);
        Optional<LatticePathfinder.Path> path = LatticePathfinder.findPath(road.getStartPos(), road.getEndPos(), terrain, road.settings);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        road.nodes.addAll(path.get().nodes());

        boolean[] isBridgeSegment = addBridges(road, path.get().bridgeSegments(), terrain);

        // The noise is created per road since its seed is mutable state and roads may be generated concurrently
        addCenterLine(road, isBridgeSegment, createJitterNoise(road.getStartPos()));

        RoadProfile.apply(road, terrain, road.settings);
        return Optional.of(road);
    }

    /**
     * Records the road's water crossings in {@link Road#bridges}: the bridges routing found, plus any step that crosses
     * water too narrow for a lattice point to land in, such as a stream.
     *
     * @param routedBridges The segments routing bridged, set at the index of the node each starts from.
     * @return Whether each segment, from node n to node n + 1, is a bridge.
     */
    private static boolean[] addBridges(Road road, BitSet routedBridges, TerrainCache terrain) {
        boolean[] isBridgeSegment = new boolean[road.nodes.size() - 1];
        for (int n = 0; n < isBridgeSegment.length; n++) {
            BlockPos from = road.nodes.get(n).rawPos;
            BlockPos to = road.nodes.get(n + 1).rawPos;
            isBridgeSegment[n] = routedBridges.get(n) || LatticePathfinder.crossesWater(from, to, terrain);
            if (isBridgeSegment[n]) {
                road.bridges.add(new Road.Bridge(atDeckHeight(from, terrain), atDeckHeight(to, terrain)));
            }
        }
        return isBridgeSegment;
    }

    /**
     * Fills in {@link Road#positions} with the road's center line, in order: the straight lines between consecutive
     * nodes, shifted sideways by noise so the road looks less straight and more natural. Each node's shifted position
     * is recorded as its jittered position.
     * <p>
     * The shift varies continuously along the road, so the line has no kinks at nodes. Its direction is blended from
     * one node's to the next, each node's being perpendicular to the average direction of the segments on either side
     * of it. It fades out toward bridge ends, so bridges stay straight and land where routing checked for dry ground.
     * <p>
     * The shifted line is sampled every {@link #JITTER_SAMPLE_SPACING} blocks, and the samples are joined by straight
     * lines of blocks. Rounding each block's shift on its own would leave the line stepping back and forth sideways.
     */
    private static void addCenterLine(Road road, boolean[] isBridgeSegment, FastNoise jitter) {
        int nodeCount = road.nodes.size();
        double[] normalX = new double[nodeCount];
        double[] normalZ = new double[nodeCount];
        double[] weight = new double[nodeCount];
        for (int n = 0; n < nodeCount; n++) {
            double[] incoming = directionToNeighbor(road, n, -1);
            double[] outgoing = directionToNeighbor(road, n, 1);
            double tangentX = incoming[0] + outgoing[0];
            double tangentZ = incoming[1] + outgoing[1];
            double length = Math.sqrt(tangentX * tangentX + tangentZ * tangentZ);
            if (length < 1e-6) {
                // The road doubles straight back on itself here, so there's no side to shift toward
                continue;
            }
            normalX[n] = tangentZ / length;
            normalZ[n] = -tangentX / length;
            boolean isBridgeEnd = (n > 0 && isBridgeSegment[n - 1]) || (n < nodeCount - 1 && isBridgeSegment[n]);
            weight[n] = isBridgeEnd ? 0 : 1;
        }

        for (int n = 0; n < nodeCount; n++) {
            Road.DebugNode node = road.nodes.get(n);
            node.jitteredPos = shifted(jitter, road.settings.jitterAmount, node.rawPos, node.rawPos.getX(), node.rawPos.getZ(),
                    normalX[n], normalZ[n], weight[n]);
            addConnected(road.positions, node.jitteredPos);
            if (n == nodeCount - 1 || isBridgeSegment[n]) {
                // A bridge is the straight line between its unshifted ends, which the next node connects to
                continue;
            }
            BlockPos next = road.nodes.get(n + 1).rawPos;
            double length = Math.sqrt(horizontalDistSqr(node.rawPos, next));
            int pieces = Math.max(1, (int) Math.round(length / JITTER_SAMPLE_SPACING));
            for (int k = 1; k < pieces; k++) {
                double t = k / (double) pieces;
                // The blended direction isn't renormalized, so where the nodes' directions differ a lot the shift
                // shrinks between them instead of swinging abruptly from one side to the other
                addConnected(road.positions, shifted(jitter, road.settings.jitterAmount, node.rawPos,
                        Mth.lerp(t, node.rawPos.getX(), next.getX()),
                        Mth.lerp(t, node.rawPos.getZ(), next.getZ()),
                        Mth.lerp(t, normalX[n], normalX[n + 1]),
                        Mth.lerp(t, normalZ[n], normalZ[n + 1]),
                        Mth.lerp(t, weight[n], weight[n + 1])));
            }
        }
    }

    /**
     * Shifts a point on the unjittered center line sideways by the noise there, and rounds it to a block position.
     *
     * @param jitterAmount The furthest the point may be shifted, in blocks.
     * @param base The position whose y the result takes.
     */
    private static BlockPos shifted(FastNoise jitter, double jitterAmount, BlockPos base, double x, double z,
                                    double normalX, double normalZ, double weight) {
        double offset = jitter.GetNoise((float) x, (float) z) * jitterAmount * weight;
        return new BlockPos((int) Math.round(x + normalX * offset), base.getY(), (int) Math.round(z + normalZ * offset));
    }

    /**
     * Adds a position to the end of the center line, along with the straight line of positions connecting it to the
     * previous one. A position the same as the previous one isn't added again.
     */
    private static void addConnected(List<BlockPos> positions, BlockPos pos) {
        if (!positions.isEmpty()) {
            BlockPos previous = positions.get(positions.size() - 1);
            if (previous.getX() == pos.getX() && previous.getZ() == pos.getZ()) {
                return;
            }
            positions.addAll(BlockLines.betweenXZ(previous, pos));
        }
        positions.add(pos);
    }

    /**
     * The x-z unit vector along the road at a node, toward or from its nearest neighbor on one side that isn't at the
     * same x-z position, or zero if there's none. A road's endpoints can coincide with the lattice points next to them.
     *
     * @param side -1 for the direction from the previous neighbor, or 1 for the direction to the next.
     */
    private static double[] directionToNeighbor(Road road, int n, int side) {
        BlockPos pos = road.nodes.get(n).rawPos;
        for (int m = n + side; m >= 0 && m < road.nodes.size(); m += side) {
            BlockPos neighbor = road.nodes.get(m).rawPos;
            if (neighbor.getX() != pos.getX() || neighbor.getZ() != pos.getZ()) {
                return side > 0 ? unitDirection(pos, neighbor) : unitDirection(neighbor, pos);
            }
        }
        return new double[2];
    }

    /**
     * The x-z unit vector pointing from one position toward another, which must differ in x or z.
     */
    private static double[] unitDirection(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        return new double[]{dx / length, dz / length};
    }

    private static double horizontalDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * The position at the height a bridge's deck would have there. Road endpoints come from structure locations, which
     * don't have a meaningful height.
     */
    private static BlockPos atDeckHeight(BlockPos pos, TerrainCache terrain) {
        double surfaceHeight = terrain.surfaceHeightAtBlock(pos.getX(), pos.getZ());
        return pos.atY((int) Math.round(LatticePathfinder.roadHeight(surfaceHeight, terrain.seaLevel())));
    }

    private static FastNoise createJitterNoise(BlockPos roadStartPos) {
        FastNoise jitter = new FastNoise();
        jitter.SetNoiseType(FastNoise.NoiseType.Simplex);
        jitter.SetFrequency(.05f);
        jitter.SetFractalOctaves(1);
        jitter.SetSeed(roadStartPos.getX() * 1000 + roadStartPos.getZ());
        return jitter;
    }
}
