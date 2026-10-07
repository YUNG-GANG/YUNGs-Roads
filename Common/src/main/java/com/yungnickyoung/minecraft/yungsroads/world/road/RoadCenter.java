package com.yungnickyoung.minecraft.yungsroads.world.road;

import net.minecraft.core.BlockPos;

/**
 * One of a road's center positions, with the road it's on, so placement can use that road's settings.
 */
public record RoadCenter(BlockPos pos, Road road) {
}
