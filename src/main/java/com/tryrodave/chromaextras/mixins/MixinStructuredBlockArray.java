package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.DragonAPI.Instantiable.Data.BlockStruct.StructuredBlockArray;

/**
 * {@code StructuredBlockArray.addBlockCoordinate} reads the current world block/metadata at every coordinate added.
 * ChromatiCraft's structure worldgen builds its templates through {@code FilledBlockArray.setBlock}, which calls
 * this super method and then immediately overwrites the stored value with the intended block - so for template
 * building the world read is pure waste, yet it force-loads neighbor chunks and produces cascading worldgen lag
 * (e.g. DungeonGenerator -> ChromaStructures.getArray -> CavernStructure.getArray).
 *
 * <p>
 * Guard both reads so they never load a chunk: if the coordinate's chunk is not currently loaded, return a
 * placeholder (air / meta 0) instead of forcing the chunk to generate. During normal runtime scans the surrounding
 * chunks are loaded, so this is a no-op there; it only changes the not-yet-generated worldgen case, where the read
 * value is discarded (FilledBlockArray) or the caller is already guarded against unloaded footprints.
 */
@Mixin(value = StructuredBlockArray.class, remap = false)
public class MixinStructuredBlockArray {

    @Redirect(
        method = "addBlockCoordinate(III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;",
            remap = true))
    private Block chromaextras$getBlockWithoutLoading(World world, int x, int y, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4) ? world.getBlock(x, y, z) : Blocks.air;
    }

    @Redirect(
        method = "addBlockCoordinate(III)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockMetadata(III)I", remap = true))
    private int chromaextras$getMetaWithoutLoading(World world, int x, int y, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4) ? world.getBlockMetadata(x, y, z) : 0;
    }
}
