package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;
import java.util.Optional;

public class RoadFeatureConfiguration implements FeatureConfiguration {
    public static final Codec<RoadFeatureConfiguration> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    RoadTypeConfig.CODEC.listOf().fieldOf("road_types").forGetter((config) -> config.roadTypes),
                    BlockStateRandomizer.CODEC.fieldOf("bridge_blockstates").forGetter((config) -> config.bridgeBlockStates),
                    BlockStateRandomizer.CODEC.optionalFieldOf("bridge_railing_blockstates").forGetter((config) -> config.bridgeRailingBlockStates))
            .apply(instance, RoadFeatureConfiguration::new));

    public final List<RoadTypeConfig> roadTypes;
    public final BlockStateRandomizer bridgeBlockStates;

    /**
     * The railings along the edges of land bridges with a drop beside them. Fences, walls, panes, and bars connect to
     * each other. Without it, land bridges have no railings.
     */
    public final Optional<BlockStateRandomizer> bridgeRailingBlockStates;

    public RoadFeatureConfiguration(List<RoadTypeConfig> roadTypes, BlockStateRandomizer bridgeBlockStates,
                                    Optional<BlockStateRandomizer> bridgeRailingBlockStates) {
        this.roadTypes = roadTypes;
        this.bridgeBlockStates = bridgeBlockStates;
        this.bridgeRailingBlockStates = bridgeRailingBlockStates;
    }
}
