package com.yungnickyoung.minecraft.yungsroads.module;


import com.google.common.collect.Lists;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.config.YRConfigForge;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigModuleForge {
    public static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, YRConfigForge.SPEC, "YungsRoads-forge-1_18.toml");
        MinecraftForge.EVENT_BUS.addListener(ConfigModuleForge::onWorldLoad);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ConfigModuleForge::onConfigChange);
    }

    private static void onWorldLoad(WorldEvent.Load event) {
        bakeConfig();
        YungsRoadsCommon.CONFIG.general.structures = parseStructureStringList(
                YRConfigForge.general.structures.get(),
                event.getWorld());
        if (event.getWorld() instanceof ServerLevel) {
            ((IStructureRegionCacheProvider) event.getWorld())
                    .getStructureRegionCache()
                    .getStructureRegionGenerator()
                    .setConfiguredStructureFeatures(YungsRoadsCommon.CONFIG.general.structures);
        }
    }

    private static void onConfigChange(ModConfigEvent event) {
        if (event.getConfig().getSpec() == YRConfigForge.SPEC) {
            bakeConfig();
        }
    }

    private static void bakeConfig() {
        YungsRoadsCommon.CONFIG.general.structuresString = YRConfigForge.general.structures.get();
    }

    private static HolderSet<ConfiguredStructureFeature<?, ?>> parseStructureStringList(String listString, LevelAccessor levelAccessor) {
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
        List<Holder<ConfiguredStructureFeature<?, ?>>> holders = new ArrayList<>();
        Registry<ConfiguredStructureFeature<?, ?>> registry = levelAccessor.registryAccess().registryOrThrow(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY);

        listOfStructuresAsStrings.forEach(structureString -> {
            // Fetch the structure from the registry.
            // The method used depends on whether the string passed in is a tag or resource location.
            if (structureString.startsWith("#")) {
                Optional<HolderSet.Named<ConfiguredStructureFeature<?, ?>>> optional = registry
                        .getTag(TagKey.create(
                                Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY,
                                new ResourceLocation(structureString.substring(1))));

                if (optional.isPresent())
                    holders.addAll(optional.get().stream().toList());
                else
                    YungsRoadsCommon.LOGGER.error("Found invalid structure tag {}", structureString);

            } else {
                Optional<ConfiguredStructureFeature<?, ?>> optional = registry
                        .getOptional(new ResourceLocation(structureString));

                if (optional.isPresent())
                    holders.add(Holder.direct(optional.get()));
                else
                    YungsRoadsCommon.LOGGER.error("Found invalid structure tag {}", structureString);

            }

        });
        return HolderSet.direct(holders);
    }
}
