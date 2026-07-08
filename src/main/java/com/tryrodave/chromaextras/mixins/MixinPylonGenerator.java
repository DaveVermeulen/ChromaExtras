package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tryrodave.chromaextras.util.DeferredStructureGen;

import Reika.ChromatiCraft.World.IWG.PylonGenerator;
import cpw.mods.fml.common.IWorldGenerator;

/**
 * A pylon's placement reads a structural footprint ({@code canGenerateAt}), can be shifted up to 16 blocks to dodge
 * trees, and finally validates a multiblock (including the ~5-block {@code PYLONBROADCAST} structure via
 * {@code matchInWorld}). During chunk population all of that reaches into ungenerated neighbor chunks and force-loads
 * them -> cascading worldgen lag.
 *
 * <p>
 * An earlier attempt guarded {@code canGenerateAt} to reject spots near unloaded chunks, but {@code tryForceGenerate}
 * only gets 24 one-shot attempts in the cell and is never retried, so rejecting ~44% of candidate spots measurably
 * reduced how many pylons spawned. Instead, defer the whole attempt: if the cell's footprint is not yet loaded, skip
 * {@code tryForceGenerate} now and hand the cell to {@link DeferredStructureGen}, which re-runs generation on a later
 * tick once the surrounding chunks exist. The retry runs the full unmodified 24-try search in a loaded neighborhood,
 * so the pylon count matches vanilla while nothing is force-loaded.
 */
@Mixin(value = PylonGenerator.class, remap = false)
public class MixinPylonGenerator {

    /** Must match {@link DeferredStructureGen}'s footprint radius so the deferred retry's guard check passes. */
    private static final int CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS = 3;

    @Inject(method = "tryForceGenerate", at = @At("HEAD"), cancellable = true)
    private void chromaextras$deferUntilFootprintLoaded(World world, int cx, int cz, Random r, CallbackInfo ci) {
        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
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
