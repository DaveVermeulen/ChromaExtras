package com.tryrodave.chromaextras;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/**
 * Registers ChromaExtras' <b>late</b> mixin config with GTNHMixins/UniMixins. Late mixins are applied after mod
 * discovery, so they can target classes from regular (non-coremod) mods like Draconic Evolution - which are invisible
 * to the early tweaker-phase config ({@code mixins.chromaextras.json}) and would be silently dropped there.
 */
@LateMixin
public class ChromaExtrasLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.chromaextras.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("DraconicEvolution")) {
            mixins.add("MixinDraconicArmorFields");
        }
        return mixins;
    }
}
