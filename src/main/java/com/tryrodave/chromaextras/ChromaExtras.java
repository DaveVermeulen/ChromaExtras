package com.tryrodave.chromaextras;

import com.tryrodave.chromaextras.util.DeferredStructureGen;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DeferredStructureGen.tick();
        }
    }
}
