package com.tryrodave.chromaextras.mixins;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.worldgen.MeteoritePlacer;

/**
 * Compile-safe access to two package-private members of AE2's {@link MeteoritePlacer} that the surface-meteorite
 * rewrite ({@link MixinMeteoriteSpawn}) needs:
 *
 * <ul>
 * <li>{@code spawnMeteorite()} - places the meteorite unconditionally from the placer's settings (AE2 itself uses this
 * for the neighbor-chunk portions). We use it to force placement when {@code spawnMeteoriteCenter()}'s vanilla-only
 * ground validation rejects modded surfaces (ChromatiCraft dye grass, GeoStrata rock, ...).</li>
 * <li>{@code getSettings()} - the placement NBT handed to neighbor-chunk placers so a meteorite spanning a chunk
 * border is completed in adjacent, already-generated chunks.</li>
 * </ul>
 */
@Mixin(value = MeteoritePlacer.class, remap = false)
public interface MeteoritePlacerAccessor {

    @Invoker(value = "spawnMeteorite", remap = false)
    void chromaextras$spawnMeteorite();

    @Invoker(value = "getSettings", remap = false)
    NBTTagCompound chromaextras$getSettings();
}
