package com.yungnickyoung.minecraft.yungsroads.world.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Samples approximate terrain for road routing without loading or generating any chunks.
 * Thread-safe, except for the {@link HeightSampler}s it creates.
 */
public class TerrainSampler {
    /**
     * Density above which vanilla considers a point solid when computing its preliminary surface level.
     * See {@code NoiseChunk#computePreliminarySurfaceLevel}.
     */
    private static final double PRELIMINARY_SURFACE_THRESHOLD = 0.390625;

    /**
     * How far above the preliminary surface level's cell to start searching for the real surface. Starting lower
     * risks starting inside a hill and stopping at a cave pocket on the way up. Measured on sample seeds, this
     * matches a full column scan's accuracy at a third of the cost.
     */
    private static final int PRELIMINARY_SURFACE_OFFSET = 16;

    /*
     * Marker types for density functions that only vary with x and z. The Marker.Type enum isn't public,
     * so its values are captured from markers created through the public factory methods.
     */
    private static final Object FLAT_CACHE_MARKER_TYPE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.flatCache(DensityFunctions.zero())).type();
    private static final Object CACHE_2D_MARKER_TYPE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.cache2d(DensityFunctions.zero())).type();

    private final ServerLevel serverLevel;

    public TerrainSampler(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
    }

    /**
     * Samples the approximate surface height of a column. Implementations may cache internal state between calls,
     * so each instance must only be used by one thread.
     */
    @FunctionalInterface
    public interface HeightSampler {
        double surfaceHeight(int x, int z);
    }

    /**
     * Creates a sampler for surface heights.
     * <p>
     * For noise-based generators this finds where the final density, which shapes the generated terrain, turns
     * solid. That's within about a block of the generated surface, ignoring caves and the surface rules' top layer.
     * Scanning a whole column of final density is slow, so the scan starts just above vanilla's preliminary surface
     * level, a cheap estimate that is usually several blocks too low (often below sea level on land), and walks up or
     * down from there. Results are interpolated between noise cells so slopes are continuous instead of stepping every
     * cell height (8 blocks).
     */
    public HeightSampler createHeightSampler() {
        ChunkGenerator generator = this.serverLevel.getChunkSource().getGenerator();
        RandomState randomState = this.serverLevel.getChunkSource().randomState();

        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return (x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, this.serverLevel, randomState);
        }

        NoiseSettings noiseSettings = noiseGenerator.generatorSettings().value().noiseSettings();
        int cellHeight = noiseSettings.getCellHeight();
        int minY = noiseSettings.minY();
        int maxY = minY + noiseSettings.height();
        DensityFunction preliminaryDensity = withColumnCaches(randomState.router().initialDensityWithoutJaggedness());
        DensityFunction finalDensity = withColumnCaches(randomState.router().finalDensity());

        return (x, z) -> {
            double preliminarySurface = preliminarySurfaceHeight(preliminaryDensity, x, z, cellHeight, minY, maxY);
            int y = Mth.clamp(Math.floorDiv((int) preliminarySurface, cellHeight) * cellHeight + PRELIMINARY_SURFACE_OFFSET, minY, maxY);
            double densityHere = finalDensity.compute(new DensityFunction.SinglePointContext(x, y, z));
            if (densityHere > 0) {
                // Started underground. Walk up to the first non-solid cell.
                while (y + cellHeight <= maxY) {
                    double densityAbove = finalDensity.compute(new DensityFunction.SinglePointContext(x, y + cellHeight, z));
                    if (densityAbove <= 0) {
                        return y + densityHere / (densityHere - densityAbove) * cellHeight;
                    }
                    y += cellHeight;
                    densityHere = densityAbove;
                }
                return maxY;
            }
            // Started above ground. Walk down to the first solid cell.
            double densityAbove = densityHere;
            while (y - cellHeight >= minY) {
                y -= cellHeight;
                densityHere = finalDensity.compute(new DensityFunction.SinglePointContext(x, y, z));
                if (densityHere > 0) {
                    return y + densityHere / (densityHere - densityAbove) * cellHeight;
                }
                densityAbove = densityHere;
            }
            return minY;
        };
    }

    /**
     * Mirrors vanilla's preliminary surface level ({@code NoiseChunk#computePreliminarySurfaceLevel}), interpolated
     * between cells.
     */
    private static double preliminarySurfaceHeight(DensityFunction density, int x, int z, int cellHeight, int minY, int maxY) {
        Double densityAbove = null;
        for (int y = maxY; y >= minY; y -= cellHeight) {
            double densityHere = density.compute(new DensityFunction.SinglePointContext(x, y, z));
            if (densityHere > PRELIMINARY_SURFACE_THRESHOLD) {
                if (densityAbove == null) {
                    return y;
                }
                return y + (densityHere - PRELIMINARY_SURFACE_THRESHOLD) / (densityHere - densityAbove) * cellHeight;
            }
            densityAbove = densityHere;
        }
        return minY;
    }

    /**
     * Outside a NoiseChunk, the router's cache markers are no-ops, so the expensive 2D noises would be recomputed
     * at every y. Cache them per column instead, same as NoiseChunk does.
     */
    private static DensityFunction withColumnCaches(DensityFunction density) {
        return density.mapAll(function ->
                function instanceof DensityFunctions.MarkerOrMarked marker
                        && (marker.type() == FLAT_CACHE_MARKER_TYPE || marker.type() == CACHE_2D_MARKER_TYPE)
                        ? new ColumnCache(marker.wrapped())
                        : function);
    }

    /**
     * Returns the biome at sea level for the given column, queried directly from the biome source.
     */
    public Holder<Biome> biomeAt(int x, int z) {
        return this.serverLevel.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(seaLevel()),
                QuartPos.fromBlock(z),
                this.serverLevel.getChunkSource().randomState().sampler());
    }

    public boolean isOcean(int x, int z) {
        return biomeAt(x, z).is(BiomeTags.IS_OCEAN);
    }

    public int seaLevel() {
        return this.serverLevel.getSeaLevel();
    }

    /**
     * Caches the wrapped function's value for the most recently sampled column.
     * Only valid for functions that don't vary with y. Not thread-safe.
     */
    private static final class ColumnCache implements DensityFunction {
        private final DensityFunction wrapped;
        private boolean hasValue = false;
        private int lastX, lastZ;
        private double lastValue;

        private ColumnCache(DensityFunction wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public double compute(FunctionContext context) {
            if (!this.hasValue || context.blockX() != this.lastX || context.blockZ() != this.lastZ) {
                this.lastValue = this.wrapped.compute(context);
                this.lastX = context.blockX();
                this.lastZ = context.blockZ();
                this.hasValue = true;
            }
            return this.lastValue;
        }

        @Override
        public void fillArray(double[] values, ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new ColumnCache(this.wrapped.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return this.wrapped.minValue();
        }

        @Override
        public double maxValue() {
            return this.wrapped.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("ColumnCache is a runtime-only wrapper and can't be serialized");
        }
    }
}
