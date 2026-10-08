package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.world.config.RoadNetwork;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;

public class RegistryModuleFabric {
    public static void init() {
        // Road networks and types are only used on the server, so they aren't synced to clients
        DynamicRegistries.register(RoadNetwork.REGISTRY_KEY, RoadNetwork.CODEC);
        DynamicRegistries.register(RoadTypes.REGISTRY_KEY, RoadType.CODEC);
    }
}
