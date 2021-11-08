package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Road {
    public static final Codec<Road> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("villageStart").forGetter(Road::getVillageStart),
            BlockPos.CODEC.fieldOf("villageEnd").forGetter(Road::getVillageEnd),
            RoadSegment.CODEC.listOf().fieldOf("roadSegments").forGetter(Road::getRoadSegments))
        .apply(builder, Road::new));

    private final BlockPos villageStart;
    private final BlockPos villageEnd;
    private final List<RoadSegment> roadSegments;

    public Road(BlockPos village1, BlockPos village2, List<RoadSegment> roadSegments) {
        this.villageStart = village1.getX() <= village2.getX() ? village1 : village2;
        this.villageEnd = this.villageStart == village1 ? village2 : village1;
        this.roadSegments = roadSegments;
    }

    public Road(BlockPos village1, BlockPos village2) {
        this(village1, village2, new ArrayList<>());
    }


    /**
     * Places the {@link Road} for blocks within the given chunk.
     *
     * @param world The world, passed in during feature generation.
     * @param rand Random passed in during feature generation.
     * @param pos Any {@link BlockPos} in the chunk we want to operate on. Should be passed in during feature generation.
     * @param nearestVillage The location of the nearest village to this point.
     *                       Only used for rendering the debug view.
     */
    public void place(ISeedReader world, Random rand, BlockPos pos, BlockPos nearestVillage) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // Determine total slope of line from starting point to ending point
        int totalXDiff = this.getVillageEnd().getX() - this.getVillageStart().getX();
        int totalZDiff = this.getVillageEnd().getZ() - this.getVillageStart().getZ();
        double totalSlope = totalXDiff == 0 ? Integer.MAX_VALUE : totalZDiff / (double) totalXDiff;
        int xDir = totalXDiff >= 0 ? 1 : -1; // x direction multiplier
        int zDir = totalZDiff >= 0 ? 1 : -1; // z direction multiplier

        // Initialize mutable at starting point
        mutable.setPos(this.getVillageStart());

        YungsRoads.LOGGER.info(this);

        double slopeCounter = Math.abs(totalSlope);

        while (!isInRange(mutable, this.getVillageEnd())) {
            // Move in z direction
            while (slopeCounter >= 1 && !isInRange(mutable, this.getVillageEnd())) {
                placePath(world, rand, mutable, nearestVillage, chunkX, chunkZ);
                mutable.move(0, 0, zDir);
                slopeCounter--;
            }

            while (slopeCounter < 1 && !isInRange(mutable, this.getVillageEnd())) {
                placePath(world, rand, mutable, nearestVillage, chunkX, chunkZ);
                mutable.move(xDir, 0, 0);
                slopeCounter += Math.abs(totalSlope);
            }

            // Place path at final position
            placePath(world, rand, mutable, nearestVillage, chunkX, chunkZ);
        }
    }

    public BlockPos getVillageStart() {
        return villageStart;
    }

    public BlockPos getVillageEnd() {
        return villageEnd;
    }

    public List<RoadSegment> getRoadSegments() {
        return roadSegments;
    }

    public Road addRoadSegment(RoadSegment roadSegment) {
        this.roadSegments.add(roadSegment);
        return this;
    }

    public Road addRoadSegment(BlockPos startPos, BlockPos endPos) {
        RoadSegment roadSegment = new RoadSegment(startPos, endPos);
        return this.addRoadSegment(roadSegment);
    }

    @Override
    public String toString() {
        return String.format("Road %s - %s (%d segments)", villageStart, villageEnd, roadSegments.size());
    }

    private boolean isInRange(BlockPos pos, BlockPos targetPos) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < 10 * 10;
    }

    private void placePath(ISeedReader world, Random random, BlockPos pos, BlockPos nearestVillage, int chunkX, int chunkZ) {
        BlockPos.Mutable mutable = pos.toMutable();

        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (x * x + z * z < 9) {
                    mutable.setPos(pos.getX() + x, pos.getY(), pos.getZ() + z);
                    if (mutable.getX() >> 4 == chunkX && mutable.getZ() >> 4 == chunkZ) {
                        mutable.setY(world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, mutable.getX(), mutable.getZ()) - 1);
                        placePathBlock(world, random, mutable, nearestVillage);
                    }
                }
            }
        }
    }

    private void placePathBlock(ISeedReader world, Random random, BlockPos pos, BlockPos nearestVillage) {
        BlockState currState = world.getBlockState(pos);
        if (random.nextFloat() < .5f) {
            if (currState == Blocks.GRASS_BLOCK.getDefaultState() || currState == Blocks.DIRT.getDefaultState()) {
                world.setBlockState(pos, Blocks.GRASS_PATH.getDefaultState(), 2);
            } else if (currState == Blocks.SAND.getDefaultState() || currState == Blocks.RED_SAND.getDefaultState()) {
                world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 2);
            } else if (currState.getMaterial() == Material.WATER) {
                world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState(), 2);
            }
        }
        DebugRenderer.getInstance().addPath(new ChunkPos(pos), new ChunkPos(nearestVillage));
    }
}
