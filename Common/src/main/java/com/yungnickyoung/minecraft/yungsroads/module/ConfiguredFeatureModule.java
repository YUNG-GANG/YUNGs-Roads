package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsapi.world.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypeSettings;
import com.yungnickyoung.minecraft.yungsroads.world.config.TempEnum;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;

public class ConfiguredFeatureModule {
    public static final ConfiguredFeature<?, ?> ROAD_CONFIGURED = new ConfiguredFeature<>(FeatureModule.ROAD, new RoadFeatureConfiguration(
            List.of(
                    new RoadTypeSettings(
                            List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.DIRT_PATH),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.DIRT_PATH.defaultBlockState())),
                    new RoadTypeSettings(
                            List.of(Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.GRAVEL),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.STONE.defaultBlockState())
                                    .addBlock(Blocks.COBBLESTONE.defaultBlockState(), 0.7F)
                                    .addBlock(Blocks.ANDESITE.defaultBlockState(), 0.2F)),
                    new RoadTypeSettings(
                            List.of(Blocks.SNOW_BLOCK, Blocks.ICE),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.SNOW_BLOCK.defaultBlockState())
                                    .addBlock(Blocks.DIRT_PATH.defaultBlockState(), 0.95F)),
                    new RoadTypeSettings(
                            List.of(Blocks.SAND, Blocks.SANDSTONE, Blocks.RED_SAND, Blocks.RED_SANDSTONE, Blocks.TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.YELLOW_TERRACOTTA),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.SAND.defaultBlockState())
                                    .addBlock(Blocks.GRAVEL.defaultBlockState(), 0.4F)
                                    .addBlock(Blocks.GRANITE.defaultBlockState(), 0.4F)
                                    .addBlock(Blocks.ANDESITE.defaultBlockState(), 0.2F))
            ),
            new BlockStateRandomizer(Blocks.OAK_PLANKS.defaultBlockState())
    ));
    public static final PlacedFeature ROAD_PLACED = new PlacedFeature(
            Holder.direct(ROAD_CONFIGURED),
            List.of());

    public static void registerConfiguredFeatures() {
        Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, new ResourceLocation(YungsRoadsCommon.MOD_ID, "road"), ROAD_CONFIGURED);
    }

    public static void registerPlacedFeatures() {
        Registry.register(BuiltinRegistries.PLACED_FEATURE, new ResourceLocation(YungsRoadsCommon.MOD_ID, "road"), ROAD_PLACED);
    }
}
