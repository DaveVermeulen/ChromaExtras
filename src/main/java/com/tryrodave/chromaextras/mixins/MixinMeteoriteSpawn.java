package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.core.worlddata.WorldData;
import appeng.worldgen.MeteoritePlacer;
import appeng.worldgen.meteorite.ChunkOnly;

/**
 * Full rewrite of AE2's meteorite landing scan so meteorites are <b>never buried underground without a crater</b>.
 *
 * <p>
 * Stock behaviour ({@code MeteoriteSpawn.tryMeteorite}) starts at a fixed depth and steps down in 15-block increments,
 * asking {@code spawnMeteoriteCenter()} at each level. That validation only accepts a <b>hardcoded vanilla</b> ground
 * list (stone/grass/dirt/sand/...). In modded terrain - ChromatiCraft Rainbow Forest dye grass, GeoStrata rock,
 * cave-riddled Immersive Cavegen underground - the true surface fails the check, so the scan keeps descending until it
 * finally hits deep vanilla stone and entombs the meteorite there. And even near the surface, the crater/decay steps
 * are
 * gated on a sky-visibility count that forest canopy defeats, which is why craters were rare.
 *
 * <p>
 * Replacement logic (this injection cancels the whole method):
 * <ol>
 * <li>Find the <b>actual surface</b> at the meteorite's (x,z): scan down from the heightmap, skipping foliage, logs,
 * plants, vines, cacti and snow layers, to the first real ground block. No fixed depths, no coarse steps.</li>
 * <li>Liquids or sky-less dimensions abort (no meteorite is always better than a buried one; matches stock behaviour
 * which also rejected water/nether).</li>
 * <li>Try AE2's own {@code spawnMeteoriteCenter()} there first, so on plain vanilla terrain nothing changes at all.
 * When its vanilla-only validation rejects modded ground, <b>force placement</b> at the surface via the
 * package-private {@code spawnMeteorite()} (the same unconditional path AE2 uses for neighbor-chunk portions).</li>
 * <li>Replicate the stock neighbor-chunk pass so meteorites spanning chunk borders are completed in adjacent
 * already-generated chunks.</li>
 * </ol>
 *
 * The companion {@link MixinMeteoritePlacer} removes the sky-visibility gate on the crater/decay so every placed
 * meteorite gets its crater. Together: same look, same sizes, same loot, same spacing - but always on the surface,
 * always with a crater. Works with {@link MeteoritePlacerAccessor}; toggle via {@code ae2-surface-meteorites} in
 * {@code config/chromaextras-mixins.properties}.
 */
@Mixin(targets = "appeng.worldgen.MeteoriteWorldGen$MeteoriteSpawn", remap = false)
public class MixinMeteoriteSpawn {

    @Shadow
    @Final
    private int x;

    @Shadow
    @Final
    private int z;

    @Shadow
    @Final
    private long seed;

    @Inject(method = "tryMeteorite", at = @At("HEAD"), cancellable = true)
    private void chromaextras$placeOnSurface(World w, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.chromaextras$placeSurfaceMeteorite(w));
    }

    private boolean chromaextras$placeSurfaceMeteorite(World w) {
        // No meteors in sky-less dimensions (stock behaviour: spawnMeteoriteCenter rejected these too).
        if (w.provider.hasNoSky) {
            return false;
        }

        // Find the real ground surface. getHeightValue is the heightmap top (includes canopy), so start slightly
        // above it and walk down past foliage/trunks/plants to the first genuine ground block. The meteor chunk is
        // the chunk currently being populated, so this reads no unloaded chunks (no cascading worldgen).
        int yy = Math.min(w.getHeightValue(x, z) + 8, 250);
        int groundY = -1;
        for (; yy >= 40; yy--) {
            Block b = w.getBlock(x, yy, z);
            Material m = b.getMaterial();
            if (m == Material.air || m == Material.leaves
                || m == Material.wood
                || m == Material.plants
                || m == Material.vine
                || m == Material.cactus
                || m == Material.snow
                || m == Material.web
                || m == Material.gourd) {
                continue; // not ground - keep descending
            }
            if (m.isLiquid()) {
                return false; // ocean/lake/lava column: skip this meteorite rather than bury it under liquid
            }
            groundY = yy;
            break;
        }
        if (groundY < 0) {
            return false; // no ground above y=40 (void-like column)
        }

        MeteoritePlacer placer = new MeteoritePlacer(new ChunkOnly(w, x >> 4, z >> 4), seed, x, groundY, z);

        // Prefer AE2's own validated placement (identical to stock on vanilla terrain). If its vanilla-only ground
        // whitelist rejects modded surfaces (dye grass, GeoStrata rock, nearby logs), force the placement at the
        // surface instead of letting the stock code entomb the meteorite in deep stone.
        if (!placer.spawnMeteoriteCenter()) {
            ((MeteoritePlacerAccessor) (Object) placer).chromaextras$spawnMeteorite();
            // CRITICAL: spawnMeteoriteCenter() registers the meteorite in AE2's spawn data on success; the forced
            // path must do it too. Without this, chunks generated later never learn a meteorite reaches into them
            // (ExistingMeteoriteSpawn finds nothing), so the crater/skystone get clipped to the origin chunk -
            // a tiny crater with hard chunk-border walls. Registration also feeds the meteorite compass.
            WorldData.instance()
                .spawnData()
                .addNearByMeteorites(
                    w.provider.dimensionId,
                    x >> 4,
                    z >> 4,
                    ((MeteoritePlacerAccessor) (Object) placer).chromaextras$getSettings());
        }

        // Stock neighbor pass: complete the parts of this meteorite that reach into adjacent, already-generated
        // chunks (ChunkOnly clips all writes to one chunk at a time).
        final int px = x >> 4;
        final int pz = z >> 4;
        for (int cx = px - 6; cx < px + 6; cx++) {
            for (int cz = pz - 6; cz < pz + 6; cz++) {
                if (cx == px && cz == pz) {
                    continue;
                }
                if (w.getChunkProvider()
                    .chunkExists(cx, cz)
                    && WorldData.instance()
                        .spawnData()
                        .hasGenerated(w.provider.dimensionId, cx, cz)) {
                    MeteoritePlacer part = new MeteoritePlacer(
                        new ChunkOnly(w, cx, cz),
                        ((MeteoritePlacerAccessor) (Object) placer).chromaextras$getSettings());
                    ((MeteoritePlacerAccessor) (Object) part).chromaextras$spawnMeteorite();
                }
            }
        }
        return true;
    }
}
