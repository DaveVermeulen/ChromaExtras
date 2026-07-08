package com.tryrodave.chromaextras.mixins;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import Reika.ChromatiCraft.World.Nether.LavaRiverGenerator;
import Reika.DragonAPI.Auxiliary.WorldGenInterceptionRegistry;
import Reika.DragonAPI.Instantiable.Data.Immutable.BlockKey;
import Reika.DragonAPI.Instantiable.Event.BlockTickEvent;
import Reika.DragonAPI.Instantiable.Event.SetBlockEvent;
import Reika.DragonAPI.Instantiable.Math.Noise.SimplexNoiseGenerator;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;

/**
 * The nether lava rivers (elevated, y 127-240) are a channel: a floor + one layer of liquid at the river center,
 * flanked by 3-tall walls at the river edge. Two things made this a heavy problem once generation was no longer one
 * contiguous cascade:
 * <ul>
 * <li>the liquid was {@code Blocks.flowing_lava} - a <em>dynamic</em> block with no source, so every one of those
 * blocks keeps re-evaluating (trying to flow/settle) every tick, and pours off any open edge; high in the sky that
 * becomes endless lava falls and constant block updates (severe lag);</li>
 * <li>per-chunk generation leaves open seams where a lava column's containing wall is in a not-yet-generated
 * neighbor, so the lava escapes there.</li>
 * </ul>
 *
 * <p>
 * This overwrite makes the river self-contained and static:
 * <ol>
 * <li>place <b>source</b> lava ({@code Blocks.lava}) instead of {@code flowing_lava}, with flag 2 (no neighbor
 * notify) - contained source liquid never ticks or flows, so no updates and no lag (Forge fluids are already source
 * at meta 0, so they are left as-is);</li>
 * <li>only place liquid in a column whose four orthogonal neighbors are also part of the river (wall or liquid, per
 * the deterministic world-position noise); any column with an open side is sealed with wall instead, so no liquid is
 * ever exposed and nothing can pour out;</li>
 * <li>generate over a one-block margin into neighbor chunks ([-1, 16]) so a chunk always places the walls that
 * enclose its own liquid, independent of neighbor generation timing (safe: the generator only runs once its ±4
 * footprint is loaded, so these writes never force-load a chunk).</li>
 * </ol>
 */
@Mixin(value = LavaRiverGenerator.class, remap = false)
public abstract class MixinLavaRiverGenerator {

    @Shadow
    @Final
    private SimplexNoiseGenerator placementNoise;
    @Shadow
    @Final
    private SimplexNoiseGenerator heightNoise;
    @Shadow
    @Final
    private BlockKey structBlock;

    @Shadow
    protected abstract Block getLiquid(double rx, double rz);

    // Mirrors LavaRiverGenerator's private final constants (compiled inline, so they can't be shadowed).
    private static final double CHROMAEXTRAS$RIVER_THRESH = 0.2;
    private static final double CHROMAEXTRAS$RIVER_CENTER_THRESH = 0.1;
    private static final double CHROMAEXTRAS$MIN_HEIGHT = 127;
    private static final double CHROMAEXTRAS$MAX_HEIGHT = 240;

    private boolean chromaextras$isRiver(int dx, int dz) {
        return Math.abs(placementNoise.getValue(dx / 32D, dz / 32D)) <= CHROMAEXTRAS$RIVER_THRESH;
    }

    /** True only if all four orthogonal neighbors are part of the river, i.e. this liquid column is fully enclosed. */
    private boolean chromaextras$isEnclosed(int dx, int dz) {
        return chromaextras$isRiver(dx + 1, dz) && chromaextras$isRiver(dx - 1, dz)
            && chromaextras$isRiver(dx, dz + 1)
            && chromaextras$isRiver(dx, dz - 1);
    }

    /**
     * @author ChromaExtras
     * @reason Make the lava river self-contained and static (source lava, sealed edges, neighbor margin); the
     *         original relied on contiguous cascading generation and dynamic flowing lava to fill the channel.
     */
    @Overwrite
    public void generate(World world, int chunkX, int chunkZ) {
        WorldGenInterceptionRegistry.skipLighting = true;
        SetBlockEvent.eventEnabledPre = false;
        SetBlockEvent.eventEnabledPost = false;
        BlockTickEvent.disallowAllUpdates = true;

        for (int i = -1; i <= 16; i++) {
            for (int k = -1; k <= 16; k++) {
                int dx = chunkX * 16 + i;
                int dz = chunkZ * 16 + k;
                double rx = dx / 32D;
                double rz = dz / 32D;
                double val = Math.abs(placementNoise.getValue(rx, rz));
                if (val > CHROMAEXTRAS$RIVER_THRESH) {
                    continue;
                }
                double h = ReikaMathLibrary.normalizeToBounds(
                    heightNoise.getValue(rx / 8, rz / 8),
                    CHROMAEXTRAS$MIN_HEIGHT,
                    CHROMAEXTRAS$MAX_HEIGHT);
                int y = (int) h;
                if (val < CHROMAEXTRAS$RIVER_CENTER_THRESH && chromaextras$isEnclosed(dx, dz)) {
                    Block liquid = this.getLiquid(rx, rz);
                    if (liquid == Blocks.flowing_lava) {
                        liquid = Blocks.lava; // source, so it never ticks/flows once placed
                    }
                    world.setBlock(dx, y, dz, structBlock.blockID, structBlock.metadata, 2);
                    world.setBlock(dx, y + 1, dz, liquid, 0, 2);
                } else {
                    // river edge, or a center column with an open side: seal it with wall so no liquid is exposed
                    for (int j = 0; j < 3; j++) {
                        world.setBlock(dx, y + j, dz, structBlock.blockID, structBlock.metadata, 2);
                    }
                }
            }
        }

        BlockTickEvent.disallowAllUpdates = false;
        WorldGenInterceptionRegistry.skipLighting = false;
        SetBlockEvent.eventEnabledPre = true;
        SetBlockEvent.eventEnabledPost = true;
    }
}
