package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.ChromatiCraft.World.TreeShaper;

/**
 * {@code TreeShaper.setBlock} (used by ColorTreeGenerator during worldgen) places tree blocks with
 * {@code world.setBlock(x, y, z, block, meta, flag)} where the flag carries the neighbor-notify bit. On a chunk
 * edge that notification reaches into ungenerated neighbor chunks and force-loads them -> cascading worldgen lag
 * (ArchaicFix warnings against TreeShaper.setBlock / ColorTreeGenerator.generate).
 *
 * <p>
 * Strip the notify bit; worldgen tree placement should not trigger neighbor updates.
 */
@Mixin(value = TreeShaper.class, remap = false)
public class MixinTreeShaper {

    @Redirect(
        method = "setBlock(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z",
            remap = true))
    private boolean chromaextras$placeWithoutNeighborNotify(World world, int x, int y, int z, Block block, int meta,
        int flag) {
        return world.setBlock(x, y, z, block, meta, flag & ~1);
    }
}
