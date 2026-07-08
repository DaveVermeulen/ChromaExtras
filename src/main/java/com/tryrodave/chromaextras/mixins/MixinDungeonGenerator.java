package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tryrodave.chromaextras.util.DeferredStructureGen;

import Reika.ChromatiCraft.Registry.ChromaStructures;
import Reika.ChromatiCraft.World.IWG.DungeonGenerator;

/**
 * ChromatiCraft's grid structures (temples/dungeons) are placed by {@code checkChunk} during a chunk's population.
 * They span several chunks, so reading terrain to validate a spot and writing the structure force-loads neighbor
 * chunks -> cascading worldgen lag. Simply skipping was worse: {@code checkChunk} marks a failed cell dead
 * permanently, so the structure was lost forever.
 *
 * <p>
 * Instead, defer: if the cell's footprint is not yet fully loaded, cancel {@code checkChunk} at HEAD (so it never
 * runs its reads/writes and never marks the cell failed - it stays {@code PLANNED}) and hand the cell to
 * {@link DeferredStructureGen}, which regenerates it on a later server tick once exploration has loaded the
 * surrounding chunks. The structure still generates - it just waits for its area to exist instead of forcing it
 * into being. When the footprint is already loaded (the deferred retry, or a revisited area), the guard passes and
 * generation proceeds normally.
 */
@Mixin(value = DungeonGenerator.class, remap = false)
public class MixinDungeonGenerator {

    /** Must match {@link DeferredStructureGen}'s footprint radius so the deferred retry's guard check passes. */
    private static final int CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS = 3;

    @Inject(method = "checkChunk", at = @At("HEAD"), cancellable = true)
    private void chromaextras$deferUntilFootprintLoaded(World world, int chunkX, int chunkZ, Random random,
        ChromaStructures s, CallbackInfoReturnable<Boolean> cir) {
        IChunkProvider cp = world.getChunkProvider();
        for (int dx = -CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dx <= CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dx++) {
            for (int dz = -CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dz <= CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS; dz++) {
                if (!cp.chunkExists(chunkX + dx, chunkZ + dz)) {
                    DeferredStructureGen.enqueue(world.provider.dimensionId, chunkX, chunkZ);
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
