package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.google.common.collect.Lists;
import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.ChunkManagerAccessor;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.StructureAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public class StructureRegionCache {
    private final Path savePath;
    private final ServerWorld world;
    private final SharedSeedRandom random;
    private final Long2ObjectLinkedOpenHashMap<StructureRegion> structureRegionCache;
    private final int spacing, separation, salt;

    public StructureRegionCache(ServerWorld world) {
        this.world = world;
        this.random = new SharedSeedRandom();
        this.structureRegionCache = new Long2ObjectLinkedOpenHashMap<>();

        // Extract separation settings
        StructureSeparationSettings separationSettings = world.getChunkProvider().getChunkGenerator().func_235957_b_().func_236197_a_(Structure.VILLAGE);
        this.spacing = separationSettings.func_236668_a_();
        this.separation = separationSettings.func_236671_b_();
        this.salt = separationSettings.func_236673_c_();

        // Create save folder if it doesn't exist
        this.savePath = ((ChunkManagerAccessor) world.getChunkProvider().chunkManager).getDimensionDirectory().toPath().resolve("roads").toAbsolutePath();
        try {
            File saveDirectory = this.savePath.toFile();
            if (saveDirectory.mkdirs()) {
                YungsRoads.LOGGER.info("Creating 'roads' directory for dimension {}...", world.getDimensionKey().getLocation().toString());
            }
        } catch (Exception e) {
            YungsRoads.LOGGER.error("Unable to create 'roads' directory for dimension {}. This shouldn't happen!", world.getDimensionKey().getLocation().toString());
            YungsRoads.LOGGER.error(e.toString());
        }
    }

    public List<BlockPos> getNearestVillages(BlockPos pos) {
        StructureRegionPos centerRegionPos = new StructureRegionPos(pos);
//        StructureRegion centerRegion = getRegion(centerRegionPos);
        ChunkPos centerChunkPos = new ChunkPos(pos);

        BlockPos village1 = new BlockPos(pos);
        BlockPos village2 = new BlockPos(pos);
        int dist1 = Integer.MAX_VALUE;
        int dist2 = Integer.MAX_VALUE;

        for (int regionXOffset = -2; regionXOffset <= 2; regionXOffset++) {
            for (int regionZOffset = -2; regionZOffset <= 2; regionZOffset++) {
                StructureRegionPos currRegionPos = new StructureRegionPos(centerRegionPos.getX() + regionXOffset, centerRegionPos.getZ() + regionZOffset);
                StructureRegion currRegion = getRegion(currRegionPos);

                for (long chunkLong : currRegion.getVillageChunks()) {
                    ChunkPos candidateChunkPos = new ChunkPos(chunkLong);
                    int sqDist = (candidateChunkPos.x - centerChunkPos.x) * (candidateChunkPos.x - centerChunkPos.x)
                            + (candidateChunkPos.z - centerChunkPos.z) * (candidateChunkPos.z) - centerChunkPos.z;
                    if (sqDist < dist1) {
                        dist2 = dist1;
                        village2 = village1;
                        dist1 = sqDist;
                        village1 = candidateChunkPos.asBlockPos();
                    } else if (sqDist < dist2) {
                        dist2 = sqDist;
                        village2 = candidateChunkPos.asBlockPos();
                    }
                }
            }
        }

        return Lists.newArrayList(village1, village2);

//        try {
//            saveRegionToFile(regionKey);
//        } catch (IOException e) {
//            YungsRoads.LOGGER.error(e);
//        }
    }


    /**
     * Loads the {@link StructureRegion} for the given key.
     *
     * If a corresponding structure region file exists, we load the data from it.
     * If the file does not exist or is corrupt, generation is delegated to {@link #generateRegion}.
     */
    private StructureRegion getRegion(long regionKey) {
        return this.structureRegionCache.computeIfAbsent(regionKey, newKey -> {
            // Try to load region from file. If an error occurs, we generate the region anew
            try {
                return loadRegionFromFile(newKey);
            } catch (IOException e) {
                return generateRegion(newKey);
            }
        });
    }

    /**
     * Loads the {@link StructureRegion} with the given region position.
     *
     * If a corresponding structure region file exists, we load the data from it.
     * If the file does not exist or is corrupt, generation is delegated to {@link #generateRegion}.
     */
    private StructureRegion getRegion(StructureRegionPos regionPos) {
        long regionKey = regionPos.asLong();
        return this.structureRegionCache.computeIfAbsent(regionKey, newKey -> {
            // Try to load region from file. If an error occurs, we generate the region anew
            try {
                return loadRegionFromFile(newKey);
            } catch (IOException e) {
                return generateRegion(newKey);
            }
        });
    }

    private StructureRegion loadRegionFromFile(long regionKey) throws IOException {
        StructureRegionPos structureRegionPos = new StructureRegionPos(regionKey);
        File inFile = this.savePath.resolve(structureRegionPos.getFileName()).toFile();
        CompoundNBT structureRegionNbt = CompressedStreamTools.readCompressed(inFile);
        return new StructureRegion(regionKey, structureRegionNbt);
    }

    public void saveRegionToFile(long regionKey) throws IOException {
        if (this.structureRegionCache.containsKey(regionKey)) {
            StructureRegion structureRegion = this.structureRegionCache.get(regionKey);
            File outFile = new File(savePath.toString(), structureRegion.getFileName());
            CompressedStreamTools.writeCompressed(structureRegion.toNbt(), outFile);
        } else {
            StructureRegion structureRegion = new StructureRegion(regionKey);
            YungsRoads.LOGGER.warn("Unable to save uncached region {} ({}, {}). This shouldn't happen!",
                    regionKey, structureRegion.getPos().getX(), structureRegion.getPos().getZ());
        }
    }

    /**
     * Generates a new {@link StructureRegion} for the given region key.
     *
     * Uses the structure's separation settings to reconstruct its structure location grid,
     * then validates each position with a biome check.
     */
    private StructureRegion generateRegion(long regionKey) {
        StructureRegionPos regionPos = new StructureRegionPos(regionKey);
        LongOpenHashSet villageChunks = new LongOpenHashSet();
        ChunkPos minChunkPos = regionPos.getMinChunkPosInRegion();
        ChunkPos maxChunkPos = regionPos.getMaxChunkPosInRegion();

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
                if (((StructureAccessor)Structure.VILLAGE).isLinearSeparation()) {
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
                    villageChunks.add(structureCandidateChunkPos.asLong());
                }
            }
        }

        return new StructureRegion(regionKey, villageChunks);
    }
}
