package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

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
    private final StructureRegionGenerator structureRegionGenerator;

    /**
     * Cache of StructureRegions, keyed by region key.
     */
    private final ConcurrentHashMap<Long, StructureRegion> cache;

    public StructureRegionCache(ServerLevel level, Path dimensionPath) {
        this.cache = new ConcurrentHashMap<>();
        this.structureRegionGenerator = new StructureRegionGenerator(level);
        this.savePath = dimensionPath.resolve("roads").toAbsolutePath();
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

        BlockPos nearestVillage = new BlockPos(pos);
        int distance = Integer.MAX_VALUE;

        for (long chunkLong : structureRegion.getVillageChunks()) {
            ChunkPos candidateChunkPos = new ChunkPos(chunkLong);
            int sqDist = (candidateChunkPos.x - chunkPos.x) * (candidateChunkPos.x - chunkPos.x)
                    + (candidateChunkPos.z - chunkPos.z) * (candidateChunkPos.z - chunkPos.z);
            if (sqDist < distance) {
                distance = sqDist;
                nearestVillage = candidateChunkPos.getWorldPosition();
            }

            DebugRenderer.getInstance().addVillage(candidateChunkPos);
        }

        return nearestVillage;
    }

    /**
     * Loads the {@link StructureRegion} with the given key.
     * <p>
     * If a corresponding structure region file exists, we load the data from it.
     * If the file does not exist or is corrupt, generation is deferred to {@link StructureRegionGenerator#generateRegion}.
     */
    public StructureRegion getRegion(long regionKey) {
        return this.cache.computeIfAbsent(regionKey, newKey -> {
            StructureRegionPos structureRegionPos = new StructureRegionPos(regionKey);

            DebugRenderer.getInstance().addStructureRegion(structureRegionPos);

            File file = this.savePath.resolve(structureRegionPos.getFileName()).toFile();

            // If file does not yet exist, generate the region and save the file.
            if (!file.exists()) {
                StructureRegion newStructureRegion = this.structureRegionGenerator.generateRegion(newKey);
                writeStructureRegionFile(newStructureRegion);
                return newStructureRegion;
            }

            // Attempt to read existing file.
            try {
                CompoundTag structureRegionNbt = NbtIo.read(file);
                return new StructureRegion(regionKey, structureRegionNbt);
            } catch (IOException e) {
                // Unable to read file. Log error & generate file anew.
                YungsRoadsCommon.LOGGER.error("Unable to read roads file {}", file.toString());
                YungsRoadsCommon.LOGGER.error(e);
                YungsRoadsCommon.LOGGER.error("Regenerating structure region from scratch...");
                StructureRegion newStructureRegion = this.structureRegionGenerator.generateRegion(newKey);
                writeStructureRegionFile(newStructureRegion);
                return newStructureRegion;
            }
        });
    }

    /**
     * Loads the {@link StructureRegion} with the given region position.
     * <p>
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
            YungsRoadsCommon.LOGGER.warn("Found existing file for region {}!", structureRegion.getFileName());
        }

        try {
            NbtIo.write(structureRegion.toNbt(), file);
        } catch (IOException e) {
            YungsRoadsCommon.LOGGER.error("Unable to write structure region file {}", structureRegion.getFileName());
            YungsRoadsCommon.LOGGER.error(e);

        }
    }

    private void createDirectoryIfDoesNotExist() {
        try {
            Files.createDirectories(this.savePath);
        } catch (IOException e) {
            YungsRoadsCommon.LOGGER.error("Unable to create roads save path {}", this.savePath);
            YungsRoadsCommon.LOGGER.error(e);
        }
    }
}
