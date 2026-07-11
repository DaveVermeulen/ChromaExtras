package com.tryrodave.chromaextras;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.EnumHelper;

import com.tryrodave.chromaextras.client.HiveVisionHandler;
import com.tryrodave.chromaextras.command.CommandTeleportPlus;
import com.tryrodave.chromaextras.compat.ChromaCastingRecipes;
import com.tryrodave.chromaextras.items.ItemHiveGoggles;
import com.tryrodave.chromaextras.util.DeferredGenSavedData;
import com.tryrodave.chromaextras.util.DeferredStructureGen;
import com.tryrodave.chromaextras.util.EndIslandDeferral;
import com.tryrodave.chromaextras.util.MissingPackTextureSilencer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(modid = ChromaExtras.MODID, version = Tags.VERSION, name = "ChromaExtras", acceptedMinecraftVersions = "[1.7.10]")
public class ChromaExtras {

    public static final String MODID = "chromaextras";

    /** Argia's Apiary Goggles - reveal nearby hives through walls, powered by ChromatiCraft energy. */
    public static ItemHiveGoggles hiveGoggles;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Unbreakable utility helmet (item damage is unused - charge lives in NBT), so durability/reduction values are
        // nominal. armorType 0 = helmet.
        ArmorMaterial material = EnumHelper
            .addArmorMaterial("chromaextras_hivegoggles", 15, new int[] { 2, 0, 0, 0 }, 10);
        hiveGoggles = new ItemHiveGoggles(material);
        hiveGoggles.setUnlocalizedName("chromaextras.hive_goggles");
        hiveGoggles.setCreativeTab(CreativeTabs.tabTools);
        GameRegistry.registerItem(hiveGoggles, "hive_goggles");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Drives deferred structure generation (see DeferredStructureGen / MixinDungeonGenerator).
        FMLCommonHandler.instance()
            .bus()
            .register(this);

        // Client-only: stop DragonAPI printing an InvocationTargetException every time an active resource pack lacks
        // a ChromatiCraft TESR texture it probes (see MissingPackTextureSilencer); and drive the goggles' hive-vision
        // particles. ClientTickEvent is an FML-bus event, so the handler goes on the FML bus, not the Forge one.
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            MissingPackTextureSilencer.register();
            FMLCommonHandler.instance()
                .bus()
                .register(new HiveVisionHandler());
        }
    }

    /** Casting recipes must be registered in postInit (per ChromatiCraft's CastingAPI contract). */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ChromaCastingRecipes.register();
    }

    /** /tpp - modern-style teleport with yaw/pitch, for lining up panorama camera shots. OP level 2 like /tp. */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandTeleportPlus());
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
