package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsroads.util.BlockLines;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSettings;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the height of a road's surface along its center line, so roads have smooth grades instead of copying every
 * bump in the terrain.
 * <p>
 * The profile is computed from sampled terrain while routing, rather than from the world while placing, so it doesn't
 * depend on which chunks have generated, or on the order roads were placed in them. Placement then levels the ground
 * to it, bridges over the ground where it falls away, or tunnels through the ground where it rises too steeply.
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

    /** Heights lowered by less than this aren't counted as lowered, so rounding error doesn't straighten the road. */
    private static final double LOWERED_EPSILON = 1e-6;

    private RoadProfile() {
    }

    /**
     * Sets the y of each of the road's center positions to its surface height: the y of the block the road is placed
     * at. Over water this is the water's surface, where the road becomes a bridge. Also records the dips the road
     * bridges across in {@link Road#landBridges}, straightening the road across each of them, and the stretches it
     * tunnels through in {@link Road#tunnels}.
     *
     * @param road A road whose center positions are set, in order. Their y is ignored.
     */
    static void apply(Road road, TerrainCache terrain, RoadSettings settings) {
        double[] ground = sampleGround(road.positions, terrain);
        List<Road.Span> dips = findDips(ground, settings.maxFillDepth, settings.maxLandBridgeLength);
        LandBridgedRoad bridged = bridgeDips(road.positions, ground, dips, settings);

        List<BlockPos> positions = bridged.positions;
        double[] unsmoothed = bridged.heights.toDoubleArray();
        double[] heights = smooth(unsmoothed, settings.smoothingRadius);
        limitCut(heights, unsmoothed, settings.maxCutDepth);
        limitGrade(heights, positions, settings.maxGrade);
        for (int i = 0; i < positions.size(); i++) {
            positions.set(i, positions.get(i).atY((int) Math.round(heights[i])));
        }
        road.positions = positions;
        road.landBridges = bridged.landBridges;
        road.tunnels = findTunnels(positions, unsmoothed, settings.maxCutDepth);
    }

    /**
     * Rebuilds the road's center line with each dip replaced by a straight land bridge between its rims, whose deck
     * sags between them. Positions outside the dips are kept as they are.
     * <p>
     * A dip's rims are where the ground comes within the max fill depth of its lower bank, which on a cliff is partway
     * down the face. Each rim is moved out to the top of any face too steep to drive, so the bridge spans from cliff top
     * to cliff top instead of leaving the road to drop down the face.
     */
    private static LandBridgedRoad bridgeDips(List<BlockPos> positions, double[] ground, List<Road.Span> dips,
                                              RoadSettings settings) {
        LandBridgedRoad bridged = new LandBridgedRoad(positions.size(), dips.size());
        int next = 0;
        for (int d = 0; d < dips.size(); d++) {
            Road.Span dip = dips.get(d);
            // A dip always has a rim on each side, since its banks are outside it. Rims may move out as far as the
            // neighboring dips' rims, and no further than the max land bridge length.
            int nextDipRim = d + 1 < dips.size() ? dips.get(d + 1).first() - 1 : positions.size() - 1;
            int fromRim = climbFace(positions, ground, dip.first() - 1, -1,
                    Math.max(next, dip.first() - 1 - settings.maxLandBridgeLength), settings.maxGrade);
            int toRim = climbFace(positions, ground, dip.last() + 1, 1,
                    Math.min(nextDipRim, dip.last() + 1 + settings.maxLandBridgeLength), settings.maxGrade);
            bridged.addOriginal(positions, ground, next, fromRim + 1);
            bridged.addLandBridge(positions.get(fromRim), ground[fromRim], positions.get(toRim), ground[toRim],
                    settings.landBridgeSag, settings.maxGrade);
            next = toRim;
        }
        bridged.addOriginal(positions, ground, next, positions.size());
        return bridged;
    }

    /**
     * Moves a rim away from its dip while the ground beyond it rises more steeply than the max grade, so it ends up at
     * the top of the face it's on.
     *
     * @param step -1 to move toward the road's start, or 1 toward its end.
     * @param limit The furthest index the rim may move to.
     * @return The rim's new index.
     */
    private static int climbFace(List<BlockPos> positions, double[] ground, int rim, int step, int limit, double maxGrade) {
        while (rim != limit) {
            int beyond = rim + step;
            if (ground[beyond] - ground[rim] <= maxGrade * horizontalDistance(positions.get(rim), positions.get(beyond))) {
                break;
            }
            rim = beyond;
        }
        return rim;
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
         * Adds a land bridge along the straight line strictly between two rims. The rims themselves aren't added.
         * <p>
         * The deck hangs between the rims in a parabola, sagging below the straight line between them by up to the sag
         * ratio of its length, but never so far that either end is steeper than the max grade.
         */
        void addLandBridge(BlockPos fromRim, double fromHeight, BlockPos toRim, double toHeight, double sagRatio,
                           double maxGrade) {
            List<BlockPos> line = BlockLines.betweenXZ(fromRim, toRim);
            if (line.isEmpty()) {
                // The rims are adjacent, so there's nothing between them to bridge
                return;
            }

            // The bridge's positions are appended to the end, so its span starts at the current size
            int firstIndex = this.positions.size();
            int lastIndex = firstIndex + line.size() - 1;
            this.landBridges.add(new Road.Span(firstIndex, lastIndex));

            // A parabola sagging by s has slope (rise ± 4s) / length at its ends
            double length = horizontalDistance(fromRim, toRim);
            double rise = toHeight - fromHeight;
            double sag = Math.max(0, Math.min(sagRatio * length, (maxGrade * length - Math.abs(rise)) / 4));

            // The line divides the bridge into equal steps, one per position plus one to reach the far rim
            for (int i = 0; i < line.size(); i++) {
                double t = (i + 1) / (double) (line.size() + 1);
                this.positions.add(line.get(i));
                this.heights.add(fromHeight + rise * t - 4 * sag * t * (1 - t));
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
        return spans(isDip);
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
     * Raises each height to no more than the max cut depth below the ground, as placement does on its own where the
     * ground rises gently.
     *
     * @param ground The ground height at each position, or the deck height along land bridges.
     */
    private static void limitCut(double[] heights, double[] ground, int maxCutDepth) {
        for (int i = 0; i < heights.length; i++) {
            heights[i] = Math.max(heights[i], ground[i] - maxCutDepth);
        }
    }

    /**
     * Lowers heights so the road is nowhere steeper than the max grade. Routing only checks the grade between lattice
     * points, so ground between them, like a narrow ridge or the top of a cliff, can leave the road steeper.
     * <p>
     * Each pass keeps the road from rising faster than the max grade in its direction, so together they lower every
     * height to the highest one within the max grade of all the others. Lowering alone would leave a peak where the
     * slopes up from either side meet, so each lowered stretch is then straightened between the heights at its ends,
     * which are within the max grade of each other. Where that runs deeper than the max cut depth, placement tunnels.
     * Straightening follows the center line, which jitter can fold back on itself, so a final pass lowers the few
     * heights around a fold that it leaves too steep.
     */
    private static void limitGrade(double[] heights, List<BlockPos> positions, double maxGrade) {
        int n = heights.length;
        double[] limited = heights.clone();
        lowerToGrade(limited, positions, maxGrade);

        int i = 0;
        while (i < n) {
            if (heights[i] - limited[i] <= LOWERED_EPSILON) {
                i++;
                continue;
            }
            int first = i;
            while (i < n && heights[i] - limited[i] > LOWERED_EPSILON) {
                i++;
            }
            // The unlowered heights on either side, unless the stretch runs off an end of the road
            int from = first - 1;
            int to = i;
            for (int k = first; k < to; k++) {
                heights[k] = from < 0 || to >= n
                        ? limited[k]
                        : Mth.lerp((k - from) / (double) (to - from), heights[from], heights[to]);
            }
        }
        lowerToGrade(heights, positions, maxGrade);
    }

    /**
     * Lowers each height to no more than the max grade above any height within {@link #GRADE_REACH} positions of it.
     */
    private static void lowerToGrade(double[] heights, List<BlockPos> positions, double maxGrade) {
        int n = heights.length;
        for (int i = 0; i < n; i++) {
            for (int j = Math.max(0, i - GRADE_REACH); j < i; j++) {
                lowerToGrade(heights, positions, i, j, maxGrade);
            }
        }
        for (int i = n - 1; i >= 0; i--) {
            for (int j = i + 1; j <= Math.min(n - 1, i + GRADE_REACH); j++) {
                lowerToGrade(heights, positions, i, j, maxGrade);
            }
        }
    }

    /**
     * Lowers the height at the first index so it's no more than the max grade above the height at the second.
     */
    private static void lowerToGrade(double[] heights, List<BlockPos> positions, int i, int j, double maxGrade) {
        double run = horizontalDistance(positions.get(i), positions.get(j));
        heights[i] = Math.min(heights[i], heights[j] + maxGrade * run);
    }

    /**
     * Finds the stretches where the road runs further below the sampled ground than the max cut depth, which placement
     * tunnels through.
     *
     * @param ground The ground height at each position, or the deck height along land bridges.
     */
    private static List<Road.Span> findTunnels(List<BlockPos> positions, double[] ground, int maxCutDepth) {
        boolean[] isTunnel = new boolean[positions.size()];
        for (int i = 0; i < isTunnel.length; i++) {
            isTunnel[i] = ground[i] - positions.get(i).getY() > maxCutDepth;
        }
        return spans(isTunnel);
    }

    /**
     * Groups the indices that are set into spans of consecutive indices, in order.
     */
    private static List<Road.Span> spans(boolean[] isSet) {
        List<Road.Span> spans = new ArrayList<>();
        for (int i = 0; i < isSet.length; i++) {
            if (!isSet[i]) {
                continue;
            }
            int first = i;
            while (i + 1 < isSet.length && isSet[i + 1]) {
                i++;
            }
            spans.add(new Road.Span(first, i));
        }
        return spans;
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
