package com.tryrodave.chromaextras.mixins;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;

/**
 * DragonAPI records every block a mod's worldgen changes, and after a chunk populates its
 * {@code WorldGenInterceptionRegistry.postPopulation} replays ChromatiCraft's {@code ChromaAux.populationWatcher}
 * ({@code ChromaAux$3.onChunkGeneration}) over that record. For each recorded block in a rainbow-forest or
 * glowing-cliffs biome it does post-gen tweaking - {@code dat.getTileEntity(world)} (to retune mob spawners /
 * Thaumcraft
 * nodes) and {@code dat.revert(world)}. Those calls resolve a {@link Coordinate}; when the recorded coordinate lies in
 * a chunk that is not currently loaded, {@code getTileEntity}/{@code revert} force-load (generate) it - cascading
 * worldgen lag (the observed cascade was a spawner {@code getTileEntity}).
 *
 * <p>
 * Filter the block-set's key set to only coordinates whose chunk is currently loaded, so the loop never reads or
 * writes into an unloaded neighbour. A block that happens to sit in a not-yet-loaded chunk simply skips its cosmetic
 * post-gen tweak - it is already placed - and nothing is force-loaded. Redirecting {@code keySet()} covers every
 * per-coordinate operation in the method at once (tile-entity retune, revert, biome read) and cannot NPE a caller,
 * unlike making {@code getTileEntity} return null.
 */
@Mixin(targets = "Reika.ChromatiCraft.Auxiliary.ChromaAux$3", remap = false)
public class MixinChromaAuxPopulationWatcher {

    // ordinal = 0 pins this to the first keySet() call - the loop over the block-set map. The method has two more
    // keySet() calls on Thaumcraft AspectLists (retuning node aspects); redirecting those too would cast an Aspect to
    // Coordinate and crash.
    @Redirect(
        method = "onChunkGeneration",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;", ordinal = 0))
    private Set<?> chromaextras$onlyLoadedCoordinates(Map<?, ?> blockSet, World world, Map<?, ?> unused) {
        IChunkProvider cp = world.getChunkProvider();
        Set<Object> loaded = new HashSet<>();
        for (Object key : blockSet.keySet()) {
            Coordinate c = (Coordinate) key;
            if (cp.chunkExists(c.xCoord >> 4, c.zCoord >> 4)) {
                loaded.add(key);
            }
        }
        return loaded;
    }
}
