package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.StructureAccessor;
import com.yungnickyoung.minecraft.yungsroads.world.road.IRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.LinearRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureRegionGenerator {
    private final int spacing, separation, salt;
    private final ServerWorld world;
    private final SharedSeedRandom random;
    private final IRoadGenerator roadGenerator;

    public StructureRegionGenerator(ServerWorld world) {
        this.world = world;
        this.random = new SharedSeedRandom();
        this.roadGenerator = new LinearRoadGenerator(world);

        // Extract separation settings
        StructureSeparationSettings separationSettings = world.getChunkProvider().getChunkGenerator().func_235957_b_().func_236197_a_(Structure.VILLAGE);
        this.spacing = separationSettings.func_236668_a_();
        this.separation = separationSettings.func_236671_b_();
        this.salt = separationSettings.func_236673_c_();
    }

    /**
     * Generates a new {@link StructureRegion} for the given region key.
     *
     * Uses the structure's separation settings to reconstruct its structure location grid,
     * then validates each position with a biome check. From there, some of the structure locations
     * are randomly selected as endpoints for roads, and the roads are constructed.
     */
    public StructureRegion generateRegion(long regionKey) {
        StructureRegionPos regionPos = new StructureRegionPos(regionKey);
        LongOpenHashSet villageSet = new LongOpenHashSet();
        ChunkPos minChunkPos = regionPos.getMinChunkPosInRegion();
        ChunkPos maxChunkPos = regionPos.getMaxChunkPosInRegion();

        // Begin locating villages in this region
        for (int chunkX = minChunkPos.x; chunkX <= maxChunkPos.x; chunkX++) {
            for (int chunkZ = minChunkPos.z; chunkZ <= maxChunkPos.z; chunkZ++) {
                int xOffset, zOffset;

                // Impose this chunk onto the structure grid.
                // This is determined by the structure's separation settings
                int structureGridX = Math.floorDiv(chunkX, this.spacing);
                int structureGridZ = Math.floorDiv(chunkZ, this.spacing);

                // Initialize random w/ seed for reproducibility
                this.random.setLargeFeatureSeedWithSalt(this.world.getSeed(), structureGridX, structureGridZ, salt);

                // Determine random x/z chunk offset
                if (((StructureAccessor) Structure.VILLAGE).isLinearSeparation()) {
                    xOffset = random.nextInt(spacing - separation);
                    zOffset = random.nextInt(spacing - separation);
                } else {
                    xOffset = (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;
                    zOffset = (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;
                }

                // Calculate the final candidate chunk pos for this chunk
                ChunkPos structureCandidateChunkPos = new ChunkPos(structureGridX * spacing + xOffset, structureGridZ * spacing + zOffset);

                // Add the structure if the containing biome is valid
                if (world.getChunkProvider().getChunkGenerator().getBiomeProvider().getNoiseBiome(
                                (structureCandidateChunkPos.x << 2) + 2,
                                0,
                                (structureCandidateChunkPos.z << 2) + 2)
                        .getGenerationSettings().hasStructure(Structure.VILLAGE)) {
                    villageSet.add(structureCandidateChunkPos.asLong());
                }
            }
        }

        // Create set for storing the village chunks
        LongList villageChunks = new LongArrayList(villageSet);

        // Generate some roads connecting villages
        List<Road> roads = new ArrayList<>();
        int numRoads = random.nextInt(villageChunks.size() / 2);
        int i = 0;
        while (i < numRoads && villageChunks.size() > 1) {
            // Choose first village
            int startIndex = random.nextInt(villageChunks.size());
            ChunkPos startVillage = new ChunkPos(villageChunks.getLong(startIndex));

            // Remove start pos from the list now that it's chosen.
            // We remove the start pos to prevent completely duplicate roads, but keep the end pos
            // to allow for villages with multiple roads
            villageChunks.removeLong(startIndex);

            // Choose second village
            ChunkPos endVillage = null;
            for (Long candidateEnd : villageChunks) {
                ChunkPos endCandidate = new ChunkPos(candidateEnd);

                // End pos must be within 800 blocks of start pos (arbitrary max road length)
                if (startVillage.asBlockPos().withinDistance(endCandidate.asBlockPos(), 800)) {
                    endVillage = endCandidate;
                    break;
                }
            }

            // If we found a second village, attempt to construct a Road connecting the two villages
            if (endVillage != null) {
                Optional<Road> roadOptional = this.roadGenerator.generateRoad(startVillage, endVillage);
                if (roadOptional.isPresent()) {
                    roads.add(roadOptional.get());
                    i++;
                }
            }
        }

        return new StructureRegion(regionKey, villageSet, roads);
    }

    public IRoadGenerator getRoadGenerator() {
        return this.roadGenerator;
    }
}
