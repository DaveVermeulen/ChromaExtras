package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import binnie.extrabees.worldgen.WorldGenHiveWater;

/**
 * Extra Bees (Binnie) scatters water hives with {@code WorldGenHiveWater}. Its {@code generate} picks an origin
 * anywhere in the chunk being populated, then offsets it by up to +-7 blocks ({@code i + rand(8) - rand(8)}) and reads
 * that spot with {@code world.getBlock} - six reads in all: two water checks and four substrate-material checks one
 * block below. When the rolled origin sits near a chunk edge, that +-7 offset lands in a not-yet-generated neighbor
 * chunk, and {@code getBlock} force-loads it mid-population -> cascading worldgen lag (every observed cascade in the
 * pack was one of these reads; Extra Bees runs as an {@code IWorldGenerator} routed through DragonAPI's interception).
 *
 * <p>
 * Guard the read: if the target position's chunk is not currently loaded, report air instead of loading it. Air is not
 * water, so the very first check ({@code getBlock != water}) makes {@code generate} return early and the hive is simply
 * not placed there - and since the placement {@code setBlock} only runs after a successful water read, the write is
 * skipped too. A water hive is dropped only when its rolled spot straddles a not-yet-generated frontier edge (rare, and
 * there are {@code waterHiveRate} rolls per chunk), the same trade already used for the other read-guarded generators
 * ({@code ControllableOreVein}, {@code StructuredBlockArray}). During normal runtime the neighbor chunks are loaded, so
 * this is a no-op there.
 */
@Mixin(value = WorldGenHiveWater.class, remap = false)
public class MixinWorldGenHiveWater {

    @Redirect(
        method = { "generate", "func_76484_a" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;",
            remap = true))
    private Block chromaextras$getBlockWithoutLoading(World world, int x, int y, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4) ? world.getBlock(x, y, z) : Blocks.air;
    }
}
