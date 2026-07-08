package com.tryrodave.chromaextras.mixins;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code GlowingCliffsAuxGenerator} builds large floating islands (radius up to ~48) whose body, decorations
 * ({@code genIslandDecorations} -> {@code getTopY}) and ore veins ({@code WorldGenMinable}) all reach well beyond the
 * chunk being populated. Any part landing in an ungenerated neighbor chunk force-loads it -> cascading worldgen lag.
 *
 * <p>
 * {@code Island.canGenerate} gates the whole placement (body + decorations + ores follow it in {@code generateIsland}),
 * so guard it: if the island's full footprint - its radius plus a margin for decorations/ore veins - touches a chunk
 * that is not currently loaded, report it as ungeneratable so the island is skipped rather than generated across
 * unloaded chunks. Islands whose surroundings are already loaded (the biome interior) still generate normally; only
 * ones right at the freshly generated frontier are passed over. This trades a little island density near chunk
 * borders for eliminating the cascade - a full per-chunk deferral (as done for the End islands) would keep every
 * island but is a much larger change across three placement subsystems.
 */
@Mixin(targets = "Reika.ChromatiCraft.World.IWG.GlowingCliffsAuxGenerator$Island", remap = false)
public class MixinGlowingCliffsAuxIsland {

    @Shadow
    private int originX;
    @Shadow
    private int originZ;
    @Shadow
    private double outerRadius;

    /** Extra reach beyond the island radius to cover decorations and ore veins placed around it. */
    private static final int CHROMAEXTRAS$DECORATION_MARGIN = 16;

    @Inject(method = "canGenerate", at = @At("HEAD"), cancellable = true)
    private void chromaextras$skipIfFootprintNotLoaded(World world, CallbackInfoReturnable<Boolean> cir) {
        int reach = MathHelper.ceiling_double_int(outerRadius) + CHROMAEXTRAS$DECORATION_MARGIN;
        int cxMin = (originX - reach) >> 4;
        int cxMax = (originX + reach) >> 4;
        int czMin = (originZ - reach) >> 4;
        int czMax = (originZ + reach) >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
