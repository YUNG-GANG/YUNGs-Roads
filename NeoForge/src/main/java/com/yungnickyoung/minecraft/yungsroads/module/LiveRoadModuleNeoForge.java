package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Hooks up {@link LiveRoadPlacer}, which re-places roads in generated chunks after road settings change.
 * Only used in debug mode.
 */
public class LiveRoadModuleNeoForge {
    public static void init() {
        if (!YungsRoadsCommon.DEBUG_MODE) {
            return;
        }

        NeoForge.EVENT_BUS.addListener(LiveRoadModuleNeoForge::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(LiveRoadModuleNeoForge::onLevelTick);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && event.getChunk() instanceof LevelChunk chunk) {
            LiveRoadPlacer placer = ((IStructureRegionCacheProvider) serverLevel).getLiveRoadPlacer();
            if (placer != null) {
                placer.onChunkLoad(chunk);
            }
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            LiveRoadPlacer placer = ((IStructureRegionCacheProvider) serverLevel).getLiveRoadPlacer();
            if (placer != null) {
                placer.tick();
            }
        }
    }
}
