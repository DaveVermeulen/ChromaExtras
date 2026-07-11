package com.tryrodave.chromaextras.latemixins;

import java.util.List;

import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.brandon3055.draconicevolution.common.lib.References;
import com.brandon3055.draconicevolution.common.utils.ItemConfigField;

/**
 * Adds an "Apiarist's Vision" toggle to the Draconic helmet's tool-config feature list, alongside its Night Vision /
 * Apiarist's Protection / Naturalist's Vision entries. The toggle is a standard DE {@link ItemConfigField} boolean, so
 * DE's own config GUI renders it, its packets save it, and it persists per config profile in the stack NBT
 * ({@code ConfigProfiles[profile].ApiaristVision}). ChromaExtras' {@code HiveVisionHandler} reads that NBT flag and,
 * when set, grants the wearer the same see-through hive reveal as the Apiary Goggles - no energy cost, matching the
 * helmet's other free passives like Night Vision.
 *
 * <p>
 * This mixin ships in the <b>late</b> config ({@code mixins.chromaextras.late.json}, see
 * {@code ChromaExtrasLateMixins}): Draconic Evolution is a regular mod, not a coremod, so its classes are invisible
 * during the early mixin phase and an early mixin against them is silently dropped with "@Mixin target ... was not
 * found" (observed with a previous DE mixin). Late mixins are applied by GTNHMixins/UniMixins after mod discovery,
 * when DE's classes are on the classpath.
 */
@Mixin(targets = "com.brandon3055.draconicevolution.common.items.armor.DraconicArmor", remap = false)
public class MixinDraconicArmorFields {

    @Inject(method = "getFields", at = @At("RETURN"))
    private void chromaextras$addApiaristVision(ItemStack stack, int slot,
        CallbackInfoReturnable<List<ItemConfigField>> cir) {
        // armorType 0 = helmet; the same DraconicArmor class backs all four pieces
        if (((ItemArmor) (Object) this).armorType == 0) {
            cir.getReturnValue()
                .add(new ItemConfigField(References.BOOLEAN_ID, slot, "ApiaristVision").readFromItem(stack, false));
        }
    }
}
