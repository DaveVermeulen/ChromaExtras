package com.tryrodave.chromaextras.mixins;

import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import Reika.DragonAPI.Libraries.Rendering.ReikaRenderHelper;

/**
 * {@code ReikaRenderHelper.setRenderTarget} binds a framebuffer and then calls {@code glGetError()}; if it is
 * non-zero it reports "FB #x was unrecognized ... This should be impossible! Driver issues?". But
 * {@code glGetError()} returns the first error accumulated since it was last called, not necessarily one caused by
 * the bind. Under lwjgl3ify's newer GL profile, unrelated legacy GL calls elsewhere leave a stale
 * {@code GL_INVALID_OPERATION (1282)} in the queue, which this method then misattributes to the framebuffer bind -
 * even though the framebuffer status comes back {@code GL_FRAMEBUFFER_COMPLETE (36053)}.
 *
 * <p>
 * Drain any pending GL errors before the method runs, so its own {@code glGetError()} check only sees errors
 * actually produced by the bind. This removes the spurious error spam without hiding genuine framebuffer failures.
 */
@Mixin(value = ReikaRenderHelper.class, remap = false)
public class MixinReikaRenderHelper {

    @Inject(method = "setRenderTarget", at = @At("HEAD"))
    private static void chromaextras$drainStaleGlErrors(Framebuffer fb, CallbackInfo ci) {
        for (int i = 0; i < 64 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
            // discard errors left over from earlier, unrelated GL calls
        }
    }
}
