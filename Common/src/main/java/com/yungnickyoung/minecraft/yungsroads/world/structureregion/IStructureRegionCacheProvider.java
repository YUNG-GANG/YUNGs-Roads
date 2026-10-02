package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;

import javax.annotation.Nullable;

public interface IStructureRegionCacheProvider {
    StructureRegionCache getStructureRegionCache();

    /** The level's live road placer, which only exists in debug mode. */
    @Nullable
    LiveRoadPlacer getLiveRoadPlacer();
}
