package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tryrodave.chromaextras.util.DeferredStructureGen;

import Reika.ChromatiCraft.World.Nether.NetherStructureGenerator;
import cpw.mods.fml.common.IWorldGenerator;

/**
 * ChromatiCraft's nether aux generator runs several passes per chunk: bypass structures, ground decorators, glowstone
 * veins, and (the dominant one) {@code LavaRiverGenerator}, which places river liquid with a neighbor-notify flag.
 * The glowstone/structure passes spread across chunks and the lava-river notify propagates over chunk edges, so all
 * of it force-loads ungenerated neighbor chunks during population -> cascading worldgen lag (this was the entire
 * Nether cascade).
 *
 * <p>
 * Defer the whole {@code generate()} as one unit - the same treatment as the glowing-cliffs aux generator - so every
 * pass runs together once the surrounding footprint is loaded (via {@link DeferredStructureGen}). Nothing is
 * force-loaded and nothing is dropped; nether features simply appear a few chunks behind the exploration frontier.
 * The river is noise-driven per chunk, so its segments stay continuous regardless of when each chunk generates.
 */
@Mixin(value = NetherStructureGenerator.class, remap = false)
public class MixinNetherStructureGenerator {

    /** Must be within {@link DeferredStructureGen}'s footprint radius so the deferred retry's guard check passes. */
    private static final int CHROMAEXTRAS$FOOTPRINT_CHUNK_RADIUS = 4;

    @Inject(
        method = "generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void chromaextras$deferUntilFootprintLoaded(Random rand, int chunkX, int chunkZ, World world,
        IChunkProvider chunkGenerator, IChunkProvider chunkProvider, CallbackInfo ci) {
        if (world.provider.dimensionId != -1) {
            return; // the generator itself no-ops outside the Nether; leave that path alone
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
