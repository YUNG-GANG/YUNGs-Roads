package com.yungnickyoung.minecraft.yungsroads.util;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws straight lines of blocks on the horizontal plane.
 */
public final class BlockLines {
    private BlockLines() {
    }

    /**
     * The block positions strictly between two positions, along the straight line from one to the other. Each step
     * moves one block along x or z, choosing whichever keeps closer to the line, so the positions are a block apart and
     * connected edge to edge. The positions take the first position's y; the second's is ignored.
     */
    public static List<BlockPos> betweenXZ(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int xStep = Integer.signum(dx);
        int zStep = Integer.signum(dz);
        int stepCount = Math.abs(dx) + Math.abs(dz);

        List<BlockPos> line = new ArrayList<>(Math.max(stepCount - 1, 0));
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
