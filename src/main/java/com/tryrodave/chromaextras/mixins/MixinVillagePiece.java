package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.DragonAPI.Instantiable.Worldgen.VillageBuilding;

/**
 * Fixes the cascading-worldgen loads from DragonAPI's custom village structures (e.g. ChromatiCraft's
 * {@code VillagersFailChromatiCraft$BrokenChromaStructure}) <b>without changing what generates</b>.
 *
 * <p>
 * Unlike vanilla village pieces, DragonAPI's {@code VillagePiece} deliberately ignores the per-chunk clip box: its
 * {@code placeBlockAtCurrentPosition} has the {@code structureBox.isVecInside} check commented out, and
 * {@code rise()} samples the structure's <em>entire</em> footprint via absolute-coordinate {@code world.getBlock}.
 * So the first chunk the structure intersects builds the whole thing in one pass, force-loading (and generating)
 * every neighboring chunk it touches - the {@code rise -> getBlockAtFixedPosition -> loadChunk} cascades in the log.
 *
 * <p>
 * The fix leans on vanilla structure machinery: {@code addComponentParts} is invoked again for <em>every</em> chunk
 * the piece's bounding box intersects, as each of those chunks generates. We skip the passes that run while part of
 * the footprint is still ungenerated (returning {@code true} keeps the component queued rather than discarding it),
 * and the pass that runs once the <b>last</b> footprint chunk exists performs the full, unclipped build - one
 * {@code rise()} over complete terrain data, every block written into loaded chunks. Output is identical to stock
 * (same single-pass build, same ground sampling - just on real data instead of force-generated frontier chunks);
 * only the forced chunk loads are gone. Same deferral trade-off as the rest of this pack's cascade fixes: the
 * structure appears when its full area has generated naturally.
 */
@Mixin(value = VillageBuilding.VillagePiece.class, remap = false)
public abstract class MixinVillagePiece {

    @Inject(method = { "addComponentParts", "func_74875_a" }, at = @At("HEAD"), cancellable = true)
    private void chromaextras$waitForFullFootprint(World world, Random rand, StructureBoundingBox clip,
        CallbackInfoReturnable<Boolean> cir) {
        StructureBoundingBox bb = ((StructureComponent) (Object) this).getBoundingBox();
        if (bb == null) {
            return;
        }
        for (int cx = bb.minX >> 4; cx <= bb.maxX >> 4; cx++) {
            for (int cz = bb.minZ >> 4; cz <= bb.maxZ >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    // Part of the footprint is still ungenerated: skip this pass. Returning true keeps the
                    // component in the structure, so the pass for a later footprint chunk builds it in full.
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
