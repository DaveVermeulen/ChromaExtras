package com.tryrodave.chromaextras;

import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;

import com.tryrodave.chromaextras.util.DeferredGenSavedData;
import com.tryrodave.chromaextras.util.DeferredStructureGen;
import com.tryrodave.chromaextras.util.EndIslandDeferral;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

@Mod(modid = ChromaExtras.MODID, version = Tags.VERSION, name = "ChromaExtras", acceptedMinecraftVersions = "[1.7.10]")
public class ChromaExtras {

    public static final String MODID = "chromaextras";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Drives deferred structure generation (see DeferredStructureGen / MixinDungeonGenerator).
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /**
     * Load (or create) the persisted deferred-generation queue for this save. The overworld's mapStorage is the
     * save-global storage, so one entry covers every dimension's queue. Once registered, the vanilla save cycle
     * snapshots the live queues on every world save.
     */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return;
        }
        MapStorage storage = overworld.mapStorage;
        DeferredGenSavedData data = (DeferredGenSavedData) storage
            .loadData(DeferredGenSavedData.class, DeferredGenSavedData.ID);
        if (data == null) {
            storage.setData(DeferredGenSavedData.ID, new DeferredGenSavedData(DeferredGenSavedData.ID));
        }
    }

    /**
     * The final world save has already run (it snapshotted the deferred queues), so drop this world's in-memory state.
     * Without this, opening a different world in the same game session would inherit - and generate - the previous
     * world's deferred structures and buffered End island blocks at the same coordinates.
     */
    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        DeferredStructureGen.clearAll();
        EndIslandDeferral.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DeferredStructureGen.tick();
        }
    }
}
