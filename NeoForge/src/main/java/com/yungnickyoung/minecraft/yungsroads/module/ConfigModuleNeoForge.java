package com.yungnickyoung.minecraft.yungsroads.module;


import com.google.common.collect.Lists;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.config.YRConfigNeoForge;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigModuleNeoForge {
    public static void init(IEventBus eventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, YRConfigNeoForge.SPEC, "YungsRoads-neoforge-1_21.toml");
        NeoForge.EVENT_BUS.addListener(ConfigModuleNeoForge::onWorldLoad);
        eventBus.addListener(ConfigModuleNeoForge::onConfigChange);
    }

    private static void onWorldLoad(LevelEvent.Load event) {
        bakeConfig();
        YungsRoadsCommon.CONFIG.general.structures = parseStructureStringList(
                YRConfigNeoForge.general.structures.get(),
                event.getLevel());
        if (event.getLevel() instanceof IStructureRegionCacheProvider stuctureRegionCacheProvider) {
            stuctureRegionCacheProvider
                    .getStructureRegionCache()
                    .getStructureRegionGenerator()
                    .setEndpointStructures(YungsRoadsCommon.CONFIG.general.structures);
        }
    }

    private static void onConfigChange(ModConfigEvent event) {
        if (event.getConfig().getSpec() == YRConfigNeoForge.SPEC) {
            bakeConfig();
        }
    }

    private static void bakeConfig() {
        YungsRoadsCommon.CONFIG.general.structuresString = YRConfigNeoForge.general.structures.get();

        YungsRoadsCommon.CONFIG.advanced.path.nodeStepDistance = YRConfigNeoForge.advanced.path.nodeStepDistance.get();
        YungsRoadsCommon.CONFIG.advanced.path.jitterAmount = YRConfigNeoForge.advanced.path.jitterAmount.get();
        YungsRoadsCommon.CONFIG.advanced.path.hScalar = YRConfigNeoForge.advanced.path.hScalar.get();
        YungsRoadsCommon.CONFIG.advanced.path.pathScalar = YRConfigNeoForge.advanced.path.pathScalar.get();
        YungsRoadsCommon.CONFIG.advanced.path.highSlopeFactorScalar = YRConfigNeoForge.advanced.path.highSlopeFactorScalar.get();
        YungsRoadsCommon.CONFIG.advanced.path.lowSlopeFactorScalar = YRConfigNeoForge.advanced.path.lowSlopeFactorScalar.get();
        YungsRoadsCommon.CONFIG.advanced.path.slopeFactorThreshold = YRConfigNeoForge.advanced.path.slopeFactorThreshold.get();
        YungsRoadsCommon.CONFIG.advanced.path.altitudePunishmentScalar = YRConfigNeoForge.advanced.path.altitudePunishment.get();

        YungsRoadsCommon.CONFIG.advanced.segment.nodeStepDistanceProportion = YRConfigNeoForge.advanced.segment.segmentStepDistanceProportion.get();
        YungsRoadsCommon.CONFIG.advanced.segment.hScalar = YRConfigNeoForge.advanced.segment.hScalar.get();
        YungsRoadsCommon.CONFIG.advanced.segment.pathScalar = YRConfigNeoForge.advanced.segment.pathScalar.get();
        YungsRoadsCommon.CONFIG.advanced.segment.highSlopeFactorScalar = YRConfigNeoForge.advanced.segment.highSlopeFactorScalar.get();
        YungsRoadsCommon.CONFIG.advanced.segment.lowSlopeFactorScalar = YRConfigNeoForge.advanced.segment.lowSlopeFactorScalar.get();
        YungsRoadsCommon.CONFIG.advanced.segment.slopeFactorThreshold = YRConfigNeoForge.advanced.segment.slopeFactorThreshold.get();
        YungsRoadsCommon.CONFIG.advanced.segment.altitudePunishmentScalar = YRConfigNeoForge.advanced.segment.altitudePunishment.get();

        YungsRoadsCommon.CONFIG.debug.enableDebugMap = YRConfigNeoForge.debug.enableDebugMap.get();
        YungsRoadsCommon.CONFIG.debug.enableExtraDebugF3Info = YRConfigNeoForge.debug.enableExtraDebugF3Info.get();
        YungsRoadsCommon.CONFIG.debug.placeUnjitteredPosDebugMarkers = YRConfigNeoForge.debug.placeUnjitteredPosDebugMarkers.get();
        YungsRoadsCommon.CONFIG.debug.placeJitteredPosDebugMarkers = YRConfigNeoForge.debug.placeJitteredPosDebugMarkers.get();
        YungsRoadsCommon.CONFIG.debug.placeRoadEndpointDebugMarkers = YRConfigNeoForge.debug.placeRoadEndpointDebugMarkers.get();
        YungsRoadsCommon.CONFIG.debug.placeRoadSegmentEndpointDebugMarkers = YRConfigNeoForge.debug.placeRoadSegmentEndpointDebugMarkers.get();
        YungsRoadsCommon.CONFIG.debug.placeStraightDebugLine = YRConfigNeoForge.debug.placeStraightDebugLine.get();
        YungsRoadsCommon.CONFIG.debug.placeDebugPaths = YRConfigNeoForge.debug.placeDebugPaths.get();
    }

    private static HolderSet<Structure> parseStructureStringList(String listString, LevelAccessor levelAccessor) {
        int strLen = listString.length();

        List<String> listOfStructuresAsStrings = new ArrayList<>();

        if (strLen < 2 || listString.charAt(0) != '[' || listString.charAt(strLen - 1) != ']') {
            // Invalid string. Use default.
            YungsRoadsCommon.LOGGER.error("INVALID VALUE FOR SETTING 'Valid Structures'. Using [#minecraft:village] instead...");
            listOfStructuresAsStrings.add("#minecraft:village");
        } else {
            // Parse valid string.
            listOfStructuresAsStrings = Lists.newArrayList(listString.substring(1, strLen - 1).split(",\\s*"));
        }

        // Create list of holders from strings
        List<Holder<Structure>> holders = new ArrayList<>();
        Registry<Structure> registry = levelAccessor.registryAccess().registry(Registries.STRUCTURE).orElse(null);
        if (registry == null) {
            YungsRoadsCommon.LOGGER.error("Could not get structure registry!");
            return HolderSet.direct(holders);
        }

        listOfStructuresAsStrings.forEach(structureString -> {
            // Fetch the structure from the registry.
            // The method used depends on whether the string passed in is a tag or resource location.
            if (structureString.startsWith("#")) {
                ResourceLocation resourceLocation = ResourceLocation.tryParse(structureString.substring(1));
                if (resourceLocation == null) {
                    YungsRoadsCommon.LOGGER.error("Found invalid structure tag {}", structureString);
                } else {
                    Optional<HolderSet.Named<Structure>> optional = registry.getTag(TagKey.create(Registries.STRUCTURE, resourceLocation));
                    if (optional.isPresent()) {
                        holders.addAll(optional.get().stream().toList());
                    } else {
                        YungsRoadsCommon.LOGGER.error("Found invalid structure tag {}", structureString);
                    }
                }
            } else {
                ResourceLocation resourceLocation = ResourceLocation.tryParse(structureString);
                if (resourceLocation == null) {
                    YungsRoadsCommon.LOGGER.error("Found invalid structure id {}", structureString);
                } else {
                    Optional<Structure> optional = registry.getOptional(resourceLocation);
                    if (optional.isPresent()) {
                        holders.add(Holder.direct(optional.get()));
                    } else {
                        YungsRoadsCommon.LOGGER.error("Found invalid structure id {}", structureString);
                    }
                }

            }

        });
        return HolderSet.direct(holders);
    }
}
