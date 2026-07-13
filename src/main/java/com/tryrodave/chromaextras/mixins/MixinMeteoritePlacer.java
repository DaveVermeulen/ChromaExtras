package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.worldgen.MeteoritePlacer;
import appeng.worldgen.meteorite.IMeteoriteWorld;

/**
 * Companion to {@link MixinMeteoriteSpawn}: guarantees every placed meteorite carves its <b>crater</b> and runs its
 * decay pass.
 *
 * <p>
 * Stock AE2 gates both on {@code skyMode} - a count of sky-visible blocks in a 30x26x30 box around the impact point
 * ({@code crater if skyMode > 10}, {@code decay if skyMode > 3}), with {@code skyMode} additionally zeroed whenever any
 * air pocket sits in the 14 blocks below the centre. Forest canopy (Rainbow Forest!), overhangs, and Immersive
 * Cavegen's caves all crush that count, so meteorites frequently placed without any visible crater - the main reason
 * they were so hard to find.
 *
 * <p>
 * Since {@link MixinMeteoriteSpawn} now guarantees the meteorite sits at the true terrain surface, a crater is always
 * appropriate: rewriting the thresholds to {@code Integer.MIN_VALUE} makes the crater and decay unconditional in both
 * placement paths (validated centre placement and the forced/neighbor-chunk path). Crater size, shape, fallout type and
 * loot are untouched.
 */
@Mixin(value = MeteoritePlacer.class, remap = false)
public class MixinMeteoritePlacer {

    @ModifyConstant(
        method = { "spawnMeteorite", "spawnMeteoriteCenter" },
        constant = @Constant(intValue = 10),
        require = 2)
    private int chromaextras$alwaysCrater(int skyModeCraterThreshold) {
        return Integer.MIN_VALUE;
    }

    @ModifyConstant(
        method = { "spawnMeteorite", "spawnMeteoriteCenter" },
        constant = @Constant(intValue = 3),
        require = 2)
    private int chromaextras$alwaysDecay(int skyModeDecayThreshold) {
        return Integer.MIN_VALUE;
    }

    /**
     * The decay pass "collapses" terrain: any block resting above a replaceable block is pulled down one
     * ({@code setBlock(j, blockAbove)} then {@code setBlock(j+1, air)}). When an air pocket sits below the meteorite
     * (common on the modded terrain the forced placement now allows), that collapse dominoes up the column until it
     * reaches the <b>loot chest</b>: the chest block is cloned downward with a fresh EMPTY tile entity, and clearing
     * its old position fires {@code breakBlock}, which dumps the filled inventory on the ground - the "loot lying on
     * the meteor" bug. Guard both writes: never pull a tile-entity block down, and never overwrite one with the
     * collapse's air. Protects the sky stone chest and any mod machine caught in the 30x39x30 decay box; plain
     * terrain/skystone collapse is untouched.
     */
    @Redirect(
        method = "decay",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/worldgen/meteorite/IMeteoriteWorld;setBlock(IIILnet/minecraft/block/Block;II)V"))
    private void chromaextras$dontCollapseTileEntityBlocks(IMeteoriteWorld w, int x, int y, int z, Block moved,
        int meta, int flags) {
        if (!moved.hasTileEntity(meta)) {
            w.setBlock(x, y, z, moved, meta, flags);
        }
    }

    @Redirect(
        method = "decay",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/worldgen/meteorite/IMeteoriteWorld;setBlock(IIILnet/minecraft/block/Block;)V"))
    private void chromaextras$dontBreakTileEntityBlocks(IMeteoriteWorld w, int x, int y, int z, Block replacement) {
        Block current = w.getBlock(x, y, z);
        if (!current.hasTileEntity(w.getBlockMetadata(x, y, z))) {
            w.setBlock(x, y, z, replacement);
        }
    }
}
