package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsapi.world.BlockStateRandomizer;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class RoadTypeSettings {
    public static final Codec<RoadTypeSettings> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    Registry.BLOCK.byNameCodec().listOf().fieldOf("targetBlocks").forGetter((settings) -> settings.targetBlocks),
                    TempEnum.CODEC.fieldOf("targetTemperature").forGetter((settings) -> settings.targetTemperature),
                    BlockStateRandomizer.CODEC.fieldOf("pathBlockStates").forGetter((settings) -> settings.pathBlockStates))
            .apply(instance, RoadTypeSettings::new));

    public final List<Block> targetBlocks;
    public final TempEnum targetTemperature;
    public final BlockStateRandomizer pathBlockStates;

    public RoadTypeSettings(List<Block> targetBlocks, TempEnum targetTemperature, BlockStateRandomizer pathBlockStates) {
        this.targetBlocks = targetBlocks;
        this.targetTemperature = targetTemperature;
        this.pathBlockStates = pathBlockStates;
    }
}
