package com.yungnickyoung.minecraft.yungsroads.mixin;

import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.listener.IChunkStatusListener;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.ISpecialSpawner;
import net.minecraft.world.storage.IServerWorldInfo;
import net.minecraft.world.storage.SaveFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerWorld.class)
public class ServerWorldMixin implements IStructureRegionCacheProvider {
    private StructureRegionCache structureRegionCache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void attachStructureRegionCache(MinecraftServer server, Executor executor, SaveFormat.LevelSave save, IServerWorldInfo worldInfo, RegistryKey<World> key, DimensionType dimensionType, IChunkStatusListener chunkStatusListener, ChunkGenerator generator, boolean debug, long seed, List<ISpecialSpawner> specialSpawners, boolean e, CallbackInfo ci) {
        this.structureRegionCache = new StructureRegionCache((ServerWorld) (Object) this);
    }

    @Override
    public StructureRegionCache getStructureRegionCache() {
        return structureRegionCache;
    }
}
