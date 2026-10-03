package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
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
     * bridges across in {@link Road#landBridges}.
     *
     * @param road A road whose center positions are set, in order. Their y is ignored.
     */
    static void apply(Road road, TerrainCache terrain, ConfigModule.Advanced settings) {
        double[] heights = sampleGround(road.positions, terrain);
        road.landBridges = bridgeDips(heights, settings.maxFillDepth, settings.maxLandBridgeLength);
        double[] smoothed = smooth(heights, settings.smoothingRadius);

        List<BlockPos> positions = new ArrayList<>(road.positions.size());
        for (int i = 0; i < road.positions.size(); i++) {
            positions.add(road.positions.get(i).atY((int) Math.round(smoothed[i])));
        }
        road.positions = positions;
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
     * Finds dips in the terrain deeper than the max fill depth, with banks no further apart than the max length, and
     * replaces them with a straight line between their rims. Placement then bridges over the dip rather than descending
     * into it. Dips in the sampled terrain are gullies and cave openings, since carved ravines and caves aren't sampled.
     *
     * @return The dips, as ranges of indices.
     */
    private static List<Road.Span> bridgeDips(double[] heights, int maxFillDepth, int maxLength) {
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

        List<Road.Span> dips = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!isDip[i]) {
                continue;
            }
            int first = i;
            while (i + 1 < n && isDip[i + 1]) {
                i++;
            }
            // A dip always has a rim on each side, since its banks are outside it
            interpolate(heights, first - 1, i + 1);
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
}
