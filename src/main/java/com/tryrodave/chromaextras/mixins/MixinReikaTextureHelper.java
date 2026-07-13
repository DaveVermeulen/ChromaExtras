package com.tryrodave.chromaextras.mixins;

import java.util.ArrayList;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.DragonAPI.Libraries.IO.ReikaTextureHelper;

/**
 * Root-cause guard for DragonAPI crashing on resource packs that are not the vanilla {@link AbstractResourcePack}.
 *
 * <p>
 * Every place DragonAPI walks resource packs ({@code bindTexture}, {@code initializeColorOverrides}) gets its list from
 * {@code getCurrentResourcePacks()} and then blindly casts each entry to {@code AbstractResourcePack} - e.g.
 * {@code ReikaTextureHelper.initializeColorOverrides} line 390, reached from a ChromatiCraft dye-leaf render tick. Mods
 * like Waystones register an {@link IResourcePack} ({@code WaystonesAlternateResourcePack}) that is not an
 * {@code AbstractResourcePack}, so those casts throw {@link ClassCastException} and crash the game (seen both while
 * rendering a Data Node and, fatally, right after a world loads).
 *
 * <p>
 * Filtering the list at its single source drops those incompatible packs before any cast sees them, fixing all current
 * and future cast sites at once. This is safe because DragonAPI only ever reads its own files ({@code dyecolor.txt},
 * texture overrides) from these packs and always treats them as {@code AbstractResourcePack}; a pack that isn't one was
 * never usable here and is simply skipped.
 */
@Mixin(value = ReikaTextureHelper.class, remap = false)
public class MixinReikaTextureHelper {

    @Inject(method = "getCurrentResourcePacks", at = @At("RETURN"))
    private static void chromaextras$dropNonAbstractResourcePacks(
        CallbackInfoReturnable<ArrayList<IResourcePack>> cir) {
        ArrayList<IResourcePack> packs = cir.getReturnValue();
        if (packs != null) {
            packs.removeIf(pack -> !(pack instanceof AbstractResourcePack));
        }
    }
}
