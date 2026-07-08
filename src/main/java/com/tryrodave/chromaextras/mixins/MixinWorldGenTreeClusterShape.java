package com.tryrodave.chromaextras.mixins;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.DragonAPI.Instantiable.Data.Immutable.BlockKey;

/**
 * Companion to {@code MixinWorldGenTreeCluster}. The per-tree footprint guard there stops a tree's reads and canopy
 * writes from force-loading a chunk that is ±5 blocks away, but the leaves/logs are placed with
 * {@code BlockKey.place(world, x, y, z)}, which sets the block with flag 3 (neighbor-notify). A leaf placed on the
 * very edge of a loaded chunk then notifies the block one past it - in the not-yet-generated neighbor chunk -
 * force-loading it. That is a cascade the position guard cannot catch, because the notify reaches one block beyond
 * the canopy the guard checked.
 *
 * <p>
 * Place with flag 2 (no neighbor notify) instead. Worldgen block placement should never trigger neighbor updates, so
 * this is lossless - the tree is written identically, it just stops poking its neighbors. The log/leaf placement all
 * funnels through the 4-argument {@code place} in the tree's shape methods, so redirecting those covers every block
 * the tree writes.
 *
 * <p>
 * The redirect also clips any block that would land in a not-yet-loaded chunk. The per-tree footprint guard checks a
 * radius of 5 around the trunk, but a giant tree's canopy reaches out to 6; when the trunk sits at the wrong offset,
 * that outermost ring falls one block into an unloaded neighbor, and {@code setBlock} there force-loads (generates)
 * it - a cascade the flag alone does not stop, since this one is the block's own chunk, not a notified neighbor.
 * Skipping those overhang blocks loses at most the outermost leaf sliver of a giant tree sitting exactly on a
 * frontier edge, and can never cascade regardless of tree size.
 */
@Mixin(targets = "Reika.ChromatiCraft.World.Dimension.Generators.WorldGenTreeCluster$TreeShape", remap = false)
public class MixinWorldGenTreeClusterShape {

    @Redirect(
        method = { "setLeaf", "generate", "generateNeedle", "generateTall" },
        at = @At(
            value = "INVOKE",
            target = "LReika/DragonAPI/Instantiable/Data/Immutable/BlockKey;place(Lnet/minecraft/world/World;III)V"))
    private void chromaextras$placeWithoutNeighborNotify(BlockKey key, World world, int x, int y, int z) {
        IChunkProvider cp = world.getChunkProvider();
        if (!cp.chunkExists(x >> 4, z >> 4)) {
            return; // clip the overhang: never force-load a neighbor chunk just to place a leaf/log
        }
        key.place(world, x, y, z, 2);
    }
}
