package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.ChromatiCraft.World.IWG.CrystalGenerator;

/**
 * {@code CrystalGenerator.generate} scatters crystals with {@code world.setBlock(x, y, z, block, meta, 3)}. Flag
 * bit 1 fires neighbor-notification block updates; on a chunk edge those updates reach into ungenerated neighbor
 * chunks and force them to load mid-population -> cascading worldgen lag (ArchaicFix warnings against
 * CrystalGenerator.generate).
 *
 * <p>
 * Strip the notify bit so worldgen placement only sends the client update (flag 2) and never notifies neighbors.
 */
@Mixin(value = CrystalGenerator.class, remap = false)
public class MixinCrystalGenerator {

    @Redirect(
        method = "generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z",
            remap = true))
    private boolean chromaextras$placeWithoutNeighborNotify(World world, int x, int y, int z, Block block, int meta,
        int flag) {
        return world.setBlock(x, y, z, block, meta, flag & ~1);
    }
}
