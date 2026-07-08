package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.ChromatiCraft.World.IWG.CaveIndicatorGenerator;

/**
 * {@code CaveIndicatorGenerator} places cave-indicator blocks throughout the chunk with
 * {@code world.setBlock(x, y, z, block)} (flag 3). The scan stays within the current chunk, but blocks placed on a
 * chunk edge fire neighbor-notification updates that reach into ungenerated neighbor chunks -> cascading worldgen
 * lag. Strip the notify bit; worldgen placement should not trigger neighbor updates.
 */
@Mixin(value = CaveIndicatorGenerator.class, remap = false)
public class MixinCaveIndicatorGenerator {

    @Redirect(
        method = "generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;)Z",
            remap = true))
    private boolean chromaextras$placeWithoutNeighborNotify(World world, int x, int y, int z, Block block) {
        return world.setBlock(x, y, z, block, 0, 2);
    }
}
