package com.tryrodave.chromaextras.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

/**
 * Persists {@link DeferredStructureGen}'s queues into the world save ({@code data/ChromaExtras_DeferredGen.dat}).
 * Loaded/registered at server start (see {@code ChromaExtras.serverStarted}); the vanilla save cycle then calls
 * {@link #writeToNBT} on every world save, snapshotting whatever is still queued. Without this, every structure
 * deferred but not yet generated at shutdown - the frontier ring around wherever the player logged out - was lost
 * permanently, since those chunks are already generated and populate never fires for them again.
 */
public class DeferredGenSavedData extends WorldSavedData {

    public static final String ID = "ChromaExtras_DeferredGen";

    /** MapStorage instantiates saved data reflectively through this (String) constructor. */
    public DeferredGenSavedData(String id) {
        super(id);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        DeferredStructureGen.readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        DeferredStructureGen.writeToNBT(tag);
    }

    /** The live state is in DeferredStructureGen's static queues, so always re-serialize on a world save. */
    @Override
    public boolean isDirty() {
        return true;
    }
}
