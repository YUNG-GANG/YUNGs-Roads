package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.ChunkManagerAccessor;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.StructureAccessor;
import com.yungnickyoung.minecraft.yungsroads.world.road.IRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.LinearRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureRegionCache {
    private final Path savePath;
    private final ServerWorld world;
    private final SharedSeedRandom random;
    private final Long2ObjectLinkedOpenHashMap<StructureRegion> structureRegionCache;
    private final IRoadGenerator roadGenerator;
    private final int spacing, separation, salt;

    public StructureRegionCache(ServerWorld world) {
        this.world = world;
        this.random = new SharedSeedRandom();
        this.structureRegionCache = new Long2ObjectLinkedOpenHashMap<>();
        this.roadGenerator = new LinearRoadGenerator(world);

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

    public BlockPos getNearestVillage(BlockPos pos) {
        StructureRegionPos structureRegionPos = new StructureRegionPos(pos);
        ChunkPos chunkPos = new ChunkPos(pos);
        StructureRegion structureRegion = getRegion(structureRegionPos);

        BlockPos village1 = new BlockPos(pos);
        int dist1 = Integer.MAX_VALUE;

        for (long chunkLong : structureRegion.getVillageChunks()) {
            ChunkPos candidateChunkPos = new ChunkPos(chunkLong);
            int sqDist = (candidateChunkPos.x - chunkPos.x) * (candidateChunkPos.x - chunkPos.x)
                    + (candidateChunkPos.z - chunkPos.z) * (candidateChunkPos.z - chunkPos.z);
            if (sqDist < dist1) {
                dist1 = sqDist;
                village1 = candidateChunkPos.asBlockPos();
            }

            DebugRenderer.getInstance().addVillage(candidateChunkPos);
        }

        return village1;

//        try {
//            saveRegionToFile(regionKey);
//        } catch (IOException e) {
//            YungsRoads.LOGGER.error(e);
//        }
    }

    public List<Road> getRoadsForPosition(BlockPos pos) {
        StructureRegionPos structureRegionPos = new StructureRegionPos(pos);
        StructureRegion structureRegion = getRegion(structureRegionPos);
        return structureRegion.getRoads();
    }

    /**
     * Loads the {@link StructureRegion} with the given key.
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
        return this.getRegion(regionPos.asLong());
    }

    private StructureRegion loadRegionFromFile(long regionKey) throws IOException {
//        StructureRegionPos structureRegionPos = new StructureRegionPos(regionKey);
//        File inFile = this.savePath.resolve(structureRegionPos.getFileName()).toFile();
//        CompoundNBT structureRegionNbt = CompressedStreamTools.readCompressed(inFile);
//        CompoundNBT structureRegionNbt = CompressedStreamTools.read(inFile);
//        return new StructureRegion(regionKey, structureRegionNbt);
        throw new IOException();
    }

    public void saveRegionToFile(long regionKey) throws IOException {
        if (this.structureRegionCache.containsKey(regionKey)) {
            StructureRegion structureRegion = this.structureRegionCache.get(regionKey);
            File outFile = new File(savePath.toString(), structureRegion.getFileName());
//            CompressedStreamTools.writeCompressed(structureRegion.toNbt(), outFile);
            CompressedStreamTools.write(structureRegion.toNbt(), outFile);
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
     * then validates each position with a biome check. From there, some of the structure locations
     * are randomly selected as endpoints for roads, and the roads are constructed.
     */
    private StructureRegion generateRegion(long regionKey) {
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
                Optional<Road> roadOptional = this.roadGenerator.generate(startVillage, endVillage);
                if (roadOptional.isPresent()) {
                    roads.add(roadOptional.get());
                    i++;
                }
            }
        }

        return new StructureRegion(regionKey, villageSet, roads);
    }
}
