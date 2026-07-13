package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops the "tile entity at a chunk edge" cascade, seen with Forestry's bee hives: {@code HiveDecorator.setHive}
 * places the hive inside the chunk being populated (legal), but hive blocks carry tile entities, and vanilla's
 * {@code setTileEntity} unconditionally runs {@code World.func_147453_f} - the comparator/neighbor-change probe -
 * which {@code getBlock}s all six adjacent positions (and one further past normal cubes). When the hive sits on a
 * chunk border, that probe reads into a not-yet-generated chunk and force-generates it mid-population: a synchronous
 * multi-decorator chunk generation on the server thread (the ArchaicFix "cascading worldgen" stack through
 * {@code func_147455_a -> func_147453_f -> getBlock -> loadChunk}).
 *
 * <p>
 * Guard the probe's reads: if the neighbor's chunk is not loaded, report air instead of loading it. This is exact,
 * not an approximation - an ungenerated chunk cannot contain an active comparator or any block with weak changes to
 * notify, and modern Minecraft guards this very call with {@code isBlockLoaded} checks. Nothing is skipped or lost:
 * the tile-entity block (the hive) is already placed by the time this runs, and probes into loaded chunks behave
 * exactly as before. Covers every mod that places TE blocks at the populate frontier, not just Forestry.
 */
@Mixin(World.class)
public class MixinWorldComparatorProbe {

    @Redirect(
        method = "func_147453_f",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;"))
    private Block chromaextras$probeWithoutLoadingChunks(World world, int x, int y, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4) ? world.getBlock(x, y, z) : Blocks.air;
    }
}
