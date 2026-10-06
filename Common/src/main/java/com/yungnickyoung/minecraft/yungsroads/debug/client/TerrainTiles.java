package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.util.GridKeys;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Map tiles showing the terrain exactly as road routing sees it: one pixel per pathfinding lattice point, using the
 * same height samples. Tiles are sampled in the background and colored on the render thread.
 * <p>
 * Must only be used on the render thread.
 */
public final class TerrainTiles {
    /** The width of a tile, in lattice points (and pixels). */
    static final int TILE_SIZE = 64;

    /** Samples are taken one lattice point past each edge, so slopes can be computed for edge pixels. */
    private static final int SAMPLED_SIZE = TILE_SIZE + 2;

    private static final int MAX_CACHED_TILES = 256;

    /** The most tiles sampled at once. Kept small so tiles that scroll out of view aren't left in a long queue. */
    private static final int MAX_IN_FLIGHT = 4;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "YUNG's Roads map terrain sampler");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger();

    private static final int OCEAN_COLOR = 0xFF1A2E5A;
    private static final int WATER_COLOR = 0xFF3C6EC8;

    public enum Layer {
        NONE("Off"),
        HEIGHT("Height"),
        /** Grade relative to the max grade. */
        SLOPE("Slope"),
        /** The cost multiplier routing applies to each step. */
        COST("Cost");

        public final String displayName;

        Layer(String displayName) {
            this.displayName = displayName;
        }
    }

    private final ServerLevel level;
    private final TerrainSampler sampler;
    private final int step;
    private final int seaLevel;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Map<Long, Tile> tiles = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Tile> eldest) {
            if (size() > MAX_CACHED_TILES) {
                eldest.getValue().release();
                return true;
            }
            return false;
        }
    };

    TerrainTiles(ServerLevel level, int step) {
        this.level = level;
        this.sampler = ((IStructureRegionCacheProvider) level).getStructureRegionCache().getStructureRegionGenerator().getTerrainSampler();
        this.step = step;
        this.seaLevel = this.sampler.seaLevel();
    }

    boolean isFor(ServerLevel level, int step) {
        return this.level == level && this.step == step;
    }

    int step() {
        return this.step;
    }

    /**
     * Returns the tile's texture colored for the given layer and settings, or null if the tile hasn't been sampled yet.
     * Requests sampling if needed and there's capacity.
     */
    @Nullable
    ResourceLocation texture(int tileX, int tileZ, Layer layer, ConfigModule.Advanced settings) {
        Tile tile = this.tiles.computeIfAbsent(GridKeys.pack(tileX, tileZ), key -> new Tile(tileX, tileZ));
        if (tile.heights == null) {
            if (!tile.requested && this.inFlight.get() < MAX_IN_FLIGHT) {
                tile.requested = true;
                this.inFlight.incrementAndGet();
                EXECUTOR.execute(() -> {
                    try {
                        tile.sample();
                    } catch (RuntimeException e) {
                        YungsRoadsCommon.LOGGER.error("Unable to sample terrain for map tile {}, {}", tileX, tileZ, e);
                    } finally {
                        this.inFlight.decrementAndGet();
                    }
                });
            }
            return null;
        }
        return tile.texture(layer, settings);
    }

    /** The sampled surface height at the lattice point nearest the block, NaN for oceans, or null if not sampled. */
    @Nullable
    Double heightAt(int x, int z) {
        int i = Math.floorDiv(x + this.step / 2, this.step);
        int j = Math.floorDiv(z + this.step / 2, this.step);
        Tile tile = this.tiles.get(GridKeys.pack(Math.floorDiv(i, TILE_SIZE), Math.floorDiv(j, TILE_SIZE)));
        if (tile == null || tile.heights == null) {
            return null;
        }
        return tile.heightAtLattice(Math.floorMod(i, TILE_SIZE), Math.floorMod(j, TILE_SIZE));
    }

    /**
     * The steepest grade from the lattice point nearest the block to any of its neighbors, or null if not sampled.
     * Infinite next to oceans, which routing can't enter.
     */
    @Nullable
    Double gradeAt(int x, int z) {
        int i = Math.floorDiv(x + this.step / 2, this.step);
        int j = Math.floorDiv(z + this.step / 2, this.step);
        Tile tile = this.tiles.get(GridKeys.pack(Math.floorDiv(i, TILE_SIZE), Math.floorDiv(j, TILE_SIZE)));
        if (tile == null || tile.heights == null) {
            return null;
        }
        return tile.maxGrade(Math.floorMod(i, TILE_SIZE), Math.floorMod(j, TILE_SIZE));
    }

    /**
     * The cost multiplier routing applies to a step on land with the given grade, matching {@code LatticePathfinder}.
     */
    double stepCost(double grade, ConfigModule.Advanced settings) {
        return 1 + settings.slopeWeight * grade * grade;
    }

    boolean isWater(double height) {
        return TerrainSampler.isUnderwater(height, this.seaLevel);
    }

    void close() {
        this.tiles.values().forEach(Tile::release);
        this.tiles.clear();
    }

    private final class Tile {
        private final int tileX, tileZ;
        private volatile double[] heights;
        private boolean requested = false;

        @Nullable
        private DynamicTexture texture;
        @Nullable
        private ResourceLocation textureId;
        @Nullable
        private String coloredFor;

        Tile(int tileX, int tileZ) {
            this.tileX = tileX;
            this.tileZ = tileZ;
        }

        /** Samples heights on a worker thread. */
        void sample() {
            TerrainCache cache = new TerrainCache(sampler, step);
            double[] sampled = new double[SAMPLED_SIZE * SAMPLED_SIZE];
            int baseI = this.tileX * TILE_SIZE - 1;
            int baseJ = this.tileZ * TILE_SIZE - 1;
            for (int sj = 0; sj < SAMPLED_SIZE; sj++) {
                for (int si = 0; si < SAMPLED_SIZE; si++) {
                    sampled[sj * SAMPLED_SIZE + si] = cache.heightAt(baseI + si, baseJ + sj);
                }
            }
            this.heights = sampled;
        }

        /** Height at a lattice point relative to the tile's first pixel, which may be one past either edge. */
        double heightAtLattice(int i, int j) {
            return this.heights[(j + 1) * SAMPLED_SIZE + (i + 1)];
        }

        /** The height a road would sit at, the same as in routing: bridges sit at sea level. */
        double roadHeight(int i, int j) {
            double height = heightAtLattice(i, j);
            return Double.isNaN(height) ? Double.NaN : Math.max(height, seaLevel);
        }

        double maxGrade(int i, int j) {
            double center = roadHeight(i, j);
            if (Double.isNaN(center)) {
                return Double.POSITIVE_INFINITY;
            }
            double max = 0;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    if (di == 0 && dj == 0) {
                        continue;
                    }
                    double neighbor = roadHeight(i + di, j + dj);
                    if (Double.isNaN(neighbor)) {
                        continue; // Oceans are impassable, rather than steep
                    }
                    double run = Math.sqrt(di * di + dj * dj) * step;
                    max = Math.max(max, Math.abs(neighbor - center) / run);
                }
            }
            return max;
        }

        ResourceLocation texture(Layer layer, ConfigModule.Advanced settings) {
            if (this.texture == null) {
                this.texture = new DynamicTexture(TILE_SIZE, TILE_SIZE, false);
                this.textureId = YungsRoadsCommon.id("map_tile_" + TEXTURE_COUNTER.incrementAndGet());
                Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
            }

            String key = layer + "/" + settings.maxGrade + "/" + settings.slopeWeight;
            if (!key.equals(this.coloredFor)) {
                NativeImage pixels = this.texture.getPixels();
                for (int j = 0; j < TILE_SIZE; j++) {
                    for (int i = 0; i < TILE_SIZE; i++) {
                        int argb = color(i, j, layer, settings);
                        // NativeImage stores pixels as ABGR
                        pixels.setPixelRGBA(i, j, FastColor.ABGR32.fromArgb32(argb));
                    }
                }
                this.texture.upload();
                this.coloredFor = key;
            }
            return this.textureId;
        }

        private int color(int i, int j, Layer layer, ConfigModule.Advanced settings) {
            double height = heightAtLattice(i, j);
            if (Double.isNaN(height)) {
                return OCEAN_COLOR;
            }
            if (isWater(height) && layer != Layer.HEIGHT) {
                // Roads only cross water on bridges, whose cost depends on their length rather than the terrain
                return WATER_COLOR;
            }
            return switch (layer) {
                case NONE -> 0;
                case HEIGHT -> heightColor(i, j, height);
                case SLOPE -> {
                    double ratio = maxGrade(i, j) / settings.maxGrade;
                    yield ratio > 1 ? 0xFF6A1B5A : gradient(ratio);
                }
                case COST -> {
                    double grade = maxGrade(i, j);
                    if (grade > settings.maxGrade) {
                        yield 0xFF6A1B5A;
                    }
                    // Log scale, so both gentle and steep differences are visible. A cost of 50x or more is full red.
                    yield gradient(Math.log(stepCost(grade, settings)) / Math.log(50));
                }
            };
        }

        private int heightColor(int i, int j, double height) {
            if (isWater(height)) {
                return WATER_COLOR;
            }
            double t = Mth.clamp((height - seaLevel) / 160, 0, 1);
            int color = t < 0.5
                    ? lerpColor(0xFF4C9A3C, 0xFFB49A64, t * 2)
                    : lerpColor(0xFFB49A64, 0xFFF0F0F0, (t - 0.5) * 2);

            // Light from the west, so the shape of the land is easier to read
            double west = heightAtLattice(i - 1, j);
            double east = heightAtLattice(i + 1, j);
            if (!Double.isNaN(west) && !Double.isNaN(east)) {
                double shade = Mth.clamp(1 + (west - east) / (2.0 * step) * 0.6, 0.65, 1.2);
                color = scaleColor(color, shade);
            }
            return color;
        }

        void release() {
            if (this.textureId != null) {
                Minecraft.getInstance().getTextureManager().release(this.textureId);
                this.texture = null;
                this.textureId = null;
            }
        }
    }

    /** Green to yellow to red, for values from 0 to 1. */
    private static int gradient(double t) {
        t = Mth.clamp(t, 0, 1);
        return t < 0.5
                ? lerpColor(0xFF3CB44B, 0xFFE6C832, t * 2)
                : lerpColor(0xFFE6C832, 0xFFE6503C, (t - 0.5) * 2);
    }

    private static int lerpColor(int from, int to, double t) {
        return FastColor.ARGB32.lerp((float) t, from, to);
    }

    private static int scaleColor(int color, double factor) {
        int r = Mth.clamp((int) (FastColor.ARGB32.red(color) * factor), 0, 255);
        int g = Mth.clamp((int) (FastColor.ARGB32.green(color) * factor), 0, 255);
        int b = Mth.clamp((int) (FastColor.ARGB32.blue(color) * factor), 0, 255);
        return FastColor.ARGB32.color(255, r, g, b);
    }
}
