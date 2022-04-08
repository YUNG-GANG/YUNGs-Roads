package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

public class SplineRoadGenerator implements IRoadGenerator {
    private final ServerLevel serverLevel;
    private final ThreadLocal<WorldgenRandom> random = ThreadLocal.withInitial(() -> new WorldgenRandom(new LegacyRandomSource(0)));
    private final FastNoise noise;

    public SplineRoadGenerator(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
        this.noise = new FastNoise();
        this.noise.SetNoiseType(FastNoise.NoiseType.Simplex);
        this.noise.SetFrequency(.012f);
        this.noise.SetFractalOctaves(1);
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.getWorldPosition() : pos2.getWorldPosition();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.getWorldPosition() : pos1.getWorldPosition();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();

        random.get().setLargeFeatureWithSalt(serverLevel.getSeed(), pos1.x, 0, pos1.z);

        // Construct road & road segments
        Road road = new Road(blockPos1, blockPos2);
        int numSegments = 6;
        for (int i = 0; i < numSegments; i++) {
            BlockPos.MutableBlockPos startPos = blockPos1.offset(xDist * i / numSegments, 0, zDist * i / numSegments).mutable();
            BlockPos.MutableBlockPos endPos = blockPos1.offset(xDist * (i + 1) / numSegments, 0, zDist * (i + 1) / numSegments).mutable();
            road.addSplineRoadSegment(startPos, endPos, random.get());
        }

        // Ensure road does not cross ocean
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (serverLevel.getChunkSource().getGenerator().getNoiseBiome(
                            QuartPos.fromBlock(roadSegment.getStartPos().getX()),
                            QuartPos.fromBlock(0),
                            QuartPos.fromBlock(roadSegment.getStartPos().getZ()))
                    .is(BiomeTags.IS_OCEAN)) {
                return Optional.empty();
            }
        }

        return Optional.of(road);
    }

    @Override
    public void placeRoad(Road road, WorldGenLevel level, Random rand, BlockPos blockPos, @Nullable BlockPos nearestVillage) {
        ChunkPos chunkPos = new ChunkPos(blockPos);

        // Short-circuit if this chunk isn't between the start/end points of the road
        if (!containsRoad(chunkPos, road)) {
            return;
        }

        // Determine road segments we need to process for this chunk
        List<RoadSegment> roadSegments = new ArrayList<>();
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (containsRoadSegment(chunkPos, roadSegment)) {
                roadSegments.add(roadSegment);
            }
        }

        // Set seeds
        random.get().setLargeFeatureSeed(level.getSeed(), road.getVillageStart().getX() >> 4, road.getVillageEnd().getZ() >> 4);

        // Temporary chunk-local carving mask to prevent overprocessing a single block
        BitSet blockMask = new BitSet(65536);

        for (RoadSegment roadSegment : roadSegments) {
            Vec3[] pts = Arrays.stream(roadSegment.getPoints()).map(pos -> new Vec3(pos.getX(), pos.getY(), pos.getZ())).toArray(Vec3[]::new);

            // Debug markers
            if (YungsRoadsCommon.DEBUG_MODE) {
                // Start pos
                if (isInChunk(chunkPos, new BlockPos(pts[0].x, pts[0].y, pts[0].z))) {
                    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pts[0].x, pts[0].y, pts[0].z);
                    mutable.setY(getSurfaceHeight(level, mutable));

                    for (int y = 0; y < 10; y++) {
                        mutable.move(Direction.UP);
                        level.setBlock(mutable, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                    }
                }
                // End pos
                if (isInChunk(chunkPos, new BlockPos(pts[3].x, pts[3].y, pts[3].z))) {
                    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pts[3].x, pts[3].y, pts[3].z);
                    mutable.setY(getSurfaceHeight(level, mutable));

                    for (int y = 0; y < 10; y++) {
                        mutable.move(Direction.UP);
                        level.setBlock(mutable, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                    }
                }
            }

            // Bezier curve path placement
            float t = 0;
            Vec3 posVec;
            BlockPos.MutableBlockPos pathPos = new BlockPos.MutableBlockPos();
            Vec3[] normals;
            int counter = 0;

            while (t <= 1f) {
                posVec = getBezierPoint(pts, t);
                pathPos.set(Math.round(posVec.x), Math.round(posVec.y), Math.round(posVec.z));

                if (isInValidRangeForChunk(chunkPos, pathPos)) {
                    // Place path at this point
//                    placePath(world, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
                    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

                    if (isInChunk(chunkPos, pathPos)) {
                        // At each path position, we place a small circle of blocks at surface height
                        for (int x = -2; x < 3; x++) {
                            for (int z = -2; z < 3; z++) {
                                // Determine path buffer space at this position.
                                // This is used to subtly vary the path's width to make its shape more interesting.
                                double pathBufferSpace = noise.GetNoise(mutable.getX(), mutable.getZ()) * 4;

                                // Determine the furthest away a block can be placed from the current position.
                                // Distances are kept as squared values as an optimization.
                                double maxRoadDistSq = getRoadSizeRadius() * getRoadSizeRadius() + pathBufferSpace;
                                if (x * x + z * z < maxRoadDistSq) {
                                    mutable.set(pathPos.getX() + x, 0, pathPos.getZ() + z);

                                    // Adjust y-coordinate based on surface height
                                    int surfaceHeight = getSurfaceHeight(level, mutable);
                                    int currY = Math.min(surfaceHeight, 80); // Height of path cannot exceed y=80
                                    mutable.setY(currY);

                                    placePathBlock(level, random.get(), mutable, nearestVillage, blockMask);

                                    // If the surface is above y=80, we carve an opening to tunnel through the terrain.
                                    if (currY == 80) {
                                        for (int i = 0; i < 4; i++) {
                                            mutable.move(Direction.UP);
                                            level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 2);
                                        }
                                    }
                                }
                            }
                        }

                        // Debug markers
                        if (YungsRoadsCommon.DEBUG_MODE) {
                            if (counter == 200 || counter == 400 || counter == 600 || counter == 800) {
                                mutable.setY(getSurfaceHeight(level, mutable));

                                for (int y = 0; y < 10; y++) {
                                    mutable.move(Direction.UP);
                                    level.setBlock(mutable, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
                                }
                            }
                        }
                    }

                    // Place normals at this point
//                    normals = getNormals(pts, t);
//                    for (Vector3d normal : normals) {
//                        for (int normalOffset = 0; normalOffset < 3; normalOffset++) {
//                            // Inner normal
//                            mutable.setPos(pathPos);
//                            mutable.move((int) Math.round(normal.getX() * (getRoadWidth() + normalOffset)), 0, (int) Math.round(normal.getZ() * (getRoadWidth() + normalOffset)));
//                            mutable.setY(getSurfaceHeight(world, mutable));
//                            BlockState surfaceBlock = world.getBlockState(mutable);
//                            int seaLevelDistance = mutable.getY() - world.getSeaLevel();
//                            int yCompression = seaLevelDistance / (10 + (3 * normalOffset));
//                            // Place air to destroy any floating plants and the like
//                            if (yCompression > 0) {
//                                mutable.move(Direction.UP);
//                                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 2);
//                                mutable.move(Direction.DOWN);
//                            }
//                            for (int y = 0; y < yCompression; y++) {
//                                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 2);
//                                mutable.move(Direction.DOWN);
//                            }
//                            world.setBlockState(mutable, surfaceBlock, 2);
//                        }
//                    }
                }
                t += 0.002f;
                counter++;
            }

            YungsRoadsCommon.LOGGER.debug("Generated {}", roadSegment);
        }
    }

    @Override
    public double getRoadSizeRadius() {
        return 2;
    }

    /**
     * Get point for given t-value.
     * This is a standard Bezier curve implementation.
     */
    private Vec3 getBezierPoint(Vec3[] pts, float t) {
        float omt = 1f - t;
        float omt2 = omt * omt;
        float t2 = t * t;
        return pts[0].scale(omt2 * omt).add(
                pts[1].scale(3f * omt2 * t)).add(
                pts[2].scale(3f * omt * t2)).add(
                pts[3].scale(t2 * t));
    }

    private Vec3 getTangent(Vec3[] pts, float t) {
        double omt = 1f - t;
        double omt2 = omt * omt;
        double t2 = t * t;
        Vec3 tangent = pts[0].scale(-omt2).add(
                pts[1].scale(3f * omt2 - 2 * omt)).add(
                pts[2].scale(-3f * t2 + 2 * t)).add(
                pts[3].scale(t2));
        return tangent.normalize();
    }
}
