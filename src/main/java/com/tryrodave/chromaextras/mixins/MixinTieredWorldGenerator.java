package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.ChromatiCraft.World.IWG.TieredWorldGenerator;
import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;

/**
 * ChromatiCraft's tiered plant/ore worldgen places blocks via {@code Coordinate#setBlock(World, Block, int)},
 * which delegates to {@code World#setBlock(x, y, z, block, meta, 3)}. Flag bit 1 of that call fires
 * neighbor-notification block updates; when the placed block sits on a chunk edge those updates reach into
 * not-yet-generated neighbor chunks and force them to load mid-population, producing cascading worldgen lag
 * (the "causing cascading worldgen lag" warnings ArchaicFix logs against TieredWorldGenerator.generate).
 *
 * <p>
 * Worldgen block placement should never trigger neighbor updates, so redirect the call to use flag 2
 * (send-to-client only, no neighbor notify). The blocks that end up in the world are identical; only the
 * spurious neighbor updates - and the chunk loads they caused - are removed.
 */
@Mixin(value = TieredWorldGenerator.class, remap = false)
public class MixinTieredWorldGenerator {

    @Redirect(
        method = "generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V",
        at = @At(
            value = "INVOKE",
            target = "LReika/DragonAPI/Instantiable/Data/Immutable/Coordinate;setBlock(Lnet/minecraft/world/World;Lnet/minecraft/block/Block;I)Z"))
    private boolean chromaextras$placeWithoutNeighborNotify(Coordinate coord, World world, Block block, int meta) {
        return coord.setBlock(world, block, meta, 2);
    }
}
