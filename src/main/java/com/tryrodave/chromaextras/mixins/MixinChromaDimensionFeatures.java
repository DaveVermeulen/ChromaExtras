package com.tryrodave.chromaextras.mixins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tryrodave.chromaextras.util.DeferredStructureGen;

import Reika.ChromatiCraft.Base.ChromaWorldGenerator;
import Reika.ChromatiCraft.World.Dimension.Generators.WorldGenCrystalPit;
import Reika.ChromatiCraft.World.Dimension.Generators.WorldGenFissure;
import Reika.ChromatiCraft.World.Dimension.Generators.WorldGenFloatstone;
import Reika.ChromatiCraft.World.Dimension.Generators.WorldGenGlowCave;

/**
 * The rare, highly visible ChromatiCraft-dimension (Proxima) features that reach across chunks: floating stone
 * islands ({@link WorldGenFloatstone}, the "stuff in the sky"), glow caves ({@link WorldGenGlowCave}, which wander
 * tens of blocks as they carve downward), fissures ({@link WorldGenFissure}), and crystal-pit geodes
 * ({@link WorldGenCrystalPit}, a ~8-block-radius hollowed ellipsoid). Each is a {@code WorldGenerator} whose
 * {@code generate} reads/writes a region around its origin; run during population, that force-loads neighbor chunks
 * (cascading worldgen lag).
 *
 * <p>
 * An earlier version simply skipped these when their footprint was not loaded. That worked for the dense tree
 * clusters, but for a rare feature whose footprint is almost never fully loaded at population time it meant the
 * feature effectively never generated - the sky islands vanished. So instead of skipping, <b>defer</b>: capture the
 * exact rolled position and hand it to {@link DeferredStructureGen}, which replays the generator once its footprint
 * has been loaded by normal exploration. Nothing is force-loaded and nothing is dropped; the feature just appears a
 * few chunks behind the frontier. (The dense tree cluster keeps its own finer per-tree skip guard.)
 *
 * <p>
 * They all share the inherited {@code generate(World, Random, int, int, int)} signature, so one multi-target guard
 * covers them.
 */
@Mixin(
    value = { WorldGenGlowCave.class, WorldGenFloatstone.class, WorldGenFissure.class, WorldGenCrystalPit.class },
    remap = false)
public class MixinChromaDimensionFeatures {

    /**
     * Horizontal reach, in blocks, that must be loaded before the feature is (re)generated. Sized for the glow cave,
     * whose carve wanders up to ~32 blocks per segment from its origin; a safe superset of the far smaller floatstone
     * and fissure footprints. Must match {@link DeferredStructureGen#enqueueFeature}'s check so the deferred replay
     * passes straight through this same guard.
     */
    private static final int CHROMAEXTRAS$FEATURE_REACH = 48;

    @Inject(method = { "generate", "func_76484_a" }, at = @At("HEAD"), cancellable = true)
    private void chromaextras$deferIfFootprintNotLoaded(World world, Random rand, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        IChunkProvider cp = world.getChunkProvider();
        for (int cx = (x - CHROMAEXTRAS$FEATURE_REACH) >> 4; cx <= (x + CHROMAEXTRAS$FEATURE_REACH) >> 4; cx++) {
            for (int cz = (z - CHROMAEXTRAS$FEATURE_REACH) >> 4; cz <= (z + CHROMAEXTRAS$FEATURE_REACH) >> 4; cz++) {
                if (!cp.chunkExists(cx, cz)) {
                    DeferredStructureGen.enqueueFeature(
                        world.provider.dimensionId,
                        x,
                        y,
                        z,
                        CHROMAEXTRAS$FEATURE_REACH,
                        (ChromaWorldGenerator) (Object) this);
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
