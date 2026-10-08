package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Optional;

/**
 * A style of road, loaded from the {@code yungsroads/road_type} datapack registry. Each road gets one road type, chosen
 * from the biomes along it. See {@link RoadTypes#choose}.
 * <p>
 * A road type has either one set of settings, or several weighted variants that roads of the type are split between.
 *
 * @param selection Which biomes the type is chosen for. A type without one is only chosen as a road network's default
 *                  type, wherever no other type's selection matches.
 * @param variants The type's settings, as one or more variants. A file with plain {@code settings} has one variant.
 */
public record RoadType(Optional<Selection> selection, List<Variant> variants) {
    public static final Codec<RoadType> CODEC = RecordCodecBuilder.<FileForm>create(instance -> instance
                    .group(
                            Selection.CODEC.optionalFieldOf("selection").forGetter(FileForm::selection),
                            RoadSettings.CODEC.optionalFieldOf("settings").forGetter(FileForm::settings),
                            ExtraCodecs.nonEmptyList(Variant.CODEC.listOf()).optionalFieldOf("variants").forGetter(FileForm::variants))
                    .apply(instance, FileForm::new))
            .flatXmap(FileForm::toRoadType, roadType -> DataResult.success(FileForm.of(roadType)));

    public RoadType {
        variants = List.copyOf(variants);
    }

    /** The type's priority when its selection overlaps another's. A type without a selection always loses ties. */
    public int priority() {
        return this.selection.map(Selection::priority).orElse(Integer.MIN_VALUE);
    }

    /** A copy whose variants' settings can be changed without affecting this type. */
    public RoadType copy() {
        return new RoadType(this.selection, this.variants.stream().map(variant -> new Variant(variant.weight, variant.settings.copy())).toList());
    }

    /** Whether every variant has the same weight and settings as the other type's, which must have the same selection. */
    public boolean sameAs(RoadType other) {
        if (this.variants.size() != other.variants.size()) {
            return false;
        }
        for (int i = 0; i < this.variants.size(); i++) {
            Variant a = this.variants.get(i);
            Variant b = other.variants.get(i);
            if (a.weight != b.weight || !a.settings.sameAs(b.settings)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Which biomes a road type is chosen for.
     *
     * @param biomes The biomes, as a biome, a list of biomes, or a biome tag.
     * @param priority Decides between types whose biomes overlap: in a biome several types match, only those with the
     *                 highest priority count. Also breaks ties between types that cover a road equally.
     */
    public record Selection(HolderSet<Biome> biomes, int priority) {
        public static final Codec<Selection> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(Selection::biomes),
                        Codec.INT.optionalFieldOf("priority", 0).forGetter(Selection::priority))
                .apply(instance, Selection::new));
    }

    /**
     * One variant of a road type.
     *
     * @param weight How often roads of the type get this variant, relative to the other variants' weights.
     */
    public record Variant(int weight, RoadSettings settings) {
        public static final Codec<Variant> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Variant::weight),
                        RoadSettings.CODEC.fieldOf("settings").forGetter(Variant::settings))
                .apply(instance, Variant::new));
    }

    /** A road type as written in its file, with either plain settings or a list of variants. */
    private record FileForm(Optional<Selection> selection, Optional<RoadSettings> settings, Optional<List<Variant>> variants) {
        static FileForm of(RoadType roadType) {
            List<Variant> variants = roadType.variants;
            boolean plain = variants.size() == 1 && variants.get(0).weight == 1;
            return plain
                    ? new FileForm(roadType.selection, Optional.of(variants.get(0).settings), Optional.empty())
                    : new FileForm(roadType.selection, Optional.empty(), Optional.of(variants));
        }

        DataResult<RoadType> toRoadType() {
            if (this.settings.isPresent() == this.variants.isPresent()) {
                return DataResult.error(() -> "A road type must have either \"settings\" or \"variants\", but not both");
            }
            return DataResult.success(new RoadType(this.selection,
                    this.variants.orElseGet(() -> List.of(new Variant(1, this.settings.get())))));
        }
    }
}
