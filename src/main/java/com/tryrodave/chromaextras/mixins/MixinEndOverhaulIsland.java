package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tryrodave.chromaextras.util.EndIslandDeferral;

import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;

/**
 * {@code EndOverhaulManager.Island.generate} is invoked once, from {@code setSeed}, while ChromatiCraft computes the
 * whole End island layout - and it writes the island's end_stone straight into the world at absolute coordinates far
 * from the chunk being populated, force-loading every chunk an island touches (the End "cascading worldgen lag").
 *
 * <p>
 * Redirect that write into {@link EndIslandDeferral} instead of the world. The blocks are then placed per-chunk as
 * each chunk populates (see {@code MixinEndOverhaulManager}), exactly like the tendril cores ChromatiCraft already
 * defers. {@code runGen1}/{@code runGen2} do no world I/O, so this single write is the only source to intercept.
 */
@Mixin(targets = "Reika.ChromatiCraft.World.EndOverhaulManager$Island", remap = false)
public class MixinEndOverhaulIsland {

    @Redirect(
        method = "generate",
        at = @At(
            value = "INVOKE",
            target = "LReika/DragonAPI/Instantiable/Data/Immutable/Coordinate;setBlock(Lnet/minecraft/world/World;Lnet/minecraft/block/Block;II)Z"))
    private boolean chromaextras$deferIslandBlock(Coordinate coord, World world, Block block, int meta, int flag) {
        return EndIslandDeferral.record(coord.xCoord, coord.yCoord, coord.zCoord, block, meta);
    }
}
