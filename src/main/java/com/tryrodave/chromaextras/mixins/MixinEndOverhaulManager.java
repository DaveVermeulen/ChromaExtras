package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tryrodave.chromaextras.util.EndIslandDeferral;

import Reika.ChromatiCraft.World.EndOverhaulManager;
import Reika.DragonAPI.Instantiable.Data.Immutable.BlockKey;

/**
 * Companion to {@code MixinEndOverhaulIsland}: island blocks are recorded into {@link EndIslandDeferral} during
 * {@code setSeed}'s one-shot layout computation instead of being written to far chunks. Right after {@code setSeed}
 * runs (while ChromatiCraft still has lighting/events/updates suppressed), place the buffered island blocks that
 * belong to the chunk currently being populated. Every End chunk populates at or after the first {@code setSeed},
 * so each chunk receives its island blocks exactly once, with no chunk ever force-loaded.
 */
@Mixin(value = EndOverhaulManager.class, remap = false)
public class MixinEndOverhaulManager {

    @Inject(
        method = "generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
        at = @At(
            value = "INVOKE",
            target = "LReika/ChromatiCraft/World/EndOverhaulManager;setSeed(Lnet/minecraft/world/World;)V",
            shift = At.Shift.AFTER))
    private void chromaextras$placeDeferredIslandBlocks(Random rand, int chunkX, int chunkZ, World world,
        IChunkProvider chunkGenerator, IChunkProvider chunkProvider, CallbackInfo ci) {
        EndIslandDeferral.placeChunk(world, chunkX, chunkZ);
    }

    /**
     * The per-chunk tendril placement uses {@code BlockKey.place(world, x, y, z)}, which sets the block with flag 3
     * (neighbor-notify). Tendril blocks on a chunk edge then notify into the neighbor chunk, force-loading it. Place
     * with flag 2 (no neighbor notify) instead - worldgen block placement should never trigger neighbor updates.
     */
    @Redirect(
        method = "generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
        at = @At(
            value = "INVOKE",
            target = "LReika/DragonAPI/Instantiable/Data/Immutable/BlockKey;place(Lnet/minecraft/world/World;III)V"))
    private void chromaextras$placeTendrilWithoutNeighborNotify(BlockKey bk, World world, int x, int y, int z) {
        bk.place(world, x, y, z, 2);
    }
}
