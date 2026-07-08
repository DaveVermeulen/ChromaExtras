package com.tryrodave.chromaextras.mixins;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.ChromatiCraft.World.Dimension.Generators.WorldGenTreeCluster;

/**
 * The ChromatiCraft dimension (Proxima) populates via its own {@code ChunkProviderChroma.runDecorators} pipeline, and
 * its dominant worldgen cascade is {@code WorldGenTreeCluster}: it scatters 3-11 trees up to 16 blocks from the
 * cluster centre, and each tree's {@code canGenerateTree} reads the trunk column + a sapling check while
 * {@code generateTree} writes the canopy - all of which reach into ungenerated neighbor chunks and force-load them.
 *
 * <p>
 * {@code canGenerateTree} gates each individual tree (both its reads and the subsequent placement), so guard it: if
 * the tree's footprint touches a chunk that is not currently loaded, report it as unplantable and skip that tree. The
 * cluster still fills in wherever its footprint is loaded; only trees that would straddle an ungenerated chunk are
 * dropped - the same trade already used for the overworld colour trees. No chunk is ever force-loaded.
 */
@Mixin(value = WorldGenTreeCluster.class, remap = false)
public class MixinWorldGenTreeCluster {

    /** Max horizontal reach of a tree's canopy from its trunk. */
    private static final int CHROMAEXTRAS$TREE_REACH = 5;

    @Inject(method = "canGenerateTree", at = @At("HEAD"), cancellable = true)
    private void chromaextras$skipTreeIfFootprintNotLoaded(World world, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        IChunkProvider cp = world.getChunkProvider();
        for (int cx = (x - CHROMAEXTRAS$TREE_REACH) >> 4; cx <= (x + CHROMAEXTRAS$TREE_REACH) >> 4; cx++) {
            for (int cz = (z - CHROMAEXTRAS$TREE_REACH) >> 4; cz <= (z + CHROMAEXTRAS$TREE_REACH) >> 4; cz++) {
                if (!cp.chunkExists(cx, cz)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
