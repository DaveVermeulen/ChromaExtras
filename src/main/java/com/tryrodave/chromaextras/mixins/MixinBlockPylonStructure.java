package com.tryrodave.chromaextras.mixins;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import Reika.ChromatiCraft.Block.BlockPylonStructure;

/**
 * Every pylon-structure block placed fires {@code onBlockAdded -> triggerAddCheck}, which scans a 33x65x33 region
 * (x-16..x+16, y-32..y+32, z-16..z+16) and reads neighbor TileEntities to re-validate the multiblock. While the
 * pylon is being placed by worldgen (PylonGenerator.generatePylon), that scan reaches into ungenerated neighbor
 * chunks and force-loads them -> cascading worldgen lag.
 *
 * <p>
 * Skip the re-validation whenever any chunk the scan would touch is not currently loaded. This only happens during
 * worldgen; at runtime the surrounding chunks are loaded so the check runs normally. The freshly generated pylon is
 * validated by its struct-control TileEntity / on the next block update anyway, so nothing is lost.
 */
@Mixin(value = BlockPylonStructure.class, remap = false)
public class MixinBlockPylonStructure {

    @Inject(method = "triggerAddCheck(Lnet/minecraft/world/World;III)V", at = @At("HEAD"), cancellable = true)
    private static void chromaextras$skipCheckWhenNeighborsUnloaded(World world, int x, int y, int z, CallbackInfo ci) {
        int cxMin = (x - 16) >> 4;
        int cxMax = (x + 16) >> 4;
        int czMin = (z - 16) >> 4;
        int czMax = (z + 16) >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
