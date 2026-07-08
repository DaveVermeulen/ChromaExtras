package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;

/**
 * {@code BlockTieredPlant.TieredPlants.generate} searches for a spot to place a tiered plant. Most variants only
 * read the target column, but some offset horizontally (e.g. the vine variant reads a neighbor via
 * {@code Coordinate.offset(dir, 1)}, and the root variant searches a radius via {@code findTreeNear}). Near a chunk
 * edge those reads reach into ungenerated neighbor chunks and force-load them -> cascading worldgen lag
 * (TieredWorldGenerator.generate -> BlockTieredPlant$TieredPlants.generate).
 *
 * <p>
 * Return "no spot" (null) whenever the search area touches a chunk that is not currently loaded; the caller
 * ({@code TieredWorldGenerator}) already null-checks the result and skips placement. Plants are decoration, so
 * skipping edge cases is fine - no chunk is force-loaded.
 */
@Mixin(targets = "Reika.ChromatiCraft.Block.Worldgen.BlockTieredPlant$TieredPlants", remap = false)
public class MixinTieredPlants {

    /** Horizontal search reach of the widest plant variant (root variant's findTreeNear radius). */
    private static final int CHROMAEXTRAS$PLANT_REACH = 8;

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void chromaextras$skipPlantIfFootprintNotLoaded(World world, int x, int z, Random r,
        CallbackInfoReturnable<Coordinate> cir) {
        for (int cx = (x - CHROMAEXTRAS$PLANT_REACH) >> 4; cx <= (x + CHROMAEXTRAS$PLANT_REACH) >> 4; cx++) {
            for (int cz = (z - CHROMAEXTRAS$PLANT_REACH) >> 4; cz <= (z + CHROMAEXTRAS$PLANT_REACH) >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    cir.setReturnValue(null);
                    return;
                }
            }
        }
    }
}
