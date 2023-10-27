package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsapi.world.BlockStateRandomizer;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

public class RoadFeatureConfiguration implements FeatureConfiguration {
    public static final Codec<RoadFeatureConfiguration> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    RoadTypeConfig.CODEC.listOf().fieldOf("roadTypes").forGetter((config) -> config.roadTypes),
                    BlockStateRandomizer.CODEC.fieldOf("bridgeBlockStates").forGetter((config) -> config.bridgeBlockStates))
            .apply(instance, RoadFeatureConfiguration::new));

    public final List<RoadTypeConfig> roadTypes;
    public final BlockStateRandomizer bridgeBlockStates;

    public RoadFeatureConfiguration(List<RoadTypeConfig> roadTypes, BlockStateRandomizer bridgeBlockStates) {
        this.roadTypes = roadTypes;
        this.bridgeBlockStates = bridgeBlockStates;
    }
}
