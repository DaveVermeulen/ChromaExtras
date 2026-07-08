package com.tryrodave.chromaextras.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cpw.mods.fml.common.FMLLog;

/**
 * AE2 rv3 (GTNH) runs a background "AE2 CSV Export" thread ({@code appeng.services.export.ExportProcess}) the first
 * time the mod list changes. It enumerates every registered item by calling {@code Item.getSubItems} off the main
 * thread. That is not safe for mods whose item enumeration triggers order-dependent static initialization -
 * ChromatiCraft is one: the export thread walks
 * {@code BlockTieredPlant.getSubItems -> TieredPlants.<clinit> -> ProgressStage.<clinit> ->
 * ChromaResearchManager.<clinit> -> ResearchLevel.<clinit>}, and {@code ResearchLevel}'s enum constructor calls
 * {@code ChromaResearchManager.instance.register(...)} while {@code instance} is still {@code null} (we are inside
 * {@code ChromaResearchManager}'s own constructor). That NPE poisons {@code ResearchLevel} with an
 * {@code ExceptionInInitializerError}, so when the main thread later loads {@code ChromaResearch} it dies with
 * {@code NoClassDefFoundError} and the game hard-crashes during INIT -> POSTINIT. Because the export only runs when the
 * mod configuration changed, the crash appears exactly on the first launch after adding/updating/removing any mod.
 *
 * <p>
 * The CSV export is a purely diagnostic feature (it writes item/recipe listings to disk; no gameplay or other mod
 * depends on it), and it is the direct cause of a whole class of off-main-thread initialization races - not just this
 * one ChromatiCraft manifestation. So rather than reorder ChromatiCraft's fragile static-init chain (which would fix
 * only this single symptom), cancel the export process entirely. The "AE2 CSV Export" thread still starts, but does
 * nothing, so no ChromatiCraft class is ever force-initialized off the main thread.
 */
@Mixin(targets = "appeng.services.export.ExportProcess", remap = false)
public class MixinAE2ExportProcess {

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void chromaextras$skipCsvExport(CallbackInfo ci) {
        FMLLog.info(
            "[ChromaExtras] Skipping AE2 CSV export: it enumerates items off the main thread and race-crashes "
                + "ChromatiCraft's progression static-init on the first launch after a mod change.");
        ci.cancel();
    }
}
