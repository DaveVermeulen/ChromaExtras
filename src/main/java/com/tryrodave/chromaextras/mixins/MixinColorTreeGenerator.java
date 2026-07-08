package com.tryrodave.chromaextras.mixins;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.ChromatiCraft.World.IWG.ColorTreeGenerator;

/**
 * {@code ColorTreeGenerator.generate} picks a spot and, gated by {@code canGenerateTree(world, x, z)}, grows a
 * color tree (or, via {@code RainbowTreeGenerator}, a large rainbow tree) whose canopy/blueprint reads and writes
 * several blocks around the trunk. Near a chunk edge that reaches into ungenerated neighbor chunks and force-loads
 * them -> cascading worldgen lag (ColorTreeGenerator.generate -> TreeShaper.* / RainbowTreeGenerator.*).
 *
 * <p>
 * {@code canGenerateTree} gates the entire tree (both the normal and rainbow paths), so guard it: report "cannot
 * generate" whenever the tree's footprint would touch a chunk that is not currently loaded. Trees are ordinary
 * decoration, so simply skipping the ones that would straddle an unloaded chunk is fine - no chunk is force-loaded.
 */
@Mixin(value = ColorTreeGenerator.class, remap = false)
public class MixinColorTreeGenerator {

    /** Max horizontal reach of a tree (large rainbow-tree blueprint is the widest, ~6 from the trunk). */
    private static final int CHROMAEXTRAS$TREE_REACH = 8;

    @Inject(method = "canGenerateTree", at = @At("HEAD"), cancellable = true)
    private static void chromaextras$skipTreeIfFootprintNotLoaded(World world, int x, int z,
        CallbackInfoReturnable<Boolean> cir) {
        for (int cx = (x - CHROMAEXTRAS$TREE_REACH) >> 4; cx <= (x + CHROMAEXTRAS$TREE_REACH) >> 4; cx++) {
            for (int cz = (z - CHROMAEXTRAS$TREE_REACH) >> 4; cz <= (z + CHROMAEXTRAS$TREE_REACH) >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
