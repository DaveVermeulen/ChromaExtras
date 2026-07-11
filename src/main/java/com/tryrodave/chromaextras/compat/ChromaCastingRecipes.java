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
    }
}
