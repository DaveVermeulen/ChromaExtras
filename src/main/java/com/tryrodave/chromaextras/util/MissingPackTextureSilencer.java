package com.tryrodave.chromaextras.util;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;

import Reika.DragonAPI.Libraries.Java.ReikaObfuscationHelper;
import Reika.DragonAPI.Libraries.Java.ReikaObfuscationHelper.ReflectiveAccessExceptionHandler;

/**
 * Silences a specific, harmless piece of DragonAPI log spam.
 *
 * <p>
 * When a TESR texture is bound, {@code ReikaTextureHelper.bindTexture} probes every active resource pack so a pack can
 * override ChromatiCraft's textures, calling {@code AbstractResourcePack.getInputStreamByName} on each via reflection
 * ({@code ReikaObfuscationHelper.invoke}). Vanilla's {@code getInputStreamByName} <em>throws</em>
 * {@link FileNotFoundException} when a pack does not contain the requested file (rather than returning null), so any
 * active pack that overrides some ChromatiCraft textures but not this one makes the probe throw. DragonAPI catches it,
 * falls back to the default texture (so rendering is correct), but unconditionally prints the wrapping
 * {@link InvocationTargetException} stack - once per not-overridden texture, and it is not gated by any config option.
 *
 * <p>
 * DragonAPI exposes {@code registerExceptionHandler} precisely so this printing can be suppressed: a handler returning
 * {@code false} silences the stacktrace. Register one that returns {@code false} only when the reflected call failed
 * with a {@link FileNotFoundException} (the "pack doesn't have this file" case) and {@code true} for everything else,
 * so genuine reflective failures still surface. No mixin required - this is DragonAPI's own intended hook.
 */
public final class MissingPackTextureSilencer implements ReflectiveAccessExceptionHandler {

    private MissingPackTextureSilencer() {}

    public static void register() {
        ReikaObfuscationHelper.registerExceptionHandler(new MissingPackTextureSilencer());
    }

    @Override
    public boolean handleException(Exception e) {
        // ReikaObfuscationHelper.invoke hands us the exception thrown by Method.invoke; the real cause is nested.
        Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
        // A missing resource-pack file is expected during the override probe - silence only that.
        return !(cause instanceof FileNotFoundException);
    }
}
