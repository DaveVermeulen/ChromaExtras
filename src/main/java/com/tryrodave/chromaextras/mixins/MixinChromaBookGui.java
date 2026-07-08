package com.tryrodave.chromaextras.mixins;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import Reika.ChromatiCraft.Base.ChromaBookGui;

/**
 * The Chromatic Lexicon (and the other ChromatiCraft book screens) draw their background in
 * {@code ChromaBookGui.drawScreen}: it resets the colour to white and binds the background texture, but never
 * establishes blend/alpha state - it just inherits whatever OpenGL state happens to be current. That is normally the
 * clean GUI state, but Draconic Evolution's armor HUD renders every frame from a {@code RenderGameOverlayEvent} and
 * leaves {@code GL_BLEND} and {@code GL_ALPHA_TEST} disabled; since the Lexicon is a non-pausing screen, that HUD
 * keeps drawing behind it and the leaked state carries into the Lexicon's own draw. With alpha handling off the
 * parchment background renders wrong - washed-out white where it is semi-transparent and opaque black where it should
 * be fully transparent.
 *
 * <p>
 * Fix it at the consumer instead of chasing every mod that might leave dirty state (and DE's client handler cannot be
 * reliably targeted by a mixin anyway - it is not on the classpath when mixins are prepared). Establish the standard
 * GUI state at the head of {@code drawScreen} so the background always draws correctly regardless of what rendered
 * before it: opaque texturing enabled, alpha test and standard alpha blending on, colour reset to white.
 *
 * <p>
 * {@code drawScreen} overrides the vanilla {@code GuiScreen} method, so its runtime name is SRG; both the MCP and SRG
 * names are given with {@code remap = false} so one matches in the dev environment and the other at runtime.
 */
@Mixin(value = ChromaBookGui.class, remap = false)
public class MixinChromaBookGui {

    @Inject(method = { "drawScreen", "func_73863_a" }, at = @At("HEAD"))
    private void chromaextras$establishGuiState(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
