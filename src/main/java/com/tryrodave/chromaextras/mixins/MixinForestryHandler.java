package com.tryrodave.chromaextras.mixins;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * DragonAPI's {@code ForestryHandler} reads Forestry's item/block registry fields reflectively. In its constructor it
 * does {@code clazz.getDeclaredField(name)} followed by {@code field.get(registry)} <em>without</em> calling
 * {@code setAccessible(true)} first (unlike its own {@code getRegistryObject()} helper, which does). That worked when
 * every registry field was {@code public}, but the GTNH Forestry fork (4.11.31) made some of them {@code private} -
 * e.g. {@code forestry.core.items.ItemRegistryCore.ash} - so the read throws
 * {@code IllegalAccessException: ... cannot access a member ... with modifiers "private final"} and that item comes
 * back null (logged as "DRAGONAPI ERROR: Illegal access exception for reading Forestry!").
 *
 * <p>
 * Redirect the reflective {@code Field.get} calls in the constructor to force the field accessible first. This is the
 * same thing DragonAPI already does everywhere else it reflects, so it just makes the two registry-read loops
 * consistent - and it is robust against any other Forestry field that may have been made private.
 */
@Mixin(targets = "Reika.DragonAPI.ModInteract.ItemHandlers.ForestryHandler", remap = false)
public class MixinForestryHandler {

    @Redirect(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Ljava/lang/reflect/Field;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object chromaextras$getAccessible(Field field, Object target) throws IllegalAccessException {
        field.setAccessible(true);
        return field.get(target);
    }
}
