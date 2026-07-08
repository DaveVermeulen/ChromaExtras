package com.tryrodave.chromaextras.mixins;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;
import Reika.DragonAPI.Libraries.World.ReikaWorldHelper;

/**
 * Only the tree-attached tiered plant variants (POD/ROOT) reach across chunks: they re-center and call
 * {@code ReikaWorldHelper.findTreeNear(world, x, y, z, 7)}, then read a few blocks around the tree they find. When
 * that search reaches a not-yet-generated neighbor chunk it force-loads it -> cascading worldgen lag. The other
 * variants (flower/lily/desert) only read their own column and never cascade.
 *
 * <p>
 * So rather than skipping the whole plant (which needlessly dropped the in-column variants and, because the search
 * is re-centered, still missed the reads it targeted), gate just the tree search: if its area isn't fully loaded,
 * report "no tree found" (null) so the plant simply isn't placed there - a vine can't attach to a tree that doesn't
 * exist yet anyway. In-column variants are untouched and generate exactly as before; nothing is force-loaded.
 */
@Mixin(targets = "Reika.ChromatiCraft.Block.Worldgen.BlockTieredPlant$TieredPlants", remap = false)
public class MixinTieredPlants {

    @Redirect(
        method = "generate",
        at = @At(
            value = "INVOKE",
            target = "LReika/DragonAPI/Libraries/World/ReikaWorldHelper;findTreeNear(Lnet/minecraft/world/World;IIII)LReika/DragonAPI/Instantiable/Data/Immutable/Coordinate;"))
    private Coordinate chromaextras$findTreeOnlyIfLoaded(World world, int x, int y, int z, int r) {
        int reach = r + 1; // the post-search checks read one block past the located tree
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
            for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    return null;
                }
            }
        }
        return ReikaWorldHelper.findTreeNear(world, x, y, z, r);
    }
}
