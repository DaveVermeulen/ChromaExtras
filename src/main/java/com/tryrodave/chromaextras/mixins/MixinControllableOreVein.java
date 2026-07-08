package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.DragonAPI.Instantiable.Worldgen.ControllableOreVein;

/**
 * DragonAPI's {@code ControllableOreVein} (used by, e.g., the glowing-cliffs biome decorator) scatters ore by
 * scanning a box around each vein node: {@code world.getBlock(...)} then, if replaceable, {@code setBlock(...)}. The
 * box crosses chunk boundaries, so the reads force-load neighbor chunks during biome decoration -> cascading
 * worldgen lag (this was the bulk of the remaining glowing-cliffs cascades - the biome decorator, a separate path
 * from the aux generator).
 *
 * <p>
 * Guard the read: if a position's chunk isn't loaded, report a non-replaceable block (bedrock) instead of loading
 * it. The vein then skips that position - and since the {@code setBlock} only runs on a replaceable result, the
 * write is skipped too. The vein simply truncates at the chunk seam, exactly like vanilla ore, which likewise never
 * bleeds into ungenerated chunks. During normal runtime this generator isn't used, so there is no runtime impact.
 */
@Mixin(value = ControllableOreVein.class, remap = false)
public class MixinControllableOreVein {

    @Redirect(
        method = { "generate", "func_76484_a" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;",
            remap = true))
    private Block chromaextras$getBlockWithoutLoading(World world, int x, int y, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4) ? world.getBlock(x, y, z) : Blocks.bedrock;
    }
}
