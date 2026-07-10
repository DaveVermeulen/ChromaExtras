package com.tryrodave.chromaextras.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.tryrodave.chromaextras.items.ItemHiveGoggles;

import Reika.ChromatiCraft.Registry.ChromaIcons;
import Reika.ChromatiCraft.Registry.CrystalElement;
import Reika.ChromatiCraft.Render.Particle.EntityCCBlurFX;
import Reika.DragonAPI.Libraries.Java.ReikaRandomHelper;
import Reika.DragonAPI.Libraries.Rendering.ReikaColorAPI;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only. While {@link ItemHiveGoggles} are worn and powered, marks every bee hive in the nearby loaded chunks
 * with a soft glow that renders <em>through</em> terrain - the same visual language ChromatiCraft itself uses to
 * reveal buried Unknown Artefacts ({@code ItemUnknownArtefact.doUA_FX}): {@link EntityCCBlurFX} particles with
 * {@code setNoDepthTest()}, a big soft {@link ChromaIcons#FADE_CLOUD} halo plus small bright
 * {@link ChromaIcons#FADE_STAR} sparkles. Colours are Argia's ({@link CrystalElement#LIGHTGRAY}), matching the
 * goggles' energy.
 *
 * <p>
 * Hive positions are cached and rescanned every {@link #SCAN_INTERVAL_TICKS} within a bounded box around the player;
 * only already-loaded chunks are read, so nothing is force-loaded. A block counts as a hive if its registry name
 * contains "hive" - matching Forestry's wild {@code beehives}, Extra Bees' {@code hive}, and ChromatiCraft's crystal
 * {@code HIVE} with no compile-time dependency on any of them; results are memoised per block type. Particles are
 * emitted from a client tick handler at a steady trickle per hive, exactly like the ambient artefact shimmer.
 */
@SideOnly(Side.CLIENT)
public class HiveVisionHandler {

    private static final int SCAN_RADIUS_XZ = 48;
    private static final int SCAN_RADIUS_Y = 32;
    private static final int SCAN_INTERVAL_TICKS = 40; // rescan every ~2s
    private static final int FX_INTERVAL_TICKS = 4; // particle trickle per hive

    private static final Map<Block, Boolean> HIVE_CACHE = new HashMap<>();
    private static final Random rand = new Random();

    /** Cached hive positions (x, y, z triples) from the last scan. */
    private final List<int[]> hives = new ArrayList<>();
    private int ticksSinceScan = SCAN_INTERVAL_TICKS; // scan immediately on first powered tick

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;
        if (player == null || world == null || mc.isGamePaused()) {
            return;
        }

        ItemStack helmet = player.getCurrentArmor(3); // armor slots: 0=boots .. 3=helmet
        if (helmet == null || !(helmet.getItem() instanceof ItemHiveGoggles)) {
            return;
        }
        if (!ItemHiveGoggles.isPowered(player, helmet)) {
            return;
        }

        ticksSinceScan++;
        if (ticksSinceScan >= SCAN_INTERVAL_TICKS) {
            this.rescan(world, player);
            ticksSinceScan = 0;
        }

        if (!hives.isEmpty() && world.getTotalWorldTime() % FX_INTERVAL_TICKS == 0) {
            for (int[] pos : hives) {
                this.spawnHiveFX(world, pos[0], pos[1], pos[2]);
            }
        }
    }

    private void rescan(World world, EntityPlayer player) {
        hives.clear();
        int ox = MathHelper.floor_double(player.posX);
        int oy = MathHelper.floor_double(player.posY);
        int oz = MathHelper.floor_double(player.posZ);
        int yMin = Math.max(0, oy - SCAN_RADIUS_Y);
        int yMax = Math.min(255, oy + SCAN_RADIUS_Y);
        for (int x = ox - SCAN_RADIUS_XZ; x <= ox + SCAN_RADIUS_XZ; x++) {
            for (int z = oz - SCAN_RADIUS_XZ; z <= oz + SCAN_RADIUS_XZ; z++) {
                if (!world.blockExists(x, yMin, z)) {
                    continue; // chunk not loaded on the client; never force it
                }
                for (int y = yMin; y <= yMax; y++) {
                    if (isHive(world.getBlock(x, y, z))) {
                        hives.add(new int[] { x, y, z });
                    }
                }
            }
        }
    }

    private static boolean isHive(Block b) {
        if (b == null) {
            return false;
        }
        Boolean known = HIVE_CACHE.get(b);
        if (known != null) {
            return known;
        }
        boolean res = false;
        UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(b);
        if (uid != null && uid.name != null
            && uid.name.toLowerCase(Locale.ROOT)
                .contains("hive")) {
            res = true;
        }
        HIVE_CACHE.put(b, res);
        return res;
    }

    /**
     * The Unknown-Artefact reveal, recoloured to Argia: an occasional large soft halo plus frequent small white
     * sparkles, all with the depth test off so they glow through the terrain between you and the hive.
     */
    private void spawnHiveFX(World world, int x, int y, int z) {
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;

        if (rand.nextInt(3) == 0) {
            double px = ReikaRandomHelper.getRandomPlusMinus(cx, 0.75);
            double py = ReikaRandomHelper.getRandomPlusMinus(cy, 0.75);
            double pz = ReikaRandomHelper.getRandomPlusMinus(cz, 0.75);
            int l = ReikaRandomHelper.getRandomBetween(20, 45);
            float s = (float) (4 + rand.nextDouble() * 4);
            int c = ReikaColorAPI.mixColors(CrystalElement.LIGHTGRAY.getColor(), 0xffffff, 0.7F);
            EntityCCBlurFX fx = new EntityCCBlurFX(world, px, py, pz);
            fx.setIcon(ChromaIcons.FADE_CLOUD)
                .setColor(c)
                .setLife(l)
                .setScale(s)
                .setAlphaFading()
                .setNoDepthTest();
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }

        int n = 1 + rand.nextInt(2);
        for (int i = 0; i < n; i++) {
            double px = ReikaRandomHelper.getRandomPlusMinus(cx, 1D);
            double py = ReikaRandomHelper.getRandomPlusMinus(cy, 1D);
            double pz = ReikaRandomHelper.getRandomPlusMinus(cz, 1D);
            int l = ReikaRandomHelper.getRandomBetween(5, 12);
            double maxv = 0.125 / l;
            double vx = ReikaRandomHelper.getRandomPlusMinus(0, maxv);
            double vy = ReikaRandomHelper.getRandomPlusMinus(0, maxv);
            double vz = ReikaRandomHelper.getRandomPlusMinus(0, maxv);
            EntityCCBlurFX fx = new EntityCCBlurFX(world, px, py, pz, vx, vy, vz);
            fx.setIcon(ChromaIcons.FADE_STAR)
                .setColor(0xffffff)
                .setLife(l)
                .setRapidExpand()
                .setNoDepthTest();
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }
    }
}
