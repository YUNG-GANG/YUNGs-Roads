package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public class RoadTypeModuleNeoForge {
    public static void init(IEventBus eventBus) {
        // Road types are only used on the server, so they aren't synced to clients
        eventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> event.dataPackRegistry(RoadTypes.REGISTRY_KEY, RoadType.CODEC));
    }
}
