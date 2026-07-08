package com.tryrodave.chromaextras.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;

import com.tryrodave.chromaextras.mixins.AccessorWorldGenInterceptionRegistry;

import Reika.ChromatiCraft.Base.ChromaWorldGenerator;
import Reika.DragonAPI.Auxiliary.WorldGenInterceptionRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IWorldGenerator;

/**
 * Deferred generation for ChromatiCraft's grid structures (dungeon/temple and pylon worldgen).
 *
 * <p>
 * These structures span several chunks, so placing one during a chunk's population reads/writes neighbor chunks and
 * force-loads them (cascading worldgen lag). The generator mixins instead defer a structure cell whose surrounding
 * footprint is not yet loaded - without dropping it - and register it here. This queue retries the cell on the
 * server tick and generates it once its footprint has been loaded by normal exploration, so no chunk is ever
 * force-loaded and generation is otherwise identical to vanilla (e.g. the pylon still gets its full 24-try
 * placement search, just in a fully-loaded neighborhood).
 *
 * <p>
 * Generation is a two-phase pass: collect the cells that are ready and remove them from the queue, then generate
 * them <em>after</em> releasing the iterator. Generating re-enters the worldgen path (a nested populate can call
 * back into {@link #enqueue}), so we must not be iterating the queue while it runs - doing so previously threw
 * {@link java.util.ConcurrentModificationException} and crashed the server.
 */
public final class DeferredStructureGen {

    /**
     * Chunk radius around a structure cell that must be loaded before it is generated. Sized for the largest
     * deferred feature - a glowing-cliffs island (outer radius ~36 blocks plus decorations/ore veins/trees) - which
     * is a safe superset of the smaller dungeon and pylon footprints, so a single check works for all of them.
     */
    private static final int FOOTPRINT_CHUNK_RADIUS = 4;

    /**
     * Per-tick throttle. When a large area loads at once (e.g. the chunk spiral generated on teleport), a whole
     * backlog of deferred cells/features becomes ready on the same tick; generating all of them in one tick is a
     * multi-second server stall ("Can't keep up! ... skipping N ticks"). Instead, generate at most this many per
     * tick and leave the rest queued, so the backlog drains smoothly over the following ticks. Structures
     * (dungeons/pylons/islands) are far heavier than the point-features, so they get the smaller budget.
     */
    private static final int MAX_STRUCTURES_PER_TICK = 1;
    private static final int MAX_FEATURES_PER_TICK = 3;

    /** dimensionId -> (packed cell chunk coords -> generators awaiting that cell). */
    private static final Map<Integer, Map<Long, Set<IWorldGenerator>>> PENDING = new HashMap<>();

    /**
     * dimensionId -> point-features awaiting their footprint. The ChromatiCraft dimension (Proxima) populates via its
     * own {@code ChunkProviderChroma.runDecorators} pipeline (not {@code IWorldGenerator}), so its rare, highly
     * visible features - floating stone islands, glow caves, fissures - are deferred as individual
     * {@link ChromaWorldGenerator} calls at a captured block position rather than as chunk-cell structures. Skipping
     * these outright (as an earlier version did) made the sky islands effectively disappear, since their footprint is
     * almost never fully loaded at population time.
     */
    private static final Map<Integer, List<DeferredFeature>> PENDING_FEATURES = new HashMap<>();

    private DeferredStructureGen() {}

    public static synchronized void enqueue(int dimensionId, int cellChunkX, int cellChunkZ,
        IWorldGenerator generator) {
        PENDING.computeIfAbsent(dimensionId, k -> new HashMap<>())
            .computeIfAbsent(pack(cellChunkX, cellChunkZ), k -> new HashSet<>())
            .add(generator);
    }

    /**
     * Defer a ChromatiCraft-dimension point-feature until its footprint is loaded, then replay it verbatim. The
     * generator is re-invoked with a fresh, position-deterministic RNG once every chunk within {@code blockReach} of
     * the origin exists, so it reads no unloaded chunk and nothing cascades. The feature is preserved (only delayed a
     * few chunks behind the exploration frontier) rather than dropped.
     *
     * @param blockReach horizontal reach, in blocks, of the feature from its origin - must cover the whole footprint,
     *                   and must match the guard reach the generator's mixin uses so the deferred replay passes
     *                   straight through its own guard instead of re-deferring.
     */
    public static synchronized void enqueueFeature(int dimensionId, int blockX, int blockY, int blockZ, int blockReach,
        ChromaWorldGenerator generator) {
        PENDING_FEATURES.computeIfAbsent(dimensionId, k -> new ArrayList<>())
            .add(new DeferredFeature(blockX, blockY, blockZ, blockReach, generator));
    }

    /** Called every server tick; generates any deferred cell whose footprint is now fully loaded. */
    public static void tick() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        // Phase 1: under the lock, gather generators for cells that are ready and remove those cells from the queue.
        // No generation here, so nothing can re-enter and mutate the queue while we iterate.
        List<Ready> ready = new ArrayList<>();
        List<ReadyFeature> readyFeatures = new ArrayList<>();
        synchronized (DeferredStructureGen.class) {
            if (PENDING.isEmpty() && PENDING_FEATURES.isEmpty()) {
                return;
            }
            int structureBudget = MAX_STRUCTURES_PER_TICK;
            Iterator<Map.Entry<Integer, Map<Long, Set<IWorldGenerator>>>> dims = PENDING.entrySet()
                .iterator();
            while (dims.hasNext() && structureBudget > 0) {
                Map.Entry<Integer, Map<Long, Set<IWorldGenerator>>> dimEntry = dims.next();
                WorldServer world = server.worldServerForDimension(dimEntry.getKey());
                if (world == null) {
                    continue;
                }
                IChunkProvider cp = world.getChunkProvider();
                Iterator<Map.Entry<Long, Set<IWorldGenerator>>> cells = dimEntry.getValue()
                    .entrySet()
                    .iterator();
                while (cells.hasNext() && structureBudget > 0) {
                    Map.Entry<Long, Set<IWorldGenerator>> cellEntry = cells.next();
                    int cx = unpackX(cellEntry.getKey());
                    int cz = unpackZ(cellEntry.getKey());
                    if (isNeighborhoodLoaded(cp, cx, cz)) {
                        for (IWorldGenerator gen : cellEntry.getValue()) {
                            ready.add(new Ready(dimEntry.getKey(), cx, cz, gen));
                        }
                        cells.remove();
                        structureBudget--;
                    }
                }
                if (dimEntry.getValue()
                    .isEmpty()) {
                    dims.remove();
                }
            }

            // Same two-phase, budgeted treatment for the ChromatiCraft-dimension point-features.
            int featureBudget = MAX_FEATURES_PER_TICK;
            Iterator<Map.Entry<Integer, List<DeferredFeature>>> fdims = PENDING_FEATURES.entrySet()
                .iterator();
            while (fdims.hasNext() && featureBudget > 0) {
                Map.Entry<Integer, List<DeferredFeature>> dimEntry = fdims.next();
                WorldServer world = server.worldServerForDimension(dimEntry.getKey());
                if (world == null) {
                    continue;
                }
                IChunkProvider cp = world.getChunkProvider();
                Iterator<DeferredFeature> it = dimEntry.getValue()
                    .iterator();
                while (it.hasNext() && featureBudget > 0) {
                    DeferredFeature f = it.next();
                    if (isBlockFootprintLoaded(cp, f.x, f.z, f.reach)) {
                        readyFeatures.add(new ReadyFeature(dimEntry.getKey(), f));
                        it.remove();
                        featureBudget--;
                    }
                }
                if (dimEntry.getValue()
                    .isEmpty()) {
                    fdims.remove();
                }
            }
        }

        // Phase 2: generate outside the lock/iteration. A re-entrant enqueue() (nested populate) is now safe, and a
        // per-cell try/catch keeps one failed structure from taking down the server tick.
        for (Ready r : ready) {
            WorldServer world = server.worldServerForDimension(r.dimensionId);
            if (world == null) {
                continue;
            }
            IChunkProvider cp = world.getChunkProvider();
            Random rng = new Random(
                world.getSeed() ^ ((long) r.cellX * 341873128712L) ^ ((long) r.cellZ * 132897987541L));
            // Open DragonAPI's worldgen-decoration window so the block changes this structure makes are recorded,
            // then run postPopulation so ChromatiCraft's post-gen tuning (dungeon spawner rates, Thaumcraft nodes,
            // ...) is applied just as it would be during a normal populate. The footprint is loaded, so this reads
            // no unloaded chunks. postPopulation decrements the counter, balancing the increment below.
            AccessorWorldGenInterceptionRegistry accessor = (AccessorWorldGenInterceptionRegistry) (Object) WorldGenInterceptionRegistry.instance;
            accessor.chromaextras$setRunningChunkDecoration(accessor.chromaextras$getRunningChunkDecoration() + 1);
            try {
                r.generator.generate(rng, r.cellX, r.cellZ, world, cp, cp);
            } catch (Throwable t) {
                FMLCommonHandler.instance()
                    .getFMLLogger()
                    .error(
                        "ChromaExtras: deferred structure generation failed at chunk " + r.cellX + ", " + r.cellZ,
                        t);
            } finally {
                WorldGenInterceptionRegistry.instance.postPopulation(world, r.cellX, r.cellZ);
            }
        }

        // Phase 2 for point-features: replay the generator exactly as runDecorators would, but now that the footprint
        // is loaded. Its own mixin guard re-checks the same reach and, seeing everything loaded, passes straight
        // through to the real generation. No WorldGenInterceptionRegistry window here - the Chroma dimension does not
        // use one for its decorators (runDecorators just calls generate directly), so we match that.
        for (ReadyFeature rf : readyFeatures) {
            WorldServer world = server.worldServerForDimension(rf.dimensionId);
            if (world == null) {
                continue;
            }
            DeferredFeature f = rf.feature;
            Random rng = new Random(world.getSeed() ^ ((long) f.x * 341873128712L) ^ ((long) f.z * 132897987541L));
            try {
                f.generator.generate(world, rng, f.x, f.y, f.z);
            } catch (Throwable t) {
                FMLCommonHandler.instance()
                    .getFMLLogger()
                    .error("ChromaExtras: deferred feature generation failed at " + f.x + ", " + f.y + ", " + f.z, t);
            }
        }
    }

    private static boolean isBlockFootprintLoaded(IChunkProvider cp, int blockX, int blockZ, int blockReach) {
        for (int cx = (blockX - blockReach) >> 4; cx <= (blockX + blockReach) >> 4; cx++) {
            for (int cz = (blockZ - blockReach) >> 4; cz <= (blockZ + blockReach) >> 4; cz++) {
                if (!cp.chunkExists(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isNeighborhoodLoaded(IChunkProvider cp, int cx, int cz) {
        for (int dx = -FOOTPRINT_CHUNK_RADIUS; dx <= FOOTPRINT_CHUNK_RADIUS; dx++) {
            for (int dz = -FOOTPRINT_CHUNK_RADIUS; dz <= FOOTPRINT_CHUNK_RADIUS; dz++) {
                if (!cp.chunkExists(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long pack(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    private static int unpackX(long v) {
        return (int) (v & 0xffffffffL);
    }

    private static int unpackZ(long v) {
        return (int) ((v >> 32) & 0xffffffffL);
    }

    private static final class Ready {

        private final int dimensionId;
        private final int cellX;
        private final int cellZ;
        private final IWorldGenerator generator;

        private Ready(int dimensionId, int cellX, int cellZ, IWorldGenerator generator) {
            this.dimensionId = dimensionId;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.generator = generator;
        }
    }

    /** A ChromatiCraft-dimension feature awaiting its footprint, captured at the exact block position it was rolled. */
    private static final class DeferredFeature {

        private final int x;
        private final int y;
        private final int z;
        private final int reach;
        private final ChromaWorldGenerator generator;

        private DeferredFeature(int x, int y, int z, int reach, ChromaWorldGenerator generator) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.reach = reach;
            this.generator = generator;
        }
    }

    private static final class ReadyFeature {

        private final int dimensionId;
        private final DeferredFeature feature;

        private ReadyFeature(int dimensionId, DeferredFeature feature) {
            this.dimensionId = dimensionId;
            this.feature = feature;
        }
    }
}
