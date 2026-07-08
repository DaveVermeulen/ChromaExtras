package com.tryrodave.chromaextras.mixins;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.ChromatiCraft.World.IWG.PylonGenerator;

/**
 * {@code PylonGenerator#canGenerateAt(World, int, int, int)} validates a candidate pylon spot by scanning a small
 * structural footprint around it, reading blocks up to 4 columns away in each horizontal direction (via
 * {@code StructuredBlockArray#addBlockCoordinate} -> {@code World#getBlock}). When the candidate is near a chunk edge
 * those reads land in not-yet-generated neighbor chunks and force them to load during population.
 *
 * <p>
 * Report "cannot generate here" whenever the footprint would touch a chunk that is not currently loaded.
 * {@code tryForceGenerate} retries up to 24 random spots in the pylon's own (loaded) chunk, and the 8x8 interior of a
 * chunk never needs a neighbor, so a pylon still generates - it just settles on an interior spot instead of forcing a
 * neighbor to load. (Unlike the large dungeons, the pylon footprint is tiny and the retry budget is per-chunk, so
 * this does not drop pylons.)
 */
@Mixin(value = PylonGenerator.class, remap = false)
public class MixinPylonGenerator {

    /** Max horizontal offset read by the footprint scan in canGenerateAt (dir*3 plus a 1-block perpendicular). */
    private static final int CHROMAEXTRAS$FOOTPRINT_REACH = 4;

    @Inject(method = "canGenerateAt(Lnet/minecraft/world/World;III)Z", at = @At("HEAD"), cancellable = true)
    private void chromaextras$skipIfFootprintNotLoaded(World world, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        int cxMin = (x - CHROMAEXTRAS$FOOTPRINT_REACH) >> 4;
        int cxMax = (x + CHROMAEXTRAS$FOOTPRINT_REACH) >> 4;
        int czMin = (z - CHROMAEXTRAS$FOOTPRINT_REACH) >> 4;
        int czMax = (z + CHROMAEXTRAS$FOOTPRINT_REACH) >> 4;
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
