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
     * Chunk radius around a structure cell that must be loaded before it is generated. Covers the dungeon bodies and
     * the pylon (whose tree-dodge offset can shift it ~1 chunk plus its ~5-block broadcast structure).
     */
    private static final int FOOTPRINT_CHUNK_RADIUS = 3;

    /** dimensionId -> (packed cell chunk coords -> generators awaiting that cell). */
    private static final Map<Integer, Map<Long, Set<IWorldGenerator>>> PENDING = new HashMap<>();

    private DeferredStructureGen() {}

    public static synchronized void enqueue(int dimensionId, int cellChunkX, int cellChunkZ,
        IWorldGenerator generator) {
        PENDING.computeIfAbsent(dimensionId, k -> new HashMap<>())
            .computeIfAbsent(pack(cellChunkX, cellChunkZ), k -> new HashSet<>())
            .add(generator);
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
        synchronized (DeferredStructureGen.class) {
            if (PENDING.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<Integer, Map<Long, Set<IWorldGenerator>>>> dims = PENDING.entrySet()
                .iterator();
            while (dims.hasNext()) {
                Map.Entry<Integer, Map<Long, Set<IWorldGenerator>>> dimEntry = dims.next();
                WorldServer world = server.worldServerForDimension(dimEntry.getKey());
                if (world == null) {
                    continue;
                }
                IChunkProvider cp = world.getChunkProvider();
                Iterator<Map.Entry<Long, Set<IWorldGenerator>>> cells = dimEntry.getValue()
                    .entrySet()
                    .iterator();
                while (cells.hasNext()) {
                    Map.Entry<Long, Set<IWorldGenerator>> cellEntry = cells.next();
                    int cx = unpackX(cellEntry.getKey());
                    int cz = unpackZ(cellEntry.getKey());
                    if (isNeighborhoodLoaded(cp, cx, cz)) {
                        for (IWorldGenerator gen : cellEntry.getValue()) {
                            ready.add(new Ready(dimEntry.getKey(), cx, cz, gen));
                        }
                        cells.remove();
                    }
                }
                if (dimEntry.getValue()
                    .isEmpty()) {
                    dims.remove();
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
            try {
                r.generator.generate(rng, r.cellX, r.cellZ, world, cp, cp);
            } catch (Throwable t) {
                FMLCommonHandler.instance()
                    .getFMLLogger()
                    .error(
                        "ChromaExtras: deferred structure generation failed at chunk " + r.cellX + ", " + r.cellZ,
                        t);
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
}
