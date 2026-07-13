package com.tryrodave.chromaextras.mixinconfig;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin plugin for both ChromaExtras configs ({@code mixins.chromaextras.json} and
 * {@code mixins.chromaextras.late.json}). Two jobs:
 *
 * <ol>
 * <li><b>Config gating</b> - each mixin is checked against {@code config/chromaextras-mixins.properties}
 * ({@link MixinToggles}); disabled ones are skipped and logged.</li>
 * <li><b>Application logging</b> - {@link #postApply} prints one {@code [ChromaExtras] Mixin applied: X -> target}
 * line per successfully transformed target class, so a log can be shallow-checked with a single search for
 * "ChromaExtras] Mixin": every enabled mixin should have an "applied" line; a missing line (or a "DISABLED" /
 * mixin-framework "target ... was not found" line) immediately shows what did not run.</li>
 * </ol>
 */
public class ChromaExtrasMixinPlugin implements IMixinConfigPlugin {

    private static String shortName(String mixinClassName) {
        return mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
    }

    @Override
    public void onLoad(String mixinPackage) {
        MixinToggles.LOG.info("Mixin plugin active for package {}", mixinPackage);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = shortName(mixinClassName);
        boolean enabled = MixinToggles.isEnabled(name);
        if (!enabled) {
            MixinToggles.LOG.info(
                "Mixin DISABLED by config ({}=false): {} -> {}",
                MixinToggles.keyFor(name),
                name,
                targetClassName);
        }
        return enabled;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        MixinToggles.LOG.info("Mixin applied: {} -> {}", shortName(mixinClassName), targetClassName);
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }
}
