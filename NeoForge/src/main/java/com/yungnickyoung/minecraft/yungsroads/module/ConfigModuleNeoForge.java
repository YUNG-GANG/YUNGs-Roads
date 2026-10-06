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
        // Client levels load again on every dimension change, which would undo settings applied from the debug screen
        if (event.getLevel().isClientSide()) {
            return;
        }
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
        if (event.getConfig().getSpec() != YRConfigNeoForge.SPEC) {
            return;
        }
        if (event instanceof ModConfigEvent.Reloading) {
            // Settings that need a world restart keep returning the values they were loaded with until the world
            // restarts, even after the file changes, so baking them here would undo settings applied from the debug
            // screen. That includes the reload fired when the debug screen saves.
            bakeDebugConfig();
        } else {
            bakeConfig();
        }
    }

    private static void bakeConfig() {
        bakeWorldConfig();
        bakeDebugConfig();
    }

    /** Bakes the settings that need a world restart to change. */
    private static void bakeWorldConfig() {
        YungsRoadsCommon.CONFIG.general.structuresString = YRConfigNeoForge.general.structures.get();

        YungsRoadsCommon.CONFIG.advanced.nodeStepDistance = YRConfigNeoForge.advanced.nodeStepDistance.get();
        YungsRoadsCommon.CONFIG.advanced.jitterAmount = YRConfigNeoForge.advanced.jitterAmount.get();
        YungsRoadsCommon.CONFIG.advanced.heuristicWeight = YRConfigNeoForge.advanced.heuristicWeight.get();
        YungsRoadsCommon.CONFIG.advanced.slopeWeight = YRConfigNeoForge.advanced.slopeWeight.get();
        YungsRoadsCommon.CONFIG.advanced.freeGrade = YRConfigNeoForge.advanced.freeGrade.get();
        YungsRoadsCommon.CONFIG.advanced.straightenRoutes = YRConfigNeoForge.advanced.straightenRoutes.get();
        YungsRoadsCommon.CONFIG.advanced.maxGrade = YRConfigNeoForge.advanced.maxGrade.get();
        YungsRoadsCommon.CONFIG.advanced.waterWeight = YRConfigNeoForge.advanced.waterWeight.get();
        YungsRoadsCommon.CONFIG.advanced.maxBridgeLength = YRConfigNeoForge.advanced.maxBridgeLength.get();
        YungsRoadsCommon.CONFIG.advanced.smoothingRadius = YRConfigNeoForge.advanced.smoothingRadius.get();
        YungsRoadsCommon.CONFIG.advanced.maxCutDepth = YRConfigNeoForge.advanced.maxCutDepth.get();
        YungsRoadsCommon.CONFIG.advanced.maxFillDepth = YRConfigNeoForge.advanced.maxFillDepth.get();
        YungsRoadsCommon.CONFIG.advanced.maxLandBridgeLength = YRConfigNeoForge.advanced.maxLandBridgeLength.get();
        YungsRoadsCommon.CONFIG.advanced.landBridgeEdgeRoughness = YRConfigNeoForge.advanced.landBridgeEdgeRoughness.get();
        YungsRoadsCommon.CONFIG.advanced.landBridgeDecay = YRConfigNeoForge.advanced.landBridgeDecay.get();
        YungsRoadsCommon.CONFIG.advanced.landBridgeSag = YRConfigNeoForge.advanced.landBridgeSag.get();
        YungsRoadsCommon.CONFIG.advanced.landBridgeRailingDrop = YRConfigNeoForge.advanced.landBridgeRailingDrop.get();
        YungsRoadsCommon.CONFIG.advanced.landBridgeRailingChance = YRConfigNeoForge.advanced.landBridgeRailingChance.get();
    }

    private static void bakeDebugConfig() {
        YungsRoadsCommon.CONFIG.debug.enableExtraDebugF3Info = YRConfigNeoForge.debug.enableExtraDebugF3Info.get();
        YungsRoadsCommon.CONFIG.debug.placeRoads = YRConfigNeoForge.debug.placeRoads.get();
        YungsRoadsCommon.CONFIG.debug.placeUnjitteredPosDebugMarkers = YRConfigNeoForge.debug.placeUnjitteredPosDebugMarkers.get();
        YungsRoadsCommon.CONFIG.debug.placeJitteredPosDebugMarkers = YRConfigNeoForge.debug.placeJitteredPosDebugMarkers.get();
        YungsRoadsCommon.CONFIG.debug.placeRoadEndpointDebugMarkers = YRConfigNeoForge.debug.placeRoadEndpointDebugMarkers.get();
        YungsRoadsCommon.CONFIG.debug.placeStraightDebugLine = YRConfigNeoForge.debug.placeStraightDebugLine.get();
        YungsRoadsCommon.CONFIG.debug.placeDebugPaths = YRConfigNeoForge.debug.placeDebugPaths.get();
    }

    /**
     * Writes the current advanced and debug settings to the config file.
     */
    public static void saveRoadSettings() {
        ConfigModule.Advanced advanced = YungsRoadsCommon.CONFIG.advanced;
        YRConfigNeoForge.advanced.nodeStepDistance.set(advanced.nodeStepDistance);
        YRConfigNeoForge.advanced.jitterAmount.set(advanced.jitterAmount);
        YRConfigNeoForge.advanced.heuristicWeight.set(advanced.heuristicWeight);
        YRConfigNeoForge.advanced.slopeWeight.set(advanced.slopeWeight);
        YRConfigNeoForge.advanced.freeGrade.set(advanced.freeGrade);
        YRConfigNeoForge.advanced.straightenRoutes.set(advanced.straightenRoutes);
        YRConfigNeoForge.advanced.maxGrade.set(advanced.maxGrade);
        YRConfigNeoForge.advanced.waterWeight.set(advanced.waterWeight);
        YRConfigNeoForge.advanced.maxBridgeLength.set(advanced.maxBridgeLength);
        YRConfigNeoForge.advanced.smoothingRadius.set(advanced.smoothingRadius);
        YRConfigNeoForge.advanced.maxCutDepth.set(advanced.maxCutDepth);
        YRConfigNeoForge.advanced.maxFillDepth.set(advanced.maxFillDepth);
        YRConfigNeoForge.advanced.maxLandBridgeLength.set(advanced.maxLandBridgeLength);
        YRConfigNeoForge.advanced.landBridgeEdgeRoughness.set(advanced.landBridgeEdgeRoughness);
        YRConfigNeoForge.advanced.landBridgeDecay.set(advanced.landBridgeDecay);
        YRConfigNeoForge.advanced.landBridgeSag.set(advanced.landBridgeSag);
        YRConfigNeoForge.advanced.landBridgeRailingDrop.set(advanced.landBridgeRailingDrop);
        YRConfigNeoForge.advanced.landBridgeRailingChance.set(advanced.landBridgeRailingChance);

        ConfigModule.Debug debug = YungsRoadsCommon.CONFIG.debug;
        YRConfigNeoForge.debug.enableExtraDebugF3Info.set(debug.enableExtraDebugF3Info);
        YRConfigNeoForge.debug.placeRoads.set(debug.placeRoads);
        YRConfigNeoForge.debug.placeUnjitteredPosDebugMarkers.set(debug.placeUnjitteredPosDebugMarkers);
        YRConfigNeoForge.debug.placeJitteredPosDebugMarkers.set(debug.placeJitteredPosDebugMarkers);
        YRConfigNeoForge.debug.placeRoadEndpointDebugMarkers.set(debug.placeRoadEndpointDebugMarkers);
        YRConfigNeoForge.debug.placeStraightDebugLine.set(debug.placeStraightDebugLine);
        YRConfigNeoForge.debug.placeDebugPaths.set(debug.placeDebugPaths);

        YRConfigNeoForge.SPEC.save();
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
