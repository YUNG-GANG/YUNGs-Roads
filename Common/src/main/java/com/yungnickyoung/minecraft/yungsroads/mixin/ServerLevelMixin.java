package com.yungnickyoung.minecraft.yungsroads.mixin;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements IStructureRegionCacheProvider {
    @Unique
    private StructureRegionCache structureRegionCache;

    @Unique
    @Nullable
    private LiveRoadPlacer liveRoadPlacer;

    protected ServerLevelMixin(WritableLevelData $$0, ResourceKey<Level> $$1, RegistryAccess $$2, Holder<DimensionType> $$3, Supplier<ProfilerFiller> $$4, boolean $$5, boolean $$6, long $$7, int $$8) {
        super($$0, $$1, $$2, $$3, $$4, $$5, $$6, $$7, $$8);
    }


    @Inject(method = "<init>", at = @At("RETURN"))
    private void yungsroads_attachStructureRegionCache(MinecraftServer server, Executor executor,LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData, ResourceKey resourceKey, LevelStem levelStem, ChunkProgressListener chunkProgressListener, boolean $$7, long $$8, List $$9, boolean $$10, RandomSequences $$11, CallbackInfo ci) {
        Path dimensionPath = levelStorageAccess.getDimensionPath(this.dimension());
        this.structureRegionCache = new StructureRegionCache((ServerLevel) (Object) this, dimensionPath);
        if (YungsRoadsCommon.DEBUG_MODE && this.structureRegionCache.getStructureRegionGenerator().hasRoadNetwork()) {
            this.liveRoadPlacer = new LiveRoadPlacer((ServerLevel) (Object) this, this.structureRegionCache, dimensionPath.resolve("roads"));
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void yungsroads_saveRoadBlockLog(@Nullable ProgressListener progressListener, boolean flush, boolean skipSave, CallbackInfo ci) {
        if (this.liveRoadPlacer != null && !skipSave) {
            this.liveRoadPlacer.save();
        }
    }

    @Unique
    @Override
    public StructureRegionCache getStructureRegionCache() {
        return structureRegionCache;
    }

    @Unique
    @Override
    @Nullable
    public LiveRoadPlacer getLiveRoadPlacer() {
        return liveRoadPlacer;
    }
}
