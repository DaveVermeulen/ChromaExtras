package com.tryrodave.chromaextras.compat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.tryrodave.chromaextras.ChromaExtras;

import Reika.ChromatiCraft.API.ChromatiAPI;
import Reika.ChromatiCraft.API.CrystalElementAccessor;
import Reika.ChromatiCraft.API.CrystalElementAccessor.CrystalElementProxy;
import Reika.ChromatiCraft.Auxiliary.ChromaStacks;
import Reika.ChromatiCraft.Registry.ChromaItems;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Registers the Apiary Goggles' casting recipe with ChromatiCraft's casting system (via {@link ChromatiAPI}, the
 * supported third-party recipe entry point - must be called during postInit).
 *
 * <p>
 * <b>Temple tier</b> deliberately: that is the first tier requiring the casting room (the level-one structure built
 * around the table) plus runes, which locks the goggles behind real ChromatiCraft progression without pushing them to
 * the endgame multiblock/pylon tiers.
 *
 * <p>
 * The shape nods to Create's Engineer's Goggles (gold frame + glass lenses) with ChromatiCraft and bee flavour:
 *
 * <pre>
 *   [Argia shard] [gold ingot    ] [Argia shard]   - the attuned headband
 *   [glass pane ] [leather helmet] [glass pane ]   - lenses on the strap
 *   [           ] [honey drop    ] [           ]   - bee attunement
 * </pre>
 *
 * plus two runes in the casting room: Light Gray (Argia, the goggles' vision element) at (0,-1,-3) and Yellow (bees)
 * at (0,-1,3) - the same proven rune positions ChromatiCraft's own Lumen Lamp recipe uses. Gold and panes accept ore
 * dictionary equivalents. The honey drop is looked up from Forestry at runtime; if Forestry were ever absent, a
 * yellow crystal shard stands in so the recipe still exists.
 */
public final class ChromaCastingRecipes {

    private ChromaCastingRecipes() {}

    public static void register() {
        if (ChromatiAPI.getAPI() == null) {
            FMLCommonHandler.instance()
                .getFMLLogger()
                .warn("ChromaExtras: ChromatiCraft casting API unavailable; Apiary Goggles recipe not registered");
            return;
        }

        // CrystalElement order: LIGHTGRAY = meta 7, YELLOW = meta 11 (see ChromaStacks)
        ItemStack argiaShard = ChromaItems.SHARD.getStackOfMetadata(7);
        Item honeyDrop = GameRegistry.findItem("Forestry", "honeyDrop");
        ItemStack beeAttunement = honeyDrop != null ? new ItemStack(honeyDrop)
            : ChromaItems.SHARD.getStackOfMetadata(11);

        IRecipe recipe = new ShapedOreRecipe(
            new ItemStack(ChromaExtras.hiveGoggles),
            "SGS",
            "PHP",
            " D ",
            'S',
            argiaShard,
            'G',
            "ingotGold",
            'P',
            "paneGlass",
            'H',
            new ItemStack(Items.leather_helmet),
            'D',
            beeAttunement);

        // Rune positions form ONE global map shared by every casting recipe: a position may only ever hold a single
        // colour, and registering a different colour there is a hard RegistrationException (crash at postInit).
        // These two positions already hold exactly the colours we need in ChromatiCraft's own layout - LIGHTGRAY at
        // (2,-1,1) and YELLOW at (-1,-1,2), read from the rune-conflict map CC prints - so we share them instead of
        // claiming fresh spots.
        Map<List<Integer>, CrystalElementProxy> runes = new HashMap<>();
        runes.put(Arrays.asList(2, -1, 1), CrystalElementAccessor.getByEnum("LIGHTGRAY"));
        runes.put(Arrays.asList(-1, -1, 2), CrystalElementAccessor.getByEnum("YELLOW"));

        ChromatiAPI.getAPI()
            .recipes()
            .addTempleCastingRecipe(recipe, runes);

        registerVaultRecipes();
    }

    /**
     * The two vault recipes, both made in the ChromatiCraft casting room (item stands around the table).
     *
     * <p>
     * <b>Void Vault</b> - MultiBlock tier (5x5 stands, no pylon energy): an Ender Chest at the centre, a Nether Star
     * as its base, ringed by 3 Draconium Ingots and Spatial Rifting Powder ({@code ChromaStacks.spaceDust}).
     *
     * <p>
     * <b>Death Vault</b> - Pylon tier (the upgrade, so it also demands crystal energy): the Void Vault itself at the
     * centre, the 3 Draconium Ingots promoted to Awakened Draconium, plus Wither Skeleton skulls and more Spatial
     * Rifting Powder for the "any death" theme, and a charge of Black + Light Blue lumen energy.
     */
    private static void registerVaultRecipes() {
        ItemStack draconium = firstOre("ingotDraconium");
        ItemStack awakened = firstOre("ingotDraconiumAwakened");
        ItemStack spaceDust = ChromaStacks.spaceDust.copy();
        ItemStack netherStar = new ItemStack(Items.nether_star);
        ItemStack witherSkull = new ItemStack(Items.skull, 1, 1);

        // --- Void Vault: MultiBlock tier ---
        Map<List<Integer>, ItemStack> voidItems = new HashMap<>();
        voidItems.put(Arrays.asList(0, 2), netherStar.copy()); // the star, the vault's core, at the front
        if (draconium != null) {
            voidItems.put(Arrays.asList(-2, 0), draconium.copy());
            voidItems.put(Arrays.asList(2, 0), draconium.copy());
            voidItems.put(Arrays.asList(0, -2), draconium.copy());
        }
        voidItems.put(Arrays.asList(-2, 2), spaceDust.copy());
        voidItems.put(Arrays.asList(2, 2), spaceDust.copy());

        ChromatiAPI.getAPI()
            .recipes()
            .addMultiBlockCastingRecipe(
                new ItemStack(ChromaExtras.voidVault),
                new ItemStack(net.minecraft.init.Blocks.ender_chest),
                null,
                voidItems);

        // --- Death Vault: Pylon tier (upgrade of the Void Vault) ---
        Map<List<Integer>, ItemStack> deathItems = new HashMap<>();
        deathItems.put(Arrays.asList(0, 2), netherStar.copy());
        if (awakened != null) {
            deathItems.put(Arrays.asList(-2, 0), awakened.copy());
            deathItems.put(Arrays.asList(2, 0), awakened.copy());
            deathItems.put(Arrays.asList(0, -2), awakened.copy());
        }
        deathItems.put(Arrays.asList(-2, -2), witherSkull.copy());
        deathItems.put(Arrays.asList(2, -2), witherSkull.copy());
        deathItems.put(Arrays.asList(-2, 2), spaceDust.copy());
        deathItems.put(Arrays.asList(2, 2), spaceDust.copy());

        Map<CrystalElementProxy, Integer> energy = new HashMap<>();
        energy.put(CrystalElementAccessor.getByEnum("BLACK"), 60_000); // void
        energy.put(CrystalElementAccessor.getByEnum("LIGHTBLUE"), 40_000); // rifting

        ChromatiAPI.getAPI()
            .recipes()
            .addPylonCastingRecipe(
                new ItemStack(ChromaExtras.deathVault),
                new ItemStack(ChromaExtras.voidVault),
                null,
                deathItems,
                energy);
    }

    /** First ore-dictionary stack for a name (size 1), or null if the mod providing it is absent. */
    private static ItemStack firstOre(String name) {
        java.util.List<ItemStack> ores = net.minecraftforge.oredict.OreDictionary.getOres(name);
        if (ores.isEmpty()) {
            FMLCommonHandler.instance()
                .getFMLLogger()
                .warn("ChromaExtras: ore '" + name + "' not found; vault recipe will omit it");
            return null;
        }
        ItemStack copy = ores.get(0)
            .copy();
        copy.stackSize = 1;
        if (copy.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) {
            copy.setItemDamage(0);
        }
        return copy;
    }
}
