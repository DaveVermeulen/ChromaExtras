package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.ChromatiCraft.ModInterface.Bees.HiveGenerator;

/**
 * ChromatiCraft's crystal-bee hive worldgen (new with Forestry) scatters up to 5 hives per chunk as an
 * {@code IWorldGenerator}. For each candidate it calls {@code canGenerateAt}, which reads the block's horizontal
 * neighbours via {@code ReikaWorldHelper.checkForAdjBlock} (x+-1, z+-1), and then places the hive with
 * {@code world.setBlock(x, y, z, block, meta, 3)} - flag 3 includes neighbour-notify. A hive rolled on a chunk edge
 * therefore reads and notifies one block into the adjacent chunk; if that chunk is not generated yet it is
 * force-loaded (cascading worldgen lag - the observed cascades were the notify from that {@code setBlock}).
 *
 * <p>
 * Guard {@code canGenerateAt}: if the hive's +-1 footprint touches a chunk that is not currently loaded, report it as
 * unplaceable and skip it before any of its reads run. Because the caller only writes when {@code canGenerateAt}
 * returns true, this also stops the edge {@code setBlock}/notify. Nothing is force-loaded; a hive is dropped only
 * when it lands exactly on a not-yet-generated frontier edge (rare, and there are five candidates per chunk), the
 * same trade already used for the dimension's trees.
 *
 * <p>
 * {@code HiveGenerator} also has a second path: {@code onGenCrystal} listens for {@code CrystalGenEvent} and, when
 * {@code CrystalGenerator} places a white crystal, drops a hive one block below it with
 * {@code setBlock(x, y, z, block, meta, 3)}. Flag 3 fires neighbor-notify updates, and the crystal directly above is
 * a {@code CrystalBlock} whose {@code onNeighborBlockChange} runs an indirect-redstone-power check - that reads the
 * placed block's horizontal neighbours and, on a chunk edge, force-loads the neighbour. Strip the notify bit so the
 * hive placement only sends the client update (flag 2); worldgen placement should never notify neighbours anyway.
 */
@Mixin(value = HiveGenerator.class, remap = false)
public class MixinHiveGenerator {

    @Inject(method = "canGenerateAt", at = @At("HEAD"), cancellable = true)
    private void chromaextras$skipHiveIfFootprintNotLoaded(World world, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        IChunkProvider cp = world.getChunkProvider();
        for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) {
            for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
                if (!cp.chunkExists(cx, cz)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }

    @Redirect(
        method = "onGenCrystal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z",
            remap = true))
    private boolean chromaextras$placeHiveWithoutNeighborNotify(World world, int x, int y, int z, Block block, int meta,
        int flag) {
        return world.setBlock(x, y, z, block, meta, flag & ~1);
    }
}
