package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws road routes as lines along the ground, so routes can be inspected in the world without placing blocks.
 * <p>
 * Each route is drawn twice: faintly through terrain, so routes behind hills stay visible, then fully where it's not
 * hidden. Roads replaced by the most recent settings change are drawn in red above the current ones.
 */
public final class RoadOverlayRenderer {
    private static final int PREVIOUS_ROAD_COLOR = 0xFFFF4040;
    private static final int NODE_COLOR = 0xFFFFFFFF;
    private static final int ENDPOINT_COLOR = 0xFF40FF40;
    private static final float NODE_HEIGHT = 1.5f;
    private static final float ENDPOINT_HEIGHT = 16f;

    /**
     * How far above the ground lines are drawn, to keep them from flickering against it.
     * Must clear thin layers on the ground, such as snow.
     */
    private static final float LINE_OFFSET = 0.25f;
    private static final float PREVIOUS_LINE_OFFSET = 0.5f;

    /**
     * Ground heights by column. Finding the ground below trees takes several block lookups, so heights are cached,
     * and refreshed every so often to pick up block changes such as newly placed roads.
     */
    private static final Long2FloatOpenHashMap GROUND_HEIGHTS = new Long2FloatOpenHashMap();
    private static final int GROUND_CACHE_LIFETIME_FRAMES = 40;
    private static int framesSinceGroundCacheCleared = 0;

    private RoadOverlayRenderer() {
    }

    static void render(ServerLevel serverLevel, Camera camera) {
        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return;
        }

        if (++framesSinceGroundCacheCleared >= GROUND_CACHE_LIFETIME_FRAMES) {
            GROUND_HEIGHTS.clear();
            framesSinceGroundCacheCleared = 0;
        }

        Vec3 cameraPos = camera.getPosition();
        int range = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
        List<StructureRegion> currentRegions = regionsNear(serverLevel, cameraPos, range);
        List<StructureRegion> previousRegions = new ArrayList<>();
        if (RoadDebugClient.showPreviousRoads) {
            // Regions that the change didn't replace are the same objects, and would only be drawn twice
            Set<StructureRegion> current = new HashSet<>(currentRegions);
            for (StructureRegion region : RoadTuning.previousRegions(serverLevel)) {
                if (!current.contains(region)) {
                    previousRegions.add(region);
                }
            }
        }

        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // Faint lines through terrain
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(3f);
        draw(clientLevel, cameraPos, range, currentRegions, previousRegions, 0x60);

        // Solid lines where not hidden
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(6f);
        draw(clientLevel, cameraPos, range, currentRegions, previousRegions, 0xFF);

        RenderSystem.lineWidth(1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void draw(ClientLevel level, Vec3 cameraPos, int range, Collection<StructureRegion> currentRegions,
                             Collection<StructureRegion> previousRegions, int alpha) {
        LineBuilder lines = new LineBuilder(Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL), cameraPos);
        for (StructureRegion region : previousRegions) {
            for (Road road : region.getRoads()) {
                drawRoute(lines, level, cameraPos, range, road, withAlpha(PREVIOUS_ROAD_COLOR, alpha), PREVIOUS_LINE_OFFSET);
            }
        }
        for (StructureRegion region : currentRegions) {
            for (Road road : region.getRoads()) {
                drawRoute(lines, level, cameraPos, range, road, withAlpha(roadColor(road), alpha), LINE_OFFSET);
                drawMarker(lines, level, cameraPos, range, road.getStartPos(), ENDPOINT_HEIGHT, withAlpha(ENDPOINT_COLOR, alpha));
                drawMarker(lines, level, cameraPos, range, road.getEndPos(), ENDPOINT_HEIGHT, withAlpha(ENDPOINT_COLOR, alpha));
                if (RoadDebugClient.showNodes) {
                    for (Road.DebugNode node : road.nodes) {
                        drawMarker(lines, level, cameraPos, range, node.jitteredPos, NODE_HEIGHT, withAlpha(NODE_COLOR, alpha));
                    }
                }
            }
        }

        MeshData mesh = lines.buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * Draws the road's center line through its jittered nodes, following the ground one block at a time.
     */
    private static void drawRoute(LineBuilder lines, ClientLevel level, Vec3 cameraPos, int range, Road road, int color, float offset) {
        for (int n = 0; n + 1 < road.nodes.size(); n++) {
            BlockPos from = road.nodes.get(n).jitteredPos;
            BlockPos to = road.nodes.get(n + 1).jitteredPos;
            if (!isInRange(from, cameraPos, range) && !isInRange(to, cameraPos, range)) {
                continue;
            }

            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(from.distSqr(to))));
            double prevX = from.getX() + 0.5, prevZ = from.getZ() + 0.5;
            float prevY = groundHeight(level, from.getX(), from.getZ());
            for (int s = 1; s <= steps; s++) {
                double x = Mth.lerp(s / (double) steps, from.getX(), to.getX()) + 0.5;
                double z = Mth.lerp(s / (double) steps, from.getZ(), to.getZ()) + 0.5;
                float y = groundHeight(level, Mth.floor(x), Mth.floor(z));
                // Skip segments over chunks the client hasn't loaded, which have no height
                if (!Float.isNaN(prevY) && !Float.isNaN(y)) {
                    lines.line(prevX, prevY + offset, prevZ, x, y + offset, z, color);
                }
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        }
    }

    private static void drawMarker(LineBuilder lines, ClientLevel level, Vec3 cameraPos, int range, BlockPos pos, float height, int color) {
        if (!isInRange(pos, cameraPos, range)) {
            return;
        }
        float y = groundHeight(level, pos.getX(), pos.getZ());
        if (!Float.isNaN(y)) {
            lines.line(pos.getX() + 0.5, y, pos.getZ() + 0.5, pos.getX() + 0.5, y + height, pos.getZ() + 0.5, color);
        }
    }

    /**
     * The top of the ground at the given column, below any trees, or NaN if the client doesn't have the chunk.
     * The client only has heightmaps that include leaves, so trees are skipped by checking blocks.
     */
    private static float groundHeight(ClientLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return Float.NaN;
        }
        long key = ChunkPos.asLong(x, z);
        if (GROUND_HEIGHTS.containsKey(key)) {
            return GROUND_HEIGHTS.get(key);
        }

        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        while (below.getY() > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(below);
            if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
                break;
            }
            below.move(Direction.DOWN);
        }
        float height = below.getY() + 1;
        GROUND_HEIGHTS.put(key, height);
        return height;
    }

    private static boolean isInRange(BlockPos pos, Vec3 cameraPos, int range) {
        double dx = pos.getX() - cameraPos.x;
        double dz = pos.getZ() - cameraPos.z;
        return dx * dx + dz * dz <= (double) range * range;
    }

    private static List<StructureRegion> regionsNear(ServerLevel level, Vec3 cameraPos, int range) {
        StructureRegionCache cache = ((IStructureRegionCacheProvider) level).getStructureRegionCache();
        List<StructureRegion> regions = new ArrayList<>();
        for (long regionKey : StructureRegionCache.regionKeysNearArea(
                (int) cameraPos.x - range, (int) cameraPos.z - range, (int) cameraPos.x + range, (int) cameraPos.z + range)) {
            // Don't trigger generation from the render thread
            StructureRegion region = cache.getRegionIfLoaded(regionKey);
            if (region != null) {
                regions.add(region);
            }
        }
        return regions;
    }

    /** A distinct, stable color for each road, so neighboring roads can be told apart. */
    static int roadColor(Road road) {
        int hash = road.getStartPos().hashCode() * 31 + road.getEndPos().hashCode();
        float hue = (hash & 0xFFFF) / (float) 0xFFFF;
        return 0xFF000000 | Mth.hsvToRgb(hue, 0.65f, 1f);
    }

    static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    /** Adds camera-relative line segments to a buffer using the lines shader's vertex format. */
    private record LineBuilder(BufferBuilder buffer, Vec3 cameraPos) {
        void line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0) {
                return;
            }
            dx /= length;
            dy /= length;
            dz /= length;
            this.buffer.addVertex((float) (x1 - this.cameraPos.x), (float) (y1 - this.cameraPos.y), (float) (z1 - this.cameraPos.z))
                    .setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color))
                    .setNormal(dx, dy, dz);
            this.buffer.addVertex((float) (x2 - this.cameraPos.x), (float) (y2 - this.cameraPos.y), (float) (z2 - this.cameraPos.z))
                    .setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color))
                    .setNormal(dx, dy, dz);
        }
    }
}
