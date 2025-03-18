package com.yungnickyoung.minecraft.yungsroads.module;

import com.google.common.collect.Lists;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.config.YRConfigFabric;
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
