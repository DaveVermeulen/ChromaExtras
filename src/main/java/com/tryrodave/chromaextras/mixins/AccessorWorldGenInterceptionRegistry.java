package com.tryrodave.chromaextras.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import Reika.DragonAPI.Auxiliary.WorldGenInterceptionRegistry;

/**
 * Exposes DragonAPI's private {@code runningChunkDecoration} counter. That counter gates whether the SetBlock
 * listener records worldgen block changes (it only records while {@code > 0}); {@code postPopulation} then hands the
 * recorded set to watchers such as ChromatiCraft's ChromaAux (which tunes dungeon mob spawners, Thaumcraft nodes,
 * etc.) and decrements it.
 *
 * <p>
 * {@link com.tryrodave.chromaextras.util.DeferredStructureGen} generates deferred structures outside the normal
 * population window, so it must open this window itself (increment, generate, then call {@code postPopulation}) or
 * the structure's post-generation tuning would be silently skipped.
 */
@Mixin(value = WorldGenInterceptionRegistry.class, remap = false)
public interface AccessorWorldGenInterceptionRegistry {

    @Accessor("runningChunkDecoration")
    int chromaextras$getRunningChunkDecoration();

    @Accessor("runningChunkDecoration")
    void chromaextras$setRunningChunkDecoration(int value);
}
