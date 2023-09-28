package com.yungnickyoung.minecraft.yungsroads.mixin;

import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements IStructureRegionCacheProvider {
    private StructureRegionCache structureRegionCache;

    protected ServerLevelMixin(WritableLevelData $$0, ResourceKey<Level> $$1, Holder<DimensionType> $$2, Supplier<ProfilerFiller> $$3, boolean $$4, boolean $$5, long $$6) {
        super($$0, $$1, $$2, $$3, $$4, $$5, $$6);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void yungsroads_attachStructureRegionCache(MinecraftServer minecraftServer,
                                          Executor executor,
                                          LevelStorageSource.LevelStorageAccess levelStorageAccess,
                                          ServerLevelData serverLevelData,
                                          ResourceKey<Level> resourceKey,
                                          Holder<DimensionType> holder,
                                          ChunkProgressListener chunkProgressListener,
                                          ChunkGenerator chunkGenerator,
                                          boolean bl,
                                          long l,
                                          List<CustomSpawner> list,
                                          boolean bl2,
                                          CallbackInfo ci) {
        Path dimensionPath = levelStorageAccess.getDimensionPath(this.dimension());
        this.structureRegionCache = new StructureRegionCache((ServerLevel) (Object) this, dimensionPath);
    }

    @Override
    public StructureRegionCache getStructureRegionCache() {
        return structureRegionCache;
    }
}
