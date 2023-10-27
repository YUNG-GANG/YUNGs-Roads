package com.yungnickyoung.minecraft.yungsroads.world.road.segment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Specifies the type of a specific {@link DefaultRoadSegment}.
 * This class also serves as the registration hub for RoadSegment types.
 */
public interface RoadSegmentType<C extends DefaultRoadSegment> {
    /* Utility maps for codecs. Simulates the approach vanilla registries use. */
    Map<ResourceLocation, RoadSegmentType<?>> ROAD_SEGMENT_TYPES_BY_NAME = new HashMap<>();
    Map<RoadSegmentType<?>, ResourceLocation> NAME_BY_ROAD_SEGMENT_TYPES = new HashMap<>();

    /* Codecs */
    Codec<RoadSegmentType<?>> ROAD_SEGMENT_TYPE_CODEC = ResourceLocation.CODEC
            .flatXmap(
                    resourceLocation -> Optional.ofNullable(ROAD_SEGMENT_TYPES_BY_NAME.get(resourceLocation))
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error("Unknown road segment type: " + resourceLocation)),
                    roadSegmentType -> Optional.of(NAME_BY_ROAD_SEGMENT_TYPES.get(roadSegmentType))
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error("No ID found for road segment type " + roadSegmentType + ". Is it registered?")));

    Codec<DefaultRoadSegment> ROAD_SEGMENT_CODEC = RoadSegmentType.ROAD_SEGMENT_TYPE_CODEC
            .dispatch("type", DefaultRoadSegment::type, RoadSegmentType::codec);

    /* Types. Add any new types here! */
    RoadSegmentType<DefaultRoadSegment> DEFAULT = register("default", DefaultRoadSegment.CODEC);
    RoadSegmentType<SplineRoadSegment> SPLINE = register("spline", SplineRoadSegment.CODEC);

    /**
     * Utility method for registering RoadSegmentTypes.
     */
    static <C extends DefaultRoadSegment> RoadSegmentType<C> register(ResourceLocation resourceLocation, Codec<C> codec) {
        RoadSegmentType<C> roadSegmentType = () -> codec;
        ROAD_SEGMENT_TYPES_BY_NAME.put(resourceLocation, roadSegmentType);
        NAME_BY_ROAD_SEGMENT_TYPES.put(roadSegmentType, resourceLocation);
        return roadSegmentType;
    }

    /**
     * Private utility method for registering RoadSegmentTypes native to YUNG's API.
     */
    private static <C extends DefaultRoadSegment> RoadSegmentType<C> register(String id, Codec<C> codec) {
        return register(new ResourceLocation(YungsRoadsCommon.MOD_ID, id), codec);
    }

    /**
     * Supplies the codec for the {@link DefaultRoadSegment} corresponding to this RoadSegmentType.
     */
    Codec<C> codec();
}
