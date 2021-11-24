package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

public class SplineRoadGenerator implements IRoadGenerator {
    private final ServerWorld world;
    private final SharedSeedRandom random = new SharedSeedRandom();
    private final FastNoise noise;

    public SplineRoadGenerator(ServerWorld world) {
        this.world = world;
        this.noise = new FastNoise();
        this.noise.SetNoiseType(FastNoise.NoiseType.Simplex);
        this.noise.SetFrequency(.012f);
        this.noise.SetFractalOctaves(1);
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.asBlockPos() : pos2.asBlockPos();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.asBlockPos() : pos1.asBlockPos();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();

        random.setLargeFeatureSeedWithSalt(world.getSeed(), pos1.x, 0, pos1.z);

        // Construct road & road segments
        Road road = new Road(blockPos1, blockPos2);
        int numSegments = 6;
        for (int i = 0; i < numSegments; i++) {
            BlockPos.Mutable startPos = blockPos1.add(xDist * i / numSegments, 0, zDist * i / numSegments).toMutable();
            BlockPos.Mutable endPos = blockPos1.add(xDist * (i + 1) / numSegments, 0, zDist * (i + 1) / numSegments).toMutable();
            road.addSplineRoadSegment(startPos, endPos, random);
        }

        // Ensure road does not cross ocean
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (world.getChunkProvider().getChunkGenerator().getBiomeProvider().getNoiseBiome(
                            (roadSegment.getStartPos().getX() >> 2) + 2,
                            0,
                            (roadSegment.getStartPos().getZ() >> 2) + 2)
                    .getCategory() == Biome.Category.OCEAN) {
                return Optional.empty();
            }
        }

        return Optional.of(road);
    }

    @Override
    public void placeRoad(Road road, ISeedReader world, Random rand, ChunkPos chunkPos, BlockPos nearestVillage) {
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
        random.setLargeFeatureSeed(world.getSeed(), road.getVillageStart().getX() >> 4, road.getVillageEnd().getZ() >> 4);

        for (RoadSegment roadSegment : roadSegments) {
            Vector3d[] pts = Arrays.stream(roadSegment.getPoints()).map(blockPos -> new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ())).toArray(Vector3d[]::new);

            // Debug markers
            if (isInChunk(chunkPos, new BlockPos(pts[0].x, pts[0].y, pts[0].z))) {
                BlockPos.Mutable mutable = new BlockPos.Mutable(pts[0].x, pts[0].y, pts[0].z);
                mutable.setY(getSurfaceHeight(world, mutable));

                for (int y = 0; y < 10; y++) {
                    mutable.move(Direction.UP);
                    world.setBlockState(mutable, Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
                }
            }
            if (isInChunk(chunkPos, new BlockPos(pts[3].x, pts[3].y, pts[3].z))) {
                BlockPos.Mutable mutable = new BlockPos.Mutable(pts[3].x, pts[3].y, pts[3].z);
                mutable.setY(getSurfaceHeight(world, mutable));

                for (int y = 0; y < 10; y++) {
                    mutable.move(Direction.UP);
                    world.setBlockState(mutable, Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
                }
            }

            // Bezier curve path placement
            float t = 0;
            Vector3d posVec;
            BlockPos.Mutable pathPos = new BlockPos.Mutable();
            Vector3d[] normals;
            int counter = 0;

            while (t <= 1f) {

                posVec = getPoint(pts, t);
                pathPos.setPos(Math.round(posVec.x), Math.round(posVec.y), Math.round(posVec.z));

                if (isInValidRangeForChunk(chunkPos, pathPos)) {
                    // Place path at this point
//                    placePath(world, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
                    BlockPos.Mutable mutable = new BlockPos.Mutable();

                    if (isInValidRangeForChunk(chunkPos, pathPos)) {
                        for (int x = -2; x < 3; x++) {
                            for (int z = -2; z < 3; z++) {
                                double n = noise.GetNoise(mutable.getX(), mutable.getZ());
                                double width = getRoadWidth() * getRoadWidth() + n * 4;
                                if (x * x + z * z < width) {
                                    mutable.setPos(pathPos.getX() + x, 0, pathPos.getZ() + z);

                                    int surfaceHeight = getSurfaceHeight(world, mutable);
                                    int currY = Math.min(surfaceHeight, 80);
                                    mutable.setY(currY);

                                    placePathBlock(world, random, mutable, nearestVillage);
                                    if (currY == 80) {
                                        for (int i = 0; i < 4; i++) {
                                            mutable.move(Direction.UP);
                                            world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 2);
                                        }
                                    }
                                }
                            }
                        }

                        // Debug markers
                        if (counter == 200 || counter == 400 || counter == 600 || counter == 800) {
                            mutable.setY(getSurfaceHeight(world, mutable));

                            for (int y = 0; y < 10; y++) {
                                mutable.move(Direction.UP);
                                world.setBlockState(mutable, Blocks.GOLD_BLOCK.getDefaultState(), 2);
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
        }

        YungsRoads.LOGGER.info("Generated {}", road);
    }

    @Override
    public double getRoadWidth() {
        return 2;
    }

    /**
     * Get point for given t-value.
     * This is a standard Bezier curve implementation.
     */
    private Vector3d getPoint(Vector3d[] pts, float t) {
        float omt = 1f - t;
        float omt2 = omt * omt;
        float t2 = t * t;
        return pts[0].scale(omt2 * omt).add(
               pts[1].scale(3f * omt2 * t)).add(
               pts[2].scale(3f * omt * t2)).add(
               pts[3].scale(t2 * t));
    }

    private Vector3d getTangent(Vector3d[] pts, float t) {
        double omt = 1f - t;
        double omt2 = omt * omt;
        double t2 = t * t;
        Vector3d tangent = pts[0].scale(-omt2).add(
                           pts[1].scale(3f * omt2 - 2 * omt)).add(
                           pts[2].scale(-3f * t2 + 2 * t)).add(
                           pts[3].scale(t2));
        return tangent.normalize();
    }
}
