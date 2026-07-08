package com.tryrodave.chromaextras.mixins;

import java.util.Comparator;
import java.util.PriorityQueue;

import net.minecraft.client.renderer.Tessellator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla {@code Tessellator.getVertexState} (func_147564_a), when building the translucent render layer, sorts the
 * quads with {@code new PriorityQueue(quadCount, comparator)}. {@link PriorityQueue} throws
 * {@code IllegalArgumentException} for an initial capacity {@code < 1}, so a render layer that is flagged for
 * sorting but currently holds zero quads crashes the client. This is a long-standing 1.7.10 rendering bug (nothing
 * to do with worldgen); it tends to surface in translucent-heavy areas such as the glowing-cliffs biome.
 *
 * <p>
 * Clamp the initial capacity to at least 1. An empty queue of capacity 1 sorts nothing - behavior is identical to
 * the non-crashing case - so this only removes the crash.
 */
@Mixin(Tessellator.class)
public class MixinTessellator {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Redirect(
        method = "getVertexState",
        at = @At(value = "NEW", target = "(ILjava/util/Comparator;)Ljava/util/PriorityQueue;"))
    private PriorityQueue chromaextras$guardSortCapacity(int capacity, Comparator comparator) {
        return new PriorityQueue(Math.max(1, capacity), comparator);
    }
}
