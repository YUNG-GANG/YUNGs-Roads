package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;

/**
 * The roads of one dimension, loaded from the {@code yungsroads/road_network} datapack registry. A network's id is the
 * id of the dimension it's for, so {@code data/minecraft/yungsroads/road_network/overworld.json} is the Overworld's.
 * Dimensions without a network have no roads.
 *
 * @param structures The structures roads connect, as a structure, a list of structures, or a structure tag.
 * @param roadTypes The road types the network's roads may use, as a road type, a list, or a tag. A tag lets other
 *                  datapacks add road types without replacing the network.
 * @param defaultRoadType The road type that gets the votes of biomes no road type in {@code roadTypes} matches. Usable
 *                        even if not in {@code roadTypes}.
 */
public record RoadNetwork(HolderSet<Structure> structures, HolderSet<RoadType> roadTypes, Holder<RoadType> defaultRoadType) {
    public static final ResourceKey<Registry<RoadNetwork>> REGISTRY_KEY = ResourceKey.createRegistryKey(YungsRoadsCommon.id("road_network"));

    public static final Codec<RoadNetwork> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    RegistryCodecs.homogeneousList(Registries.STRUCTURE).fieldOf("structures").forGetter(RoadNetwork::structures),
                    RegistryCodecs.homogeneousList(RoadTypes.REGISTRY_KEY).fieldOf("road_types").forGetter(RoadNetwork::roadTypes),
                    RegistryFixedCodec.create(RoadTypes.REGISTRY_KEY).fieldOf("default_road_type").forGetter(RoadNetwork::defaultRoadType))
            .apply(instance, RoadNetwork::new));

    /** The road network of a level's dimension, if it has one. */
    public static Optional<RoadNetwork> of(ServerLevel level) {
        Optional<RoadNetwork> network = level.registryAccess().registry(REGISTRY_KEY)
                .flatMap(registry -> registry.getOptional(level.dimension().location()));
        // An empty list is a valid way to turn roads off, but is more often a misspelled tag
        if (network.isPresent() && network.get().structures().size() == 0) {
            YungsRoadsCommon.LOGGER.warn("The road network for {} has no structures, so the dimension will not generate roads.", level.dimension().location());
        }
        return network;
    }
}
