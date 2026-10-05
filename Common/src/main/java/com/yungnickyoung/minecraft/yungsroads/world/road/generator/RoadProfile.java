package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the height of a road's surface along its center line, so roads have smooth grades instead of copying every
 * bump in the terrain.
 * <p>
 * The profile is computed from sampled terrain while routing, rather than from the world while placing, so it doesn't
 * depend on which chunks have generated, or on the order roads were placed in them. Placement then levels the ground
 * to it, or bridges over the ground where it falls away.
 */
final class RoadProfile {
    /**
     * The ground is sampled at every this many center positions, and interpolated between them. Positions are about a
     * block apart, so this is much finer than smoothing needs, and sampling is the profile's main cost.
     */
    private static final int SAMPLE_SPACING = 2;

    private RoadProfile() {
    }

    /**
     * Sets the y of each of the road's center positions to its surface height: the y of the block the road is placed
     * at. Over water this is the water's surface, where the road becomes a bridge. Also records the dips the road
     * bridges across in {@link Road#landBridges}, and straightens the road across each of them.
     *
     * @param road A road whose center positions are set, in order. Their y is ignored.
     */
    static void apply(Road road, TerrainCache terrain, ConfigModule.Advanced settings) {
        double[] heights = sampleGround(road.positions, terrain);
        List<Road.Span> dips = findDips(heights, settings.maxFillDepth, settings.maxLandBridgeLength);
        LandBridgedRoad bridged = bridgeDips(road.positions, heights, dips);

        double[] smoothed = smooth(bridged.heights.toDoubleArray(), settings.smoothingRadius);
        List<BlockPos> positions = bridged.positions;
        for (int i = 0; i < positions.size(); i++) {
            positions.set(i, positions.get(i).atY((int) Math.round(smoothed[i])));
        }
        road.positions = positions;
        road.landBridges = bridged.landBridges;
    }

    /**
     * Rebuilds the road's center line with each dip replaced by a straight land bridge between its rims, at heights
     * rising or falling evenly between them. Positions outside the dips are kept as they are.
     */
    private static LandBridgedRoad bridgeDips(List<BlockPos> positions, double[] heights, List<Road.Span> dips) {
        LandBridgedRoad bridged = new LandBridgedRoad(positions.size(), dips.size());
        int next = 0;
        for (Road.Span dip : dips) {
            // A dip always has a rim on each side, since its banks are outside it
            int fromRim = dip.first() - 1;
            int toRim = dip.last() + 1;
            bridged.addOriginal(positions, heights, next, dip.first());
            bridged.addLandBridge(positions.get(fromRim), heights[fromRim], positions.get(toRim), heights[toRim]);
            next = toRim;
        }
        bridged.addOriginal(positions, heights, next, positions.size());
        return bridged;
    }

    /**
     * A road's center line and ground heights, built up in order from start to end, with the land bridges along it.
     */
    private static final class LandBridgedRoad {
        final List<BlockPos> positions;
        final DoubleList heights;
        final List<Road.Span> landBridges;

        LandBridgedRoad(int expectedSize, int expectedLandBridges) {
            this.positions = new ArrayList<>(expectedSize);
            this.heights = new DoubleArrayList(expectedSize);
            this.landBridges = new ArrayList<>(expectedLandBridges);
        }

        /**
         * Adds the original positions from the first index, inclusive, to the last, exclusive, with their heights.
         */
        void addOriginal(List<BlockPos> positions, double[] heights, int from, int to) {
            for (int i = from; i < to; i++) {
                this.positions.add(positions.get(i));
                this.heights.add(heights[i]);
            }
        }

        /**
         * Adds a land bridge along the straight line strictly between two rims, at heights changing evenly from one
         * rim's to the other's. The rims themselves aren't added.
         */
        void addLandBridge(BlockPos fromRim, double fromHeight, BlockPos toRim, double toHeight) {
            List<BlockPos> line = lineBetween(fromRim, toRim);
            if (line.isEmpty()) {
                // The rims are adjacent, so there's nothing between them to bridge
                return;
            }

            // The bridge's positions are appended to the end, so its span starts at the current size
            int firstIndex = this.positions.size();
            int lastIndex = firstIndex + line.size() - 1;
            this.landBridges.add(new Road.Span(firstIndex, lastIndex));

            // The line divides the rise from one rim to the other into equal steps, one per position plus one to
            // reach the far rim
            double heightStep = (toHeight - fromHeight) / (line.size() + 1);
            for (int i = 0; i < line.size(); i++) {
                this.positions.add(line.get(i));
                this.heights.add(fromHeight + heightStep * (i + 1));
            }
        }
    }

    /**
     * Samples the y of the top ground block at each position, or of the water's surface over water.
     */
    private static double[] sampleGround(List<BlockPos> positions, TerrainCache terrain) {
        int n = positions.size();
        double[] heights = new double[n];
        // The sea fills up to the block below sea level
        int waterSurface = terrain.seaLevel() - 1;
        if (n == 0) {
            return heights;
        }
        // The last position is always sampled, so there's no need to extrapolate
        int prevSample = -1;
        for (int sample = 0; ; sample = Math.min(sample + SAMPLE_SPACING, n - 1)) {
            BlockPos pos = positions.get(sample);
            heights[sample] = Math.max(Math.floor(terrain.exactSurfaceHeightAtBlock(pos.getX(), pos.getZ())), waterSurface);
            interpolate(heights, prevSample, sample);
            if (sample == n - 1) {
                return heights;
            }
            prevSample = sample;
        }
    }

    /**
     * Finds dips in the terrain deeper than the max fill depth, with banks no further apart than the max length.
     * Placement bridges over each dip rather than descending into it. Dips in the sampled terrain are gullies and cave
     * openings, since carved ravines and caves aren't sampled.
     *
     * @return The dips, as ranges of indices, in order. Each has a rim on both sides, so none includes either end.
     */
    private static List<Road.Span> findDips(double[] heights, int maxFillDepth, int maxLength) {
        int n = heights.length;
        boolean[] isDip = new boolean[n];
        for (int i = 0; i < n; i++) {
            // The point is in a dip if there's higher ground on both sides, within the max length of each other
            double leftBank = Double.NEGATIVE_INFINITY;
            for (int j = Math.max(0, i - maxLength); j < i; j++) {
                leftBank = Math.max(leftBank, heights[j]);
            }
            double rightBank = Double.NEGATIVE_INFINITY;
            for (int j = i + 1; j <= Math.min(n - 1, i + maxLength); j++) {
                rightBank = Math.max(rightBank, heights[j]);
            }
            // Gap counts the empty blocks between the ground and a road at the lower bank's height
            double gap = Math.min(leftBank, rightBank) - heights[i] - 1;
            isDip[i] = gap > maxFillDepth;
        }

        // Group consecutive dips into spans, which are the dips the road bridges over
        List<Road.Span> dips = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!isDip[i]) {
                continue;
            }
            int first = i;
            while (i + 1 < n && isDip[i + 1]) {
                i++;
            }
            dips.add(new Road.Span(first, i));
        }
        return dips;
    }

    /**
     * Averages each height with those within the radius on either side, using fewer near the ends of the road.
     */
    private static double[] smooth(double[] heights, int radius) {
        int n = heights.length;
        double[] prefixSums = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefixSums[i + 1] = prefixSums[i] + heights[i];
        }
        double[] smoothed = new double[n];
        for (int i = 0; i < n; i++) {
            int from = Math.max(0, i - radius);
            int to = Math.min(n - 1, i + radius);
            smoothed[i] = (prefixSums[to + 1] - prefixSums[from]) / (to - from + 1);
        }
        return smoothed;
    }

    /**
     * Sets the values strictly between two indices to a straight line between the values at those indices.
     * Does nothing if the first index is negative.
     */
    private static void interpolate(double[] values, int from, int to) {
        if (from < 0) {
            return;
        }
        for (int i = from + 1; i < to; i++) {
            double t = (i - from) / (double) (to - from);
            values[i] = values[from] + (values[to] - values[from]) * t;
        }
    }

    /**
     * The block positions strictly between two positions, along the straight line from one to the other. Each step
     * moves one block along x or z, choosing whichever keeps closer to the line, so the positions are about a block
     * apart and connected edge to edge, like the rest of a road's center line. The positions' y is ignored.
     */
    private static List<BlockPos> lineBetween(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int xStep = Integer.signum(dx);
        int zStep = Integer.signum(dz);
        int stepCount = Math.abs(dx) + Math.abs(dz);

        List<BlockPos> line = new ArrayList<>();
        // The walk's current offset from the start
        int x = 0;
        int z = 0;
        // The final step would land on the end position, which is excluded
        for (int i = 0; i < stepCount - 1; i++) {
            boolean stepAlongX;
            if (x == dx) {
                stepAlongX = false;
            } else if (z == dz) {
                stepAlongX = true;
            } else {
                stepAlongX = distanceFromLine(x + xStep, z, dx, dz) <= distanceFromLine(x, z + zStep, dx, dz);
            }

            if (stepAlongX) {
                x += xStep;
            } else {
                z += zStep;
            }
            line.add(from.offset(x, 0, z));
        }
        return line;
    }

    /**
     * How far an offset from a line's start is from the line through the start with the given direction, scaled by
     * the direction's length. Only meaningful for comparing offsets against the same line.
     */
    private static long distanceFromLine(int x, int z, int dx, int dz) {
        // The magnitude of the cross product of the offset and the direction
        return Math.abs((long) x * dz - (long) z * dx);
    }
}
