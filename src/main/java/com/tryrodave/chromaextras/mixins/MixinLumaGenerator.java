package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.ChromatiCraft.World.IWG.LumaGenerator;

/**
 * {@code LumaGenerator} scatters a luma cluster (radius up to ~6) around a point, and for each block checks
 * {@code isValidLocation} then {@code world.setBlock(...)}. Near a chunk edge both the validity reads and the
 * placement (with neighbor-notify) reach into ungenerated neighbor chunks -> cascading worldgen lag.
 *
 * <p>
 * Guard {@code isValidLocation} to report invalid when the position's chunk (or an immediate neighbor it reads) is
 * not loaded - this both skips the read and prevents the gated {@code setBlock}. Also strip the notify bit from that
 * placement so blocks landing on a loaded chunk edge don't notify into an unloaded neighbor.
 */
@Mixin(value = LumaGenerator.class, remap = false)
public class MixinLumaGenerator {

    @Inject(method = "isValidLocation", at = @At("HEAD"), cancellable = true)
    private void chromaextras$skipIfNotLoaded(World world, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) {
            for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }

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
