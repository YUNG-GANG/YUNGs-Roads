package com.yungnickyoung.minecraft.yungsroads.world.road;

import net.minecraft.util.math.ChunkPos;

public interface IRoadGenerator {
    Road generate(ChunkPos pos1, ChunkPos pos2);
}
