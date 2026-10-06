package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.util.BlockLines;
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

    /**
     * How many positions on each side each height is checked against for the max grade. Jitter can fold the center line
     * back on itself for a few positions, so the grade is measured over straight distances rather than along the line,
     * and far enough to span a fold. Beyond that the line runs roughly straight, so the grade carries over.
     */
    private static final int GRADE_REACH = 8;

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

        double[] unsmoothed = bridged.heights.toDoubleArray();
        double[] smoothed = smooth(unsmoothed, settings.smoothingRadius);
        limitCut(smoothed, unsmoothed, settings.maxCutDepth);
        List<BlockPos> positions = bridged.positions;
        limitGrade(smoothed, positions, settings.maxGrade);
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
            List<BlockPos> line = BlockLines.betweenXZ(fromRim, toRim);
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
     * Raises each height to no more than the max cut depth below the ground, as placement does, so the grade limit
     * accounts for the road rising with the ground where smoothing would cut too deep.
     *
     * @param ground The ground height at each position, or the deck height along land bridges.
     */
    private static void limitCut(double[] heights, double[] ground, int maxCutDepth) {
        for (int i = 0; i < heights.length; i++) {
            heights[i] = Math.max(heights[i], ground[i] - maxCutDepth);
        }
    }

    /**
     * Raises heights so the road is nowhere steeper than the max grade. Routing only checks the grade between lattice
     * points, so ground between them, like a cliff above a river or a narrow ridge, can leave the road steeper. Each
     * pass keeps the road from falling faster than the max grade in its direction, so together they raise every height
     * to the lowest one within the max grade of all the others. Raised stretches end up above the ground, where
     * placement fills under them or carries them on a bridge.
     */
    private static void limitGrade(double[] heights, List<BlockPos> positions, double maxGrade) {
        int n = heights.length;
        for (int i = 0; i < n; i++) {
            for (int j = Math.max(0, i - GRADE_REACH); j < i; j++) {
                raiseToGrade(heights, positions, i, j, maxGrade);
            }
        }
        for (int i = n - 1; i >= 0; i--) {
            for (int j = i + 1; j <= Math.min(n - 1, i + GRADE_REACH); j++) {
                raiseToGrade(heights, positions, i, j, maxGrade);
            }
        }
    }

    /**
     * Raises the height at the first index so it's no more than the max grade below the height at the second.
     */
    private static void raiseToGrade(double[] heights, List<BlockPos> positions, int i, int j, double maxGrade) {
        double run = horizontalDistance(positions.get(i), positions.get(j));
        heights[i] = Math.max(heights[i], heights[j] - maxGrade * run);
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
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
}
