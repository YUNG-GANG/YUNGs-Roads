package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.ChunkManagerAccessor;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches StructureRegions for a given world and exposes methods for retrieving them.
 * If a StructureRegion is not yet cached, generation is deferred to {@link StructureRegionGenerator}
 */
public class StructureRegionCache {
    private final Path savePath;
    private final ConcurrentHashMap<Long, StructureRegion> structureRegionCache;
    private final StructureRegionGenerator structureRegionGenerator;

    public StructureRegionCache(ServerWorld world) {
        this.structureRegionCache = new ConcurrentHashMap<>();
        this.structureRegionGenerator = new StructureRegionGenerator(world);
        this.savePath = ((ChunkManagerAccessor) world.getChunkProvider().chunkManager).getDimensionDirectory().toPath().resolve("roads").toAbsolutePath();
        createDirectoryIfDoesNotExist();
    }

    /**
     * Finds the nearest village to the given position <i>within the StructureRegion the provided position is in.</i>
     * This means the position returned may not actually be the closest village.
     */
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
    }

    /**
     * Loads the {@link StructureRegion} with the given key.
     *
     * If a corresponding structure region file exists, we load the data from it.
     * If the file does not exist or is corrupt, generation is deferred to {@link StructureRegionGenerator#generateRegion}.
     */
    public StructureRegion getRegion(long regionKey) {
        return this.structureRegionCache.computeIfAbsent(regionKey, newKey -> {
            StructureRegionPos structureRegionPos = new StructureRegionPos(regionKey);
            File file = this.savePath.resolve(structureRegionPos.getFileName()).toFile();

            // If file does not yet exist, generate the region and save the file.
            if (!file.exists()) {
                StructureRegion newStructureRegion = this.structureRegionGenerator.generateRegion(newKey);
                writeStructureRegionFile(newStructureRegion);
                return newStructureRegion;
            }

            // Attempt to read existing file.
            try {
                CompoundNBT structureRegionNbt = CompressedStreamTools.read(file);
                return new StructureRegion(regionKey, structureRegionNbt);
            } catch (IOException e) {
                // Unable to read file. Log error & generate file anew.
                YungsRoads.LOGGER.error("Unable to read roads file {}", file.toString());
                YungsRoads.LOGGER.error(e);
                StructureRegion newStructureRegion = this.structureRegionGenerator.generateRegion(newKey);
                writeStructureRegionFile(newStructureRegion);
                return newStructureRegion;
            }
        });
    }

    /**
     * Loads the {@link StructureRegion} with the given region position.
     *
     * If a corresponding structure region file exists, we load the data from it.
     * If the file does not exist or is corrupt, generation is deferred to {@link StructureRegionGenerator#generateRegion}.
     */
    public StructureRegion getRegion(StructureRegionPos regionPos) {
        return this.getRegion(regionPos.asLong());
    }

    public StructureRegionGenerator getStructureRegionGenerator() {
        return structureRegionGenerator;
    }

    private void writeStructureRegionFile(StructureRegion structureRegion) {
        File file = new File(savePath.toString(), structureRegion.getFileName());

        if (file.exists()) {
            YungsRoads.LOGGER.warn("Found existing file for region {}!", structureRegion.getFileName());
        }

        try {
            CompressedStreamTools.write(structureRegion.toNbt(), file);
        } catch (IOException e) {
            YungsRoads.LOGGER.error("Unable to write structure region file {}", structureRegion.getFileName());
            YungsRoads.LOGGER.error(e);

        }
    }

    private void createDirectoryIfDoesNotExist() {
        try {
            Files.createDirectories(this.savePath);
        } catch (IOException e) {
            YungsRoads.LOGGER.error("Unable to create roads save path {}", this.savePath);
            YungsRoads.LOGGER.error(e);
        }
    }
}
