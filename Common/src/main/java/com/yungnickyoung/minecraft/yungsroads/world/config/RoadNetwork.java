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
import net.minecraft.resources.ResourceLocation;
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
 * @param defaultRoadType The road type that biomes no road type in {@code roadTypes} matches count toward. Usable even
 *                        if not in {@code roadTypes}.
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
        return level.registryAccess().registry(REGISTRY_KEY).flatMap(registry -> registry.getOptional(level.dimension().location()));
    }

    /**
     * Warns about likely mistakes that leave the network's dimension without roads, or a road type without roads. Called
     * once, when the dimension's level loads.
     */
    public void warnAboutMistakes(ResourceLocation dimension) {
        // An empty list is a valid way to turn roads off, but is more often a misspelled tag
        if (this.structures.size() == 0) {
            YungsRoadsCommon.LOGGER.warn("The road network for {} has no structures, so the dimension will not generate roads.", dimension);
        }
        // A road type is only chosen by its biomes, or as the default type
        for (Holder<RoadType> type : this.roadTypes) {
            if (type.value().selection().isEmpty() && !type.unwrapKey().equals(this.defaultRoadType.unwrapKey())) {
                YungsRoadsCommon.LOGGER.warn("Road type {} has no biomes and isn't the default road type of the road network for {}, so no roads there will use it.",
                        type.unwrapKey().map(key -> key.location().toString()).orElse("?"), dimension);
            }
        }
    }
}
