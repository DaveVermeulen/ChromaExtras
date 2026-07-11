package com.tryrodave.chromaextras;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.EnumHelper;

import com.tryrodave.chromaextras.blocks.BlockDeathVault;
import com.tryrodave.chromaextras.blocks.BlockVoidVault;
import com.tryrodave.chromaextras.blocks.ItemBlockVoidVault;
import com.tryrodave.chromaextras.blocks.TileEntityDeathVault;
import com.tryrodave.chromaextras.blocks.TileEntityVoidVault;
import com.tryrodave.chromaextras.client.HiveVisionHandler;
import com.tryrodave.chromaextras.client.RenderVoidVault;
import com.tryrodave.chromaextras.command.CommandTeleportPlus;
import com.tryrodave.chromaextras.compat.ChromaCastingRecipes;
import com.tryrodave.chromaextras.items.ItemHiveGoggles;
import com.tryrodave.chromaextras.util.DeferredGenSavedData;
import com.tryrodave.chromaextras.util.DeferredStructureGen;
import com.tryrodave.chromaextras.util.EndIslandDeferral;
import com.tryrodave.chromaextras.util.MissingPackTextureSilencer;
import com.tryrodave.chromaextras.util.VoidDeathHandler;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mod(modid = ChromaExtras.MODID, version = Tags.VERSION, name = "ChromaExtras", acceptedMinecraftVersions = "[1.7.10]")
public class ChromaExtras {

    public static final String MODID = "chromaextras";

    /** Argia's Apiary Goggles - reveal nearby hives through walls, powered by ChromatiCraft energy. */
    public static ItemHiveGoggles hiveGoggles;

    /** The Void Vault - a player-bound chest that catches its owner's drops on a void death. */
    public static BlockVoidVault voidVault;

    /** The Death Vault - the Void Vault's upgrade; catches drops on ANY death. One vault per player, either type. */
    public static BlockDeathVault deathVault;

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

        voidVault = new BlockVoidVault();
        voidVault.setBlockName("chromaextras.void_vault");
        voidVault.setCreativeTab(CreativeTabs.tabDecorations);
        GameRegistry.registerBlock(voidVault, ItemBlockVoidVault.class, "void_vault");
        GameRegistry.registerTileEntity(TileEntityVoidVault.class, "chromaextras:void_vault");

        deathVault = new BlockDeathVault();
        deathVault.setBlockName("chromaextras.death_vault");
        deathVault.setCreativeTab(CreativeTabs.tabDecorations);
        GameRegistry.registerBlock(deathVault, ItemBlockVoidVault.class, "death_vault");
        GameRegistry.registerTileEntity(TileEntityDeathVault.class, "chromaextras:death_vault");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Drives deferred structure generation (see DeferredStructureGen / MixinDungeonGenerator).
        FMLCommonHandler.instance()
            .bus()
            .register(this);

        // Rescues drops into the owner's Void Vault on void deaths (PlayerDropsEvent is a Forge-bus event).
        MinecraftForge.EVENT_BUS.register(new VoidDeathHandler());

        // WAILA owner tooltip for the Void Vault; harmless no-op if Waila is absent.
        FMLInterModComms
            .sendMessage("Waila", "register", "com.tryrodave.chromaextras.compat.WailaVoidVault.callbackRegister");

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
            this.registerVoidVaultRenderers();
        }
    }

    /** Casting recipes must be registered in postInit (per ChromatiCraft's CastingAPI contract). */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ChromaCastingRecipes.register();
    }

    /** Client-only bodies live in a separate method so the classes never load on a dedicated server. */
    @SideOnly(Side.CLIENT)
    private void registerVoidVaultRenderers() {
        // one TESR registration covers TileEntityDeathVault too (subclass); it picks the texture per tile type
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityVoidVault.class, new RenderVoidVault());
        MinecraftForgeClient.registerItemRenderer(
            Item.getItemFromBlock(voidVault),
            new RenderVoidVault.ItemRender(RenderVoidVault.TEXTURE_VOID));
        MinecraftForgeClient.registerItemRenderer(
            Item.getItemFromBlock(deathVault),
            new RenderVoidVault.ItemRender(RenderVoidVault.TEXTURE_DEATH));
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
