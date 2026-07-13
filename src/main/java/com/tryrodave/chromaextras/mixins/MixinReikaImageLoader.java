package com.tryrodave.chromaextras.mixins;

import java.awt.image.BufferedImage;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import Reika.DragonAPI.IO.ReikaImageLoader;

/**
 * Stops a hard crash when DragonAPI loads a texture through {@code ReikaTextureHelper.bindTexture} (e.g. rendering a
 * ChromatiCraft Data Node) while a mod has registered a resource pack that does not extend the vanilla
 * {@link AbstractResourcePack}.
 *
 * <p>
 * {@code bindTexture} walks <em>every</em> loaded resource pack and passes each to
 * {@code ReikaImageLoader.getImageFromResourcePack}, which blindly does {@code (AbstractResourcePack) res}. Waystones
 * ships {@code net.blay09.mods.waystones.client.resource.WaystonesAlternateResourcePack}, an {@link IResourcePack} that
 * is <em>not</em> an {@code AbstractResourcePack}, so the cast throws {@link ClassCastException} and takes the game
 * down
 * (crash: "Rendering Block Entity" -> {@code RenderDataNode.renderTower}).
 *
 * <p>
 * The calling loop already treats a {@code null} return as "this pack has no such image" and moves on to the next pack,
 * so we short-circuit non-{@code AbstractResourcePack} packs to {@code null}. The texture still resolves from
 * ChromatiCraft's own (vanilla-type) pack; the incompatible pack is simply skipped instead of crashing.
 */
@Mixin(value = ReikaImageLoader.class, remap = false)
public class MixinReikaImageLoader {

    @Inject(method = "getImageFromResourcePack", at = @At("HEAD"), cancellable = true)
    private static void chromaextras$skipNonAbstractResourcePacks(String path, IResourcePack res,
        ReikaImageLoader.ImageEditor editor, CallbackInfoReturnable<BufferedImage> cir) {
        if (!(res instanceof AbstractResourcePack)) {
            cir.setReturnValue(null);
        }
    }
}
