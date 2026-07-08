package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tryrodave.chromaextras.util.DeferredStructureGen;

import Reika.ChromatiCraft.World.IWG.GlowingCliffsAuxGenerator;
import cpw.mods.fml.common.IWorldGenerator;

/**
 * The glowing-cliffs aux generator adds large floating islands, ore veins, and grows saplings into full trees across
 * the biome. The islands (outer radius up to ~36 blocks) plus their decorations ({@code getTopY}) and the trees grown
 * by {@code growSaplings} all reach well past the chunk being populated, force-loading neighbor chunks -> cascading
 * worldgen lag (this was the bulk of the remaining cascades in the cliffs biome).
 *
 * <p>
 * Guarding just the island's {@code canGenerate} wasn't enough - the decoration and sapling passes run afterward and
 * reach further. So defer the whole {@code generate()} as one unit (which also avoids re-running only part of it and
 * duplicating ore veins): if the surrounding footprint isn't loaded yet, hand the chunk to {@link DeferredStructureGen}
 * and skip. It regenerates once exploration has loaded the neighborhood, running every sub-pass in a fully loaded area.
 * Island presence is driven by seeded noise rather than the populate RNG, so density is preserved; only the timing
 * (features appear a few chunks behind the frontier) changes.
 */
@Mixin(value = GlowingCliffsAuxGenerator.class, remap = false)
public class MixinGlowingCliffsAuxGenerator {

    /** Must be within {@link DeferredStructureGen}'s footprint radius so the deferred retry's guard check passes. */
    private static final int CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS = 4;

    @Inject(
        method = "generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void chromaextras$deferUntilFootprintLoaded(Random random, int chunkX, int chunkZ, World world,
        IChunkProvider chunkGenerator, IChunkProvider chunkProvider, CallbackInfo ci) {
        if (world.provider.dimensionId != 0) {
            return; // cliffs are an overworld biome; leave other dimensions to run normally
        }
        IChunkProvider cp = world.getChunkProvider();
        for (int dx = -CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dx <= CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dx++) {
            for (int dz = -CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dz <= CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dz++) {
                if (!cp.chunkExists(chunkX + dx, chunkZ + dz)) {
                    DeferredStructureGen
                        .enqueue(world.provider.dimensionId, chunkX, chunkZ, (IWorldGenerator) (Object) this);
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
