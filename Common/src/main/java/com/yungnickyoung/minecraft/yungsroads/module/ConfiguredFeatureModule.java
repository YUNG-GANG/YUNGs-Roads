package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsapi.world.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypeConfig;
import com.yungnickyoung.minecraft.yungsroads.world.config.TempEnum;
import com.yungnickyoung.minecraft.yungsroads.world.road.decoration.RoadDecorations;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfiguredFeatureModule {
    /**
     * Map of all placed features
     */
    private static final Map<String, PlacedFeature> placedFeatures = new HashMap<>();

    /* Road Feature */
    public static final ConfiguredFeature<?, ?> ROAD_CONFIGURED = new ConfiguredFeature<>(FeatureModule.ROAD, new RoadFeatureConfiguration(
            List.of(
                    new RoadTypeConfig(
                            List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.DIRT_PATH),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.DIRT_PATH.defaultBlockState())
                                    .addBlock(Blocks.GRASS_BLOCK.defaultBlockState(), 0.05f),
                            1.5f,
                            2.0f,
                            List.of(
                                    RoadDecorations.BUSH1.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.SMALL_WOOD_BENCH.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.STONE_LAMP_POST.configured().withChance(0.3f)
                            )),
                    new RoadTypeConfig(
                            List.of(Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.GRAVEL),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.STONE.defaultBlockState())
                                    .addBlock(Blocks.COBBLESTONE.defaultBlockState(), 0.7F)
                                    .addBlock(Blocks.ANDESITE.defaultBlockState(), 0.2F),
                            1.5f,
                            2.0f,
                            List.of(
                                    RoadDecorations.BUSH1.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.SMALL_WOOD_BENCH.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.STONE_LAMP_POST.configured().withChance(0.3f)
                            )),
                    new RoadTypeConfig(
                            List.of(Blocks.SNOW_BLOCK, Blocks.ICE),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.SNOW_BLOCK.defaultBlockState())
                                    .addBlock(Blocks.DIRT_PATH.defaultBlockState(), 0.95F),
                            1.5f,
                            2.0f,
                            List.of(
                                    RoadDecorations.BUSH1.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.SMALL_WOOD_BENCH.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.STONE_LAMP_POST.configured().withChance(0.3f)
                            )),
                    new RoadTypeConfig(
                            List.of(Blocks.SAND, Blocks.SANDSTONE, Blocks.RED_SAND, Blocks.RED_SANDSTONE, Blocks.TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.YELLOW_TERRACOTTA),
                            TempEnum.ANY,
                            new BlockStateRandomizer(Blocks.SAND.defaultBlockState())
                                    .addBlock(Blocks.GRAVEL.defaultBlockState(), 0.4F)
                                    .addBlock(Blocks.GRANITE.defaultBlockState(), 0.4F)
                                    .addBlock(Blocks.ANDESITE.defaultBlockState(), 0.2F),
                            1.5f,
                            2.0f,
                            List.of(
                                    RoadDecorations.BUSH1.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.SMALL_WOOD_BENCH.configured().withChance(0.3f).withNormalOffset(1),
                                    RoadDecorations.STONE_LAMP_POST.configured().withChance(0.3f)
                            ))
            ),
            new BlockStateRandomizer(Blocks.OAK_PLANKS.defaultBlockState())
    ));
    public static final PlacedFeature ROAD_PLACED = createPlacedFeature("road",
            Holder.direct(ROAD_CONFIGURED),
            List.of());

    /* Decoration features */
    public static final PlacedFeature SUNFLOWER_DECORATION_PLACED = createPlacedFeature("sunflower_decoration",
            VegetationFeatures.PATCH_SUNFLOWER,
            List.of());

    public static final PlacedFeature FLOWER_DEFAULT_DECORATION_PLACED = createPlacedFeature("flower_decoration",
            VegetationFeatures.FLOWER_DEFAULT,
            List.of());

    public static void registerConfiguredFeatures() {
        Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, new ResourceLocation(YungsRoadsCommon.MOD_ID, "road"), ROAD_CONFIGURED);
    }

    public static void registerPlacedFeatures() {
        placedFeatures.forEach((name, placedFeature) -> {
            Registry.register(BuiltinRegistries.PLACED_FEATURE, new ResourceLocation(YungsRoadsCommon.MOD_ID, name), placedFeature);
        });
    }

    private static PlacedFeature createPlacedFeature(String name, Holder<? extends ConfiguredFeature<?, ?>> holder, List<PlacementModifier> list) {
        PlacedFeature placedFeature = new PlacedFeature(Holder.hackyErase(holder), list);
        placedFeatures.put(name, placedFeature);
        return placedFeature;
    }
}
