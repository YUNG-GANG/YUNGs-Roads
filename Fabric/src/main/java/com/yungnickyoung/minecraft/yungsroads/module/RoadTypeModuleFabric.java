package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;

public class RoadTypeModuleFabric {
    public static void init() {
        // Road types are only used on the server, so they aren't synced to clients
        DynamicRegistries.register(RoadTypes.REGISTRY_KEY, RoadType.CODEC);
    }
}
