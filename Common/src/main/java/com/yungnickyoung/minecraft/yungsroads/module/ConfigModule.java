package com.yungnickyoung.minecraft.yungsroads.module;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;

public class ConfigModule {
    public General general = new General();

    public static class General {
        public String structuresString = "[#minecraft:village]";
        public HolderSet<ConfiguredStructureFeature<?, ?>> structures; // Evaluated at runtime, after registries are loaded
    }
}
