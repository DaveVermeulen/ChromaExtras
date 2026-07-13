package com.tryrodave.chromaextras.compat;

import Reika.DragonAPI.Instantiable.Worldgen.LootController;
import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Works around a latent DragonAPI bug that spams the log with
 * {@code Tried to fire an event for chest loot from an unrecognized structure
 * forestry.apiculture.worldgen.ComponentVillageBeeHouse!} (and the same for Thaumcraft towers, Tinkers' workshops,
 * Witchery/Mystcraft structures) every time such a structure generates a chest.
 *
 * <p>
 * DragonAPI's {@code LootController.ModdedStructures} enum <em>does</em> map those structure components to their loot
 * tables, but each constant only registers itself into {@code LootController.locationsByObject} inside its constructor
 * -
 * which never runs, because the enum is never class-loaded. The single line that would have forced the load
 * ({@code public static final ModdedStructures[] list = values();}) is commented out in DragonAPI, and nothing else
 * references the enum. So the mappings are never installed and {@code ChestLootEvent.fire} falls through to the error
 * branch for every modded structure chest.
 *
 * <p>
 * Simply touching the enum ({@code values()}) triggers its {@code <clinit>}, runs every constant's constructor, and
 * populates the map - silencing the errors and restoring the modded-structure loot events Reika intended. Must run
 * after
 * the source mods are loaded (postInit), so their structure classes resolve. Fully guarded: if DragonAPI ever changes
 * this shape, we log and move on rather than break loading.
 */
public final class DragonApiLootFix {

    private DragonApiLootFix() {}

    public static void apply() {
        try {
            // Referencing values() class-loads the enum, so every constant's constructor registers its
            // structure -> loot-table mapping in DragonAPI's LootController. That is the whole fix.
            int count = LootController.ModdedStructures.values().length;
            FMLCommonHandler.instance()
                .getFMLLogger()
                .info(
                    "ChromaExtras: initialised DragonAPI LootController.ModdedStructures (" + count
                        + " entries) to register modded-structure chest loot and silence the "
                        + "'unrecognized structure' spam (e.g. Forestry bee houses).");
        } catch (Throwable t) {
            // Never fatal - a missing/renamed class in a future DragonAPI just means the spam stays.
            FMLCommonHandler.instance()
                .getFMLLogger()
                .warn("ChromaExtras: could not initialise DragonAPI LootController.ModdedStructures: " + t);
        }
    }
}
