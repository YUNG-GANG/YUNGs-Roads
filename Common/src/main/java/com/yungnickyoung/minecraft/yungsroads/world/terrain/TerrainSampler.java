package com.yungnickyoung.minecraft.yungsroads.world.terrain;

import com.yungnickyoung.minecraft.yungsroads.util.GridKeys;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
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

    /**
     * How many cells of air in a row show that a point is above the terrain rather than in a cave, when rechecking a
     * surface with vanilla's interpolation.
     */
    private static final int OPEN_AIR_CELLS = 4;
    /*
     * Marker types. The Marker.Type enum isn't public, so its values are captured from markers created through the
     * public factory methods. Flat caches and 2D caches mark functions that only vary with x and z. Interpolated
     * marks functions that vanilla only evaluates at cell corners, interpolating between them.
     */
    private static final Object FLAT_CACHE_MARKER_TYPE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.flatCache(DensityFunctions.zero())).type();
    private static final Object CACHE_2D_MARKER_TYPE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.cache2d(DensityFunctions.zero())).type();
    private static final Object INTERPOLATED_MARKER_TYPE = ((DensityFunctions.MarkerOrMarked) DensityFunctions.interpolated(DensityFunctions.zero())).type();

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
     * <p>
     * That's usually within a block or two of the generated terrain, but not always. In about 1% of land columns it
     * finds a surface underwater that isn't. Optionally, surfaces it finds underwater are rechecked with vanilla's own
     * interpolation, which is exact but much slower.
     *
     * @param recheckWater Whether to recheck surfaces found underwater. Rechecking takes about 0.3 ms per column, so
     *                     it's best limited to checks that one wrong sample would spoil, rather than broad sampling.
     */
    public HeightSampler createHeightSampler(boolean recheckWater) {
        ChunkGenerator generator = this.serverLevel.getChunkSource().getGenerator();
        RandomState randomState = this.serverLevel.getChunkSource().randomState();

        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return (x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, this.serverLevel, randomState);
        }

        NoiseSettings noiseSettings = noiseGenerator.generatorSettings().value().noiseSettings();
        int cellWidth = noiseSettings.getCellWidth();
        int cellHeight = noiseSettings.getCellHeight();
        int minY = noiseSettings.minY();
        int maxY = minY + noiseSettings.height();
        int seaLevel = seaLevel();
        DensityFunction preliminaryDensity = withColumnCaches(randomState.router().initialDensityWithoutJaggedness());
        DensityFunction finalDensity = withColumnCaches(randomState.router().finalDensity());
        DensityFunction interpolatedFinalDensity = recheckWater
                ? withCellInterpolation(randomState.router().finalDensity(), cellWidth, cellHeight)
                : null;

        return (x, z) -> {
            int start = scanStart(preliminaryDensity, x, z, cellHeight, minY, maxY);
            double surface = pointDensitySurface(finalDensity, x, z, start, cellHeight, minY, maxY);
            // Oceans are water whatever their exact depth, and common enough that rechecking them would be slow
            if (!recheckWater || !isUnderwater(surface, seaLevel) || isOcean(x, z)) {
                return surface;
            }

            // The point density misses how vanilla shapes terrain: it interpolates the density between cell corners.
            // That can read a block or two low near sea level, or skip past the surface into a cave below it, which
            // would both be taken for water. So, we recheck with vanilla's interpolation. Most rechecks only confirm the
            // water, but as far as I can tell there's no cheap way to tell which ones won't.
            return interpolatedSurface(interpolatedFinalDensity, x, z, start, cellHeight, minY, maxY);
        };
    }

    /**
     * Creates a sampler for surface heights that matches the generated terrain, using vanilla's interpolation for
     * every column. See {@link #interpolatedSurface}. Along roads, the top block is the sampled height rounded down in
     * about 90% of columns, and within a block of it in about 96%. The rest are mostly carved by carvers or adapted
     * to structures, which the density doesn't include.
     * <p>
     * An isolated column takes about 0.2 ms, but neighboring columns share cached cell corners, so sampling columns in
     * order, such as along a road, costs about as much as {@link #createHeightSampler} does.
     */
    public HeightSampler createExactHeightSampler() {
        ChunkGenerator generator = this.serverLevel.getChunkSource().getGenerator();
        RandomState randomState = this.serverLevel.getChunkSource().randomState();

        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            // The base height is the y above the top solid block. Subtract one so rounding down gives the top block, as it
            // does for the density surface.
            return (x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.serverLevel, randomState) - 1;
        }

        NoiseSettings noiseSettings = noiseGenerator.generatorSettings().value().noiseSettings();
        int cellWidth = noiseSettings.getCellWidth();
        int cellHeight = noiseSettings.getCellHeight();
        int minY = noiseSettings.minY();
        int maxY = minY + noiseSettings.height();
        DensityFunction preliminaryDensity = withColumnCaches(randomState.router().initialDensityWithoutJaggedness());
        DensityFunction interpolatedFinalDensity = withCellInterpolation(randomState.router().finalDensity(), cellWidth, cellHeight);

        return (x, z) -> interpolatedSurface(interpolatedFinalDensity, x, z,
                scanStart(preliminaryDensity, x, z, cellHeight, minY, maxY), cellHeight, minY, maxY);
    }

    /**
     * Where to start searching a column for the surface: just above vanilla's preliminary surface level's cell.
     */
    private static int scanStart(DensityFunction preliminaryDensity, int x, int z, int cellHeight, int minY, int maxY) {
        double preliminarySurface = preliminarySurfaceHeight(preliminaryDensity, x, z, cellHeight, minY, maxY);
        return Mth.clamp(Math.floorDiv((int) preliminarySurface, cellHeight) * cellHeight + PRELIMINARY_SURFACE_OFFSET, minY, maxY);
    }

    /**
     * Finds the surface from the final density at the column's own position, walking up or down a cell at a time
     * from the start and interpolating between cells. Fast, but see {@link #interpolatedSurface} for its errors.
     */
    private static double pointDensitySurface(DensityFunction density, int x, int z, int start, int cellHeight, int minY, int maxY) {
        int y = start;
        double densityHere = density(density, x, y, z);
        if (densityHere > 0) {
            // Started underground. Walk up to the first non-solid cell.
            while (y + cellHeight <= maxY) {
                double densityAbove = density(density, x, y + cellHeight, z);
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
            densityHere = density(density, x, y, z);
            if (densityHere > 0) {
                return y + densityHere / (densityHere - densityAbove) * cellHeight;
            }
            densityAbove = densityHere;
        }
        return minY;
    }

    /**
     * Finds the surface from the final density as vanilla generates it, with its interpolated parts interpolated
     * between cell corners. Matches the generated terrain to the block, except for changes made outside the density,
     * such as carvers and structures' terrain adaptation, but is much slower than {@link #pointDensitySurface}.
     * <p>
     * Climbs from the start a cell at a time until clear of the terrain, then scans down block by block. Scanning by
     * cell instead could skip the surface: noodle caves aren't linear within a cell, so a cell's corner can be in a
     * noodle cave with solid ground just above it. Corner values are cached, so scanning by block costs little more.
     */
    private static double interpolatedSurface(DensityFunction density, int x, int z, int start, int cellHeight, int minY, int maxY) {
        // A single non-solid cell may be a cave inside a hill, which would give the cave's floor, so require several
        int clearFrom = start;
        int clearCells = 0;
        int clearTo = start;
        while (clearCells < OPEN_AIR_CELLS) {
            int checkY = clearFrom + clearCells * cellHeight;
            if (checkY > maxY) {
                if (clearCells == 0) {
                    return maxY;
                }
                break; // Nothing above the build limit
            }
            if (density(density, x, checkY, z) > 0) {
                // Found a solid cell, so the clear stretch ended. Start over from the next cell.
                clearFrom = checkY + cellHeight;
                clearCells = 0;
            } else {
                clearTo = checkY;
                clearCells++;
            }
        }

        // Scan down from the top of the clear stretch, whose lower cells may still be in a noodle cave
        double densityAbove = density(density, x, clearTo, z);
        for (int y = clearTo - 1; y >= minY; y--) {
            double densityHere = density(density, x, y, z);
            if (densityHere > 0) {
                return y + densityHere / (densityHere - densityAbove);
            }
            densityAbove = densityHere;
        }
        return minY;
    }

    private static double density(DensityFunction density, int x, int y, int z) {
        return density.compute(new DensityFunction.SinglePointContext(x, y, z));
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
        return withColumnCaches(density, 1);
    }

    private static DensityFunction withColumnCaches(DensityFunction density, int maxColumns) {
        return density.mapAll(function ->
                function instanceof DensityFunctions.MarkerOrMarked marker
                        && (marker.type() == FLAT_CACHE_MARKER_TYPE || marker.type() == CACHE_2D_MARKER_TYPE)
                        ? new ColumnCache(marker.wrapped(), maxColumns)
                        : function);
    }

    /**
     * Like {@link #withColumnCaches}, but also interpolates the parts vanilla interpolates between cell corners,
     * so the result matches the generated terrain. Interpolation samples the four columns at a cell's corners in
     * turn, so this caches several columns.
     */
    private static DensityFunction withCellInterpolation(DensityFunction density, int cellWidth, int cellHeight) {
        return withColumnCaches(density.mapAll(function ->
                function instanceof DensityFunctions.MarkerOrMarked marker && marker.type() == INTERPOLATED_MARKER_TYPE
                        ? new CellInterpolated(marker.wrapped(), cellWidth, cellHeight)
                        : function), 1024);
    }

    /**
     * Returns the biome at sea level for the given column, queried directly from the biome source.
     */
    public Holder<Biome> biomeAt(int x, int z) {
        return biomeAt(x, seaLevel(), z);
    }

    /**
     * Returns the biome at the given block, queried directly from the biome source. Biomes vary with height, so the
     * biome at the surface is the one seen there.
     */
    public Holder<Biome> biomeAt(int x, int y, int z) {
        return this.serverLevel.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(y),
                QuartPos.fromBlock(z),
                this.serverLevel.getChunkSource().randomState().sampler());
    }

    /**
     * Whether a column with the given sampled surface height is covered by water.
     * Water fills up to the block below sea level, so a surface between that block's bottom and sea level is dry
     * ground level with the water's surface, such as a beach or riverbank.
     */
    public static boolean isUnderwater(double surfaceHeight, int seaLevel) {
        return surfaceHeight < seaLevel - 1;
    }

    public boolean isOcean(int x, int z) {
        return biomeAt(x, z).is(BiomeTags.IS_OCEAN);
    }

    public int seaLevel() {
        return this.serverLevel.getSeaLevel();
    }

    /**
     * Caches the wrapped function's value for the most recently sampled column, and optionally more recent ones.
     * Only valid for functions that don't vary with y. Not thread-safe.
     */
    private static final class ColumnCache implements DensityFunction {
        private final DensityFunction wrapped;
        private final int maxColumns;
        private boolean hasValue = false;
        private int lastX, lastZ;
        private double lastValue;

        /** Values for other recent columns, or null if only the last column is cached. */
        private final Long2DoubleOpenHashMap values;

        private ColumnCache(DensityFunction wrapped, int maxColumns) {
            this.wrapped = wrapped;
            this.maxColumns = maxColumns;
            this.values = maxColumns > 1 ? new Long2DoubleOpenHashMap() : null;
            if (this.values != null) {
                this.values.defaultReturnValue(Double.NaN);
            }
        }

        @Override
        public double compute(FunctionContext context) {
            int x = context.blockX();
            int z = context.blockZ();
            if (this.hasValue && x == this.lastX && z == this.lastZ) {
                return this.lastValue;
            }

            double value = Double.NaN;
            if (this.values != null) {
                value = this.values.get(GridKeys.pack(x, z));
            }
            if (Double.isNaN(value)) {
                value = this.wrapped.compute(context);
                if (this.values != null) {
                    if (this.values.size() >= this.maxColumns) {
                        this.values.clear();
                    }
                    this.values.put(GridKeys.pack(x, z), value);
                }
            }
            this.lastX = x;
            this.lastZ = z;
            this.lastValue = value;
            this.hasValue = true;
            return value;
        }

        @Override
        public void fillArray(double[] values, ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new ColumnCache(this.wrapped.mapAll(visitor), this.maxColumns));
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

    /**
     * Evaluates the wrapped function only at the corners of the cell containing each point, and interpolates between
     * them trilinearly, the same as {@code NoiseChunk} does during generation. Corner values are cached, since
     * neighboring points share them. Not thread-safe.
     */
    private static final class CellInterpolated implements DensityFunction {
        private static final int MAX_CORNERS = 16384;

        private final DensityFunction wrapped;
        private final int cellWidth, cellHeight;
        private final Long2DoubleOpenHashMap corners = new Long2DoubleOpenHashMap();

        private CellInterpolated(DensityFunction wrapped, int cellWidth, int cellHeight) {
            this.wrapped = wrapped;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.corners.defaultReturnValue(Double.NaN);
        }

        @Override
        public double compute(FunctionContext context) {
            int x0 = Math.floorDiv(context.blockX(), this.cellWidth) * this.cellWidth;
            int y0 = Math.floorDiv(context.blockY(), this.cellHeight) * this.cellHeight;
            int z0 = Math.floorDiv(context.blockZ(), this.cellWidth) * this.cellWidth;
            int x1 = x0 + this.cellWidth;
            int y1 = y0 + this.cellHeight;
            int z1 = z0 + this.cellWidth;
            return Mth.lerp3(
                    (context.blockX() - x0) / (double) this.cellWidth,
                    (context.blockY() - y0) / (double) this.cellHeight,
                    (context.blockZ() - z0) / (double) this.cellWidth,
                    corner(x0, y0, z0), corner(x1, y0, z0), corner(x0, y1, z0), corner(x1, y1, z0),
                    corner(x0, y0, z1), corner(x1, y0, z1), corner(x0, y1, z1), corner(x1, y1, z1));
        }

        private double corner(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            double value = this.corners.get(key);
            if (Double.isNaN(value)) {
                if (this.corners.size() >= MAX_CORNERS) {
                    this.corners.clear();
                }
                value = this.wrapped.compute(new SinglePointContext(x, y, z));
                this.corners.put(key, value);
            }
            return value;
        }

        @Override
        public void fillArray(double[] values, ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new CellInterpolated(this.wrapped.mapAll(visitor), this.cellWidth, this.cellHeight));
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
            throw new UnsupportedOperationException("CellInterpolated is a runtime-only wrapper and can't be serialized");
        }
    }
}
