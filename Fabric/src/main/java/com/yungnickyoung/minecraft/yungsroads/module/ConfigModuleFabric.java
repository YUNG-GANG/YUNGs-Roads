package com.yungnickyoung.minecraft.yungsroads.module;

import com.google.common.collect.Lists;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.config.YRConfigFabric;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.AdvancedSetting;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigModuleFabric {
    public static void init() {
        AutoConfig.register(YRConfigFabric.class, Toml4jConfigSerializer::new);
        AutoConfig.getConfigHolder(YRConfigFabric.class).registerSaveListener(ConfigModuleFabric::bakeConfig);
        AutoConfig.getConfigHolder(YRConfigFabric.class).registerLoadListener(ConfigModuleFabric::bakeConfig);
        bakeConfig(AutoConfig.getConfigHolder(YRConfigFabric.class).get());
        ServerWorldEvents.LOAD.register(ConfigModuleFabric::onWorldLoad);
    }

    private static void onWorldLoad(MinecraftServer minecraftServer, ServerLevel serverLevel) {
        YungsRoadsCommon.CONFIG.general.structures = parseStructureStringList(
                YungsRoadsCommon.CONFIG.general.structuresString,
                serverLevel);
        ((IStructureRegionCacheProvider) serverLevel)
                .getStructureRegionCache()
                .getStructureRegionGenerator()
                .setEndpointStructures(YungsRoadsCommon.CONFIG.general.structures);
    }

    private static InteractionResult bakeConfig(ConfigHolder<YRConfigFabric> configHolder, YRConfigFabric configFabric) {
        bakeConfig(configFabric);
        return InteractionResult.SUCCESS;
    }

    private static void bakeConfig(YRConfigFabric configFabric) {
        YungsRoadsCommon.CONFIG.general.structuresString = configFabric.general.structures;

        // AutoConfig doesn't enforce ranges on decimal values, so clamp them to the valid ranges here
        ConfigModule.Advanced advanced = YungsRoadsCommon.CONFIG.advanced;
        advanced.nodeStepDistance = (int) AdvancedSetting.NODE_STEP_DISTANCE.clamp(configFabric.advanced.nodeStepDistance);
        advanced.jitterAmount = AdvancedSetting.JITTER_AMOUNT.clamp(configFabric.advanced.jitterAmount);
        advanced.heuristicWeight = AdvancedSetting.HEURISTIC_WEIGHT.clamp(configFabric.advanced.heuristicWeight);
        advanced.slopeWeight = AdvancedSetting.SLOPE_WEIGHT.clamp(configFabric.advanced.slopeWeight);
        advanced.freeGrade = AdvancedSetting.FREE_GRADE.clamp(configFabric.advanced.freeGrade);
        advanced.straightenRoutes = configFabric.advanced.straightenRoutes;
        advanced.maxGrade = AdvancedSetting.MAX_GRADE.clamp(configFabric.advanced.maxGrade);
        advanced.waterWeight = AdvancedSetting.WATER_WEIGHT.clamp(configFabric.advanced.waterWeight);
        advanced.maxBridgeLength = (int) AdvancedSetting.MAX_BRIDGE_LENGTH.clamp(configFabric.advanced.maxBridgeLength);
        advanced.smoothingRadius = (int) AdvancedSetting.SMOOTHING_RADIUS.clamp(configFabric.advanced.smoothingRadius);
        advanced.maxCutDepth = (int) AdvancedSetting.MAX_CUT_DEPTH.clamp(configFabric.advanced.maxCutDepth);
        advanced.maxFillDepth = (int) AdvancedSetting.MAX_FILL_DEPTH.clamp(configFabric.advanced.maxFillDepth);
        advanced.maxLandBridgeLength = (int) AdvancedSetting.MAX_LAND_BRIDGE_LENGTH.clamp(configFabric.advanced.maxLandBridgeLength);
        advanced.landBridgeEdgeRoughness = AdvancedSetting.LAND_BRIDGE_EDGE_ROUGHNESS.clamp(configFabric.advanced.landBridgeEdgeRoughness);
        advanced.landBridgeDecay = AdvancedSetting.LAND_BRIDGE_DECAY.clamp(configFabric.advanced.landBridgeDecay);
        advanced.landBridgeSag = AdvancedSetting.LAND_BRIDGE_SAG.clamp(configFabric.advanced.landBridgeSag);
        advanced.landBridgeRailingDrop = (int) AdvancedSetting.LAND_BRIDGE_RAILING_DROP.clamp(configFabric.advanced.landBridgeRailingDrop);
        advanced.landBridgeRailingChance = (int) AdvancedSetting.LAND_BRIDGE_RAILING_CHANCE.clamp(configFabric.advanced.landBridgeRailingChance);

        ConfigModule.Debug debug = YungsRoadsCommon.CONFIG.debug;
        debug.enableExtraDebugF3Info = configFabric.debug.enableExtraDebugF3Info;
        debug.placeRoads = configFabric.debug.placeRoads;
        debug.placeUnjitteredPosDebugMarkers = configFabric.debug.placeUnjitteredPosDebugMarkers;
        debug.placeJitteredPosDebugMarkers = configFabric.debug.placeJitteredPosDebugMarkers;
        debug.placeRoadEndpointDebugMarkers = configFabric.debug.placeRoadEndpointDebugMarkers;
        debug.placeStraightDebugLine = configFabric.debug.placeStraightDebugLine;
        debug.placeDebugPaths = configFabric.debug.placeDebugPaths;
    }

    /**
     * Writes the current advanced and debug settings to the config file.
     */
    public static void saveRoadSettings() {
        ConfigHolder<YRConfigFabric> holder = AutoConfig.getConfigHolder(YRConfigFabric.class);
        YRConfigFabric configFabric = holder.getConfig();

        ConfigModule.Advanced advanced = YungsRoadsCommon.CONFIG.advanced;
        configFabric.advanced.nodeStepDistance = advanced.nodeStepDistance;
        configFabric.advanced.jitterAmount = advanced.jitterAmount;
        configFabric.advanced.heuristicWeight = advanced.heuristicWeight;
        configFabric.advanced.slopeWeight = advanced.slopeWeight;
        configFabric.advanced.freeGrade = advanced.freeGrade;
        configFabric.advanced.straightenRoutes = advanced.straightenRoutes;
        configFabric.advanced.maxGrade = advanced.maxGrade;
        configFabric.advanced.waterWeight = advanced.waterWeight;
        configFabric.advanced.maxBridgeLength = advanced.maxBridgeLength;
        configFabric.advanced.smoothingRadius = advanced.smoothingRadius;
        configFabric.advanced.maxCutDepth = advanced.maxCutDepth;
        configFabric.advanced.maxFillDepth = advanced.maxFillDepth;
        configFabric.advanced.maxLandBridgeLength = advanced.maxLandBridgeLength;
        configFabric.advanced.landBridgeEdgeRoughness = advanced.landBridgeEdgeRoughness;
        configFabric.advanced.landBridgeDecay = advanced.landBridgeDecay;
        configFabric.advanced.landBridgeSag = advanced.landBridgeSag;
        configFabric.advanced.landBridgeRailingDrop = advanced.landBridgeRailingDrop;
        configFabric.advanced.landBridgeRailingChance = advanced.landBridgeRailingChance;

        ConfigModule.Debug debug = YungsRoadsCommon.CONFIG.debug;
        configFabric.debug.enableExtraDebugF3Info = debug.enableExtraDebugF3Info;
        configFabric.debug.placeRoads = debug.placeRoads;
        configFabric.debug.placeUnjitteredPosDebugMarkers = debug.placeUnjitteredPosDebugMarkers;
        configFabric.debug.placeJitteredPosDebugMarkers = debug.placeJitteredPosDebugMarkers;
        configFabric.debug.placeRoadEndpointDebugMarkers = debug.placeRoadEndpointDebugMarkers;
        configFabric.debug.placeStraightDebugLine = debug.placeStraightDebugLine;
        configFabric.debug.placeDebugPaths = debug.placeDebugPaths;

        holder.save();
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
        Registry<Structure> registry = levelAccessor.registryAccess().registryOrThrow(Registries.STRUCTURE);

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
