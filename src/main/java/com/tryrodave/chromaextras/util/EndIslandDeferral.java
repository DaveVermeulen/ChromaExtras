package com.tryrodave.chromaextras.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import Reika.DragonAPI.Instantiable.Data.Immutable.BlockKey;
import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;

/**
 * Buffers the End-overhaul island blocks that ChromatiCraft's {@code EndOverhaulManager} would otherwise write
 * straight into far, ungenerated chunks during its one-shot layout computation (loading every chunk an island
 * touches - the End cascade). Instead they are recorded here, keyed by chunk, and placed as each chunk populates -
 * the same chunk-safe deferral ChromatiCraft already uses for its tendril cores.
 *
 * <p>
 * Worldgen is single-threaded in 1.7.10, so this plain static buffer needs no synchronization.
 */
public final class EndIslandDeferral {

    private static final Map<Long, Map<Coordinate, BlockKey>> BUFFER = new HashMap<>();

    private EndIslandDeferral() {}

    /** Record a would-be island world write; returns true to satisfy the redirected setBlock's boolean contract. */
    public static boolean record(int x, int y, int z, Block block, int meta) {
        long key = ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4);
        BUFFER.computeIfAbsent(key, k -> new HashMap<>())
            .put(new Coordinate(x, y, z), new BlockKey(block, meta));
        return true;
    }

    /**
     * Drop the whole buffer. Called when the server stops, so a different world opened later in the same game session
     * cannot receive this world's island blocks. Nothing is lost for the world that was just closed: ChromatiCraft
     * resets {@code EndOverhaulManager} on the same event ({@code ChromatiCraft.singlePlayerLogout} ->
     * {@code clearCaches()}), so the next load recomputes the layout from the seed and re-records every not-yet-placed
     * block here.
     */
    public static void clear() {
        BUFFER.clear();
    }

    /** Place and clear any buffered island blocks in the given chunk, without triggering neighbor updates (flag 2). */
    public static void placeChunk(World world, int chunkX, int chunkZ) {
        Map<Coordinate, BlockKey> map = BUFFER.remove(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
        if (map == null) {
            return;
        }
        for (Map.Entry<Coordinate, BlockKey> e : map.entrySet()) {
            Coordinate c = e.getKey();
            BlockKey bk = e.getValue();
            world.setBlock(c.xCoord, c.yCoord, c.zCoord, bk.blockID, bk.metadata, 2);
        }
    }
}
