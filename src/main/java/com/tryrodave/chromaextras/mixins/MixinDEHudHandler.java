package com.tryrodave.chromaextras.mixins;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.brandon3055.draconicevolution.client.handler.HudHandler;

/**
 * Root-cause fix (companion to {@code MixinChromaBookGui}, which defends the Lexicon specifically): Draconic
 * Evolution draws its armor shield/energy HUD every frame from a {@code RenderGameOverlayEvent.Post} handler
 * ({@code HudHandler.drawHUD}), and it ends with {@code GL_BLEND} and {@code GL_ALPHA_TEST} both <em>disabled</em>
 * (see {@code drawArmorHUD}) without restoring them. Minecraft's {@code setupOverlayRendering} only resets the
 * matrices before drawing a {@code GuiScreen}, not the blend/alpha enables, so with any non-pausing screen open the
 * HUD keeps rendering behind it and leaves alpha handling off - which makes GUIs drawn afterwards mishandle
 * transparency (opaque black where they should be transparent, washed-out white where semi-transparent).
 *
 * <p>
 * Restore the standard GUI transparency state at the tail of the handler so every screen drawn afterwards - not just
 * ChromatiCraft's Lexicon - inherits clean state. Explicit enable calls are used rather than
 * {@code glPushAttrib}/{@code glPopAttrib}, whose legacy attrib stack is incompletely emulated under lwjgl3ify and
 * corrupts unrelated state; these are the exact calls vanilla GUI code uses and are always supported.
 *
 * <p>
 * This targets DE directly (DE is a compile-time-only dependency), so it is a normal mixin - not {@code @Pseudo} -
 * and applies reliably when {@code HudHandler} is class-loaded, unlike the earlier pseudo attempt that mixin dropped
 * because DE's client classes are not on the classpath during the early mixin-prepare phase. Injected at
 * {@code TAIL} so it runs only on the {@code ElementType.ALL} pass that actually draws, not the early-out.
 */
@Mixin(value = HudHandler.class, remap = false)
public class MixinDEHudHandler {

    @Inject(method = "drawHUD", at = @At("TAIL"))
    private void chromaextras$restoreGuiTransparencyState(CallbackInfo ci) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
