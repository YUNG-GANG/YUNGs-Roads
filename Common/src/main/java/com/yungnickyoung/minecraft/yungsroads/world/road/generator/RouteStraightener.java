package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Straightens a route found by {@link LatticePathfinder} by cutting out nodes the road can bypass in a straight line.
 * <p>
 * Routes can only head in the lattice's 16 move directions, and on flat ground many routes cost about the same, so
 * the search's route often zigzags where a straight road would do. A shortcut replaces the nodes between two others
 * when it costs no more than the route it replaces, is nowhere steeper than the max grade, and stays out of water.
 * Switchbacks up a slope survive, since cutting across them would be steeper and cost more.
 */
final class RouteStraightener {
    /** The most nodes a single shortcut may bypass. */
    private static final int MAX_SHORTCUT_NODES = 16;

    /** Allows for rounding error when comparing a shortcut's cost with the route's. */
    private static final double COST_EPSILON = 1e-6;

    private RouteStraightener() {
    }

    /**
     * Returns the route with every node the road can bypass removed. Starting from the first node, each next node kept
     * is the furthest one a shortcut reaches. Bridges are kept as they are.
     */
    static LatticePathfinder.Path straighten(LatticePathfinder.Path path, TerrainCache terrain,
                                             ConfigModule.Advanced settings) {
        List<Road.DebugNode> nodes = path.nodes();
        BitSet bridgeSegments = path.bridgeSegments();
        List<Road.DebugNode> kept = new ArrayList<>(nodes.size());
        BitSet keptBridgeSegments = new BitSet();
        kept.add(nodes.get(0));

        int from = 0;
        while (from < nodes.size() - 1) {
            int to = from + 1;
            if (bridgeSegments.get(from)) {
                keptBridgeSegments.set(kept.size() - 1);
            } else {
                // Shortcuts can't skip over a bridge, so they end at the next bridge's start at the furthest
                int nextBridge = bridgeSegments.nextSetBit(from);
                int furthest = Math.min(from + MAX_SHORTCUT_NODES, nextBridge < 0 ? nodes.size() - 1 : nextBridge);
                for (int candidate = furthest; candidate > from + 1; candidate--) {
                    if (isShortcut(nodes.get(from), nodes.get(candidate), terrain, settings)) {
                        to = candidate;
                        break;
                    }
                }
            }
            kept.add(nodes.get(to));
            from = to;
        }
        return new LatticePathfinder.Path(kept, keptBridgeSegments);
    }

    /**
     * Whether the road can run straight between two nodes of the route: the straight line costs no more than the route
     * between them, is nowhere steeper than the max grade, and doesn't cross water.
     * <p>
     * The line is measured in pieces about a node step long, with heights interpolated between the lattice points
     * around them, like the moves routing measures.
     */
    private static boolean isShortcut(Road.DebugNode from, Road.DebugNode to, TerrainCache terrain,
                                      ConfigModule.Advanced settings) {
        BlockPos start = from.rawPos;
        BlockPos end = to.rawPos;
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        int pieces = Math.max(1, (int) Math.ceil(length / terrain.step()));
        double pieceLength = length / pieces;
        double routeCost = to.g - from.g;

        double cost = 0;
        double previousHeight = roadHeightAt(start.getX(), start.getZ(), terrain);
        for (int piece = 1; piece <= pieces; piece++) {
            double t = piece / (double) pieces;
            double height = roadHeightAt(start.getX() + dx * t, start.getZ() + dz * t, terrain);
            if (Double.isNaN(height)) {
                return false;
            }
            double grade = Math.abs(height - previousHeight) / pieceLength;
            if (grade > settings.maxGrade) {
                return false;
            }
            cost += pieceLength * LatticePathfinder.slopeCostFactor(grade, settings);
            if (cost > routeCost + COST_EPSILON) {
                return false;
            }
            previousHeight = height;
        }
        // Checked last, since it samples the terrain at every block
        return !LatticePathfinder.crossesWater(start, end, terrain);
    }

    /**
     * The road height at a block position, interpolated between the lattice points around it, or NaN if any of them
     * is impassable or water.
     */
    private static double roadHeightAt(double blockX, double blockZ, TerrainCache terrain) {
        double latticeX = blockX / terrain.step();
        double latticeZ = blockZ / terrain.step();
        int x = Mth.floor(latticeX);
        int z = Mth.floor(latticeZ);
        double[] corners = {
                terrain.heightAt(x, z), terrain.heightAt(x + 1, z),
                terrain.heightAt(x, z + 1), terrain.heightAt(x + 1, z + 1)
        };
        for (int c = 0; c < corners.length; c++) {
            if (Double.isNaN(corners[c]) || LatticePathfinder.isWater(corners[c], terrain.seaLevel())) {
                return Double.NaN;
            }
            corners[c] = LatticePathfinder.roadHeight(corners[c], terrain.seaLevel());
        }
        return Mth.lerp2(latticeX - x, latticeZ - z, corners[0], corners[1], corners[2], corners[3]);
    }
}
