package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Hooks up {@link LiveRoadPlacer}, which re-places roads in generated chunks after road settings change.
 * Only used in debug mode.
 */
public class LiveRoadModuleFabric {
    public static void init() {
        if (!YungsRoadsCommon.DEBUG_MODE) {
            return;
        }

        ServerChunkEvents.CHUNK_LOAD.register((serverLevel, chunk) -> {
            LiveRoadPlacer placer = ((IStructureRegionCacheProvider) serverLevel).getLiveRoadPlacer();
            if (placer != null) {
                placer.onChunkLoad(chunk);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(serverLevel -> {
            LiveRoadPlacer placer = ((IStructureRegionCacheProvider) serverLevel).getLiveRoadPlacer();
            if (placer != null) {
                placer.tick();
            }
        });
    }
}
