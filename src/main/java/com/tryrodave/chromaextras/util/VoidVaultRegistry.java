package com.tryrodave.chromaextras.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;

/**
 * Per-player Void Vault binding, persisted in the world save ({@code data/ChromaExtras_VoidVaults.dat}). Each player
 * has at most one bound vault - placing a new one moves the binding; breaking the bound one clears it. Looked up by
 * {@code VoidDeathHandler} when its owner dies to the void.
 */
public class VoidVaultRegistry extends WorldSavedData {

    public static final String ID = "ChromaExtras_VoidVaults";

    /** Vault that only catches void deaths. */
    public static final int TYPE_VOID = 0;
    /** Vault that catches every death (the Death Vault upgrade). */
    public static final int TYPE_DEATH = 1;

    /** A bound vault position. */
    public static final class VaultLocation {

        public final int dimension;
        public final int x;
        public final int y;
        public final int z;
        /** {@link #TYPE_VOID} or {@link #TYPE_DEATH}; stored here so death handling can skip chunk loads. */
        public final int type;

        private VaultLocation(int dimension, int x, int y, int z, int type) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
        }
    }

    private final Map<UUID, VaultLocation> bindings = new HashMap<>();

    /** MapStorage instantiates saved data reflectively through this (String) constructor. */
    public VoidVaultRegistry(String id) {
        super(id);
    }

    /**
     * The registry for the currently running save, from the overworld's save-global mapStorage. Server side only;
     * returns null when no server world exists (nothing to bind against then anyway).
     */
    public static VoidVaultRegistry get() {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return null;
        }
        MapStorage storage = overworld.mapStorage;
        VoidVaultRegistry data = (VoidVaultRegistry) storage.loadData(VoidVaultRegistry.class, ID);
        if (data == null) {
            data = new VoidVaultRegistry(ID);
            storage.setData(ID, data);
        }
        return data;
    }

    public void bind(UUID player, int dimension, int x, int y, int z, int type) {
        bindings.put(player, new VaultLocation(dimension, x, y, z, type));
        this.markDirty();
    }

    /**
     * Clears the binding only when it points at exactly this vault (breaking an old, unbound vault changes nothing).
     */
    public void unbindIfAt(UUID player, int dimension, int x, int y, int z) {
        VaultLocation loc = bindings.get(player);
        if (loc != null && loc.dimension == dimension && loc.x == x && loc.y == y && loc.z == z) {
            bindings.remove(player);
            this.markDirty();
        }
    }

    public VaultLocation getBinding(UUID player) {
        return bindings.get(player);
    }

    // --- persistence ------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        bindings.clear();
        NBTTagList list = tag.getTagList("vaults", 10 /* compound */);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            UUID player = new UUID(e.getLong("idMost"), e.getLong("idLeast"));
            bindings.put(
                player,
                new VaultLocation(
                    e.getInteger("dim"),
                    e.getInteger("x"),
                    e.getInteger("y"),
                    e.getInteger("z"),
                    e.getInteger("type"))); // absent (pre-Death-Vault saves) = 0 = TYPE_VOID
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, VaultLocation> entry : bindings.entrySet()) {
            NBTTagCompound e = new NBTTagCompound();
            e.setLong(
                "idMost",
                entry.getKey()
                    .getMostSignificantBits());
            e.setLong(
                "idLeast",
                entry.getKey()
                    .getLeastSignificantBits());
            VaultLocation loc = entry.getValue();
            e.setInteger("dim", loc.dimension);
            e.setInteger("x", loc.x);
            e.setInteger("y", loc.y);
            e.setInteger("z", loc.z);
            e.setInteger("type", loc.type);
            list.appendTag(e);
        }
        tag.setTag("vaults", list);
    }
}
