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

import Reika.ChromatiCraft.World.IWG.DungeonGenerator;
import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Deferred generation for ChromatiCraft's grid structures (Dungeon/temple worldgen).
 *
 * <p>
 * These structures span several chunks, so placing one during a chunk's population reads/writes neighbor chunks and
 * force-loads them (cascading worldgen lag). {@code MixinDungeonGenerator} instead defers a structure cell whose
 * surrounding footprint is not yet loaded - crucially <em>without</em> marking it failed, so ChromatiCraft's own
 * persisted status keeps it {@code PLANNED}. This queue then retries those cells on the server tick and generates
 * each one once its footprint has been loaded by normal exploration - no chunk is force-loaded, and no structure is
 * dropped (the earlier "skip and mark dead" behaviour deleted them permanently).
 *
 * <p>
 * Generation is a two-phase pass: first collect the cells that are ready and remove them from the queue, then
 * generate them <em>after</em> releasing the iterator. Generating re-enters the worldgen path (a nested populate can
 * call back into {@link #enqueue}), so we must not be iterating the queue while it runs - doing so previously threw
 * {@link java.util.ConcurrentModificationException} and crashed the server.
 */
public final class DeferredStructureGen {

    /**
     * Chunk radius around a structure cell that must be loaded before it is generated. Covers the structure body
     * plus the short probes/tunnels that reach past it; the rare far-search fallback can still load a chunk, but the
     * common (in-cell) placement no longer does.
     */
    private static final int FOOTPRINT_CHUNK_RADIUS = 3;

    /** dimensionId -> set of packed structure-cell chunk coordinates awaiting generation. */
    private static final Map<Integer, Set<Long>> PENDING = new HashMap<>();

    private DeferredStructureGen() {}

    public static synchronized void enqueue(int dimensionId, int cellChunkX, int cellChunkZ) {
        PENDING.computeIfAbsent(dimensionId, k -> new HashSet<>())
            .add(pack(cellChunkX, cellChunkZ));
    }

    /** Called every server tick; generates any deferred cell whose footprint is now fully loaded. */
    public static void tick() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        // Phase 1: under the lock, gather cells that are ready and remove them from the queue. No generation here,
        // so nothing can re-enter and mutate the queue while we iterate.
        List<long[]> ready = new ArrayList<>();
        synchronized (DeferredStructureGen.class) {
            if (PENDING.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<Integer, Set<Long>>> dims = PENDING.entrySet()
                .iterator();
            while (dims.hasNext()) {
                Map.Entry<Integer, Set<Long>> entry = dims.next();
                WorldServer world = server.worldServerForDimension(entry.getKey());
                if (world == null) {
                    continue;
                }
                IChunkProvider cp = world.getChunkProvider();
                Iterator<Long> cells = entry.getValue()
                    .iterator();
                while (cells.hasNext()) {
                    long packed = cells.next();
                    if (isNeighborhoodLoaded(cp, unpackX(packed), unpackZ(packed))) {
                        ready.add(new long[] { entry.getKey(), packed });
                        cells.remove();
                    }
                }
                if (entry.getValue()
                    .isEmpty()) {
                    dims.remove();
                }
            }
        }

        // Phase 2: generate outside the lock/iteration. A re-entrant enqueue() (nested populate) is now safe, and a
        // per-cell try/catch keeps one failed structure from taking down the server tick.
        for (long[] cell : ready) {
            WorldServer world = server.worldServerForDimension((int) cell[0]);
            if (world == null) {
                continue;
            }
            IChunkProvider cp = world.getChunkProvider();
            int cx = unpackX(cell[1]);
            int cz = unpackZ(cell[1]);
            Random rng = new Random(world.getSeed() ^ ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L));
            try {
                DungeonGenerator.instance.generate(rng, cx, cz, world, cp, cp);
            } catch (Throwable t) {
                FMLCommonHandler.instance()
                    .getFMLLogger()
                    .error("ChromaExtras: deferred structure generation failed at chunk " + cx + ", " + cz, t);
            }
        }
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
}
