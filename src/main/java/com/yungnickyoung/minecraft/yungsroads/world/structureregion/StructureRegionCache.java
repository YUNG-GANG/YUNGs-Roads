package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.ChunkManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class StructureRegionCache {
    private final Path savePath;
    private final Long2ObjectLinkedOpenHashMap<StructureRegion> structureRegionCache;
    private final StructureRegionGenerator structureRegionGenerator;

    public StructureRegionCache(ServerWorld world) {
        this.structureRegionCache = new Long2ObjectLinkedOpenHashMap<>();
        this.structureRegionGenerator = new StructureRegionGenerator(world);

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

    /**
     * Loads the {@link StructureRegion} with the given key.
     *
     * If a corresponding structure region file exists, we load the data from it.
     * If the file does not exist or is corrupt, generation is delegated to {@link StructureRegionGenerator#generateRegion}.
     */
    public StructureRegion getRegion(long regionKey) {
        return this.structureRegionCache.computeIfAbsent(regionKey, newKey -> {
            // Try to load region from file. If an error occurs, we generate the region anew
            try {
                return loadRegionFromFile(newKey);
            } catch (IOException e) {
                return this.structureRegionGenerator.generateRegion(newKey);
            }
        });
    }

    /**
     * Loads the {@link StructureRegion} with the given region position.
     *
     * If a corresponding structure region file exists, we load the data from it.
     * If the file does not exist or is corrupt, generation is delegated to {@link StructureRegionGenerator#generateRegion}.
     */
    public StructureRegion getRegion(StructureRegionPos regionPos) {
        return this.getRegion(regionPos.asLong());
    }

    public StructureRegionGenerator getStructureRegionGenerator() {
        return structureRegionGenerator;
    }

    private StructureRegion loadRegionFromFile(long regionKey) throws IOException {
//        StructureRegionPos structureRegionPos = new StructureRegionPos(regionKey);
//        File inFile = this.savePath.resolve(structureRegionPos.getFileName()).toFile();
//        CompoundNBT structureRegionNbt = CompressedStreamTools.readCompressed(inFile);
//        CompoundNBT structureRegionNbt = CompressedStreamTools.read(inFile);
//        return new StructureRegion(regionKey, structureRegionNbt);
        throw new IOException();
    }

    private void saveRegionToFile(long regionKey) throws IOException {
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
}
