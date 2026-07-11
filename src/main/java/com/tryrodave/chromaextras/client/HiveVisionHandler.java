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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

    /** Sentinel colour for ChromatiCraft's crystal hive: cycle through the rainbow instead of one fixed colour. */
    private static final int RAINBOW = -1;

    /** Sentinel colour for ChromatiCraft's pure hive: each particle rolls either white or a clear blue. */
    private static final int PURE_BLUE_WHITE = -2;

    private static final int PURE_BLUE = 0x4C8CF5;

    /** Cached hive markers (x, y, z, colour quadruples) from the last scan. */
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
        if (helmet == null || !isVisionActive(player, helmet)) {
            return;
        }

        ticksSinceScan++;
        if (ticksSinceScan >= SCAN_INTERVAL_TICKS) {
            this.rescan(world, player);
            ticksSinceScan = 0;
        }

        if (!hives.isEmpty() && world.getTotalWorldTime() % FX_INTERVAL_TICKS == 0) {
            for (int[] pos : hives) {
                // Fade the effect with distance. Underground hives (Extra Bees rock hives spawn at y 20-69, several
                // attempts per chunk) otherwise blanket the whole view in sparkles the moment you look down - nearby
                // hives keep the full effect, far/deep ones just glow faintly now and then.
                double dist = Math.sqrt(player.getDistanceSq(pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5));
                float intensity = dist <= 16 ? 1F : (float) Math.max(0.1, 1 - (dist - 16) / 40);
                this.spawnHiveFX(world, pos[0], pos[1], pos[2], pos[3], intensity);
            }
        }
    }

    /**
     * The reveal is active when the wearer has either powered {@link ItemHiveGoggles}, or a Draconic helmet with the
     * "Apiarist's Vision" tool-config toggle enabled (added by {@code MixinDraconicArmorFields}). The Draconic path is
     * free, like the helmet's other passives (Night Vision etc.).
     */
    private static boolean isVisionActive(EntityPlayer player, ItemStack helmet) {
        if (helmet.getItem() instanceof ItemHiveGoggles) {
            return ItemHiveGoggles.isPowered(player, helmet);
        }
        return isDraconicHelmWithApiaristVision(helmet);
    }

    /**
     * Reads the toggle straight from DE's config-profile NBT ({@code ConfigProfiles[ConfigProfile].ApiaristVision},
     * see {@code IConfigurableItem.ProfileHelper}) so this class needs no Draconic Evolution classes at compile time.
     */
    private static boolean isDraconicHelmWithApiaristVision(ItemStack helmet) {
        UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(helmet.getItem());
        if (uid == null || !"DraconicEvolution".equals(uid.modId) || !"draconicHelm".equals(uid.name)) {
            return false;
        }
        NBTTagCompound tag = helmet.stackTagCompound;
        if (tag == null) {
            return false;
        }
        NBTTagList profiles = tag.getTagList("ConfigProfiles", 10 /* compound */);
        int profile = tag.getInteger("ConfigProfile");
        if (profile < 0 || profile >= profiles.tagCount()) {
            return false;
        }
        return profiles.getCompoundTagAt(profile)
            .getBoolean("ApiaristVision"); // absent = false, matching the field's default
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
                    Block b = world.getBlock(x, y, z);
                    if (isHive(b)) {
                        hives.add(new int[] { x, y, z, getHiveColor(b, world.getBlockMetadata(x, y, z)) });
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
     * Colour for a hive's markers, keyed off the hive block + metadata so each hive type reads at a glance.
     *
     * <ul>
     * <li>Forestry {@code beehives} metas (HiveDescription): 1 FOREST amber, 2 MEADOWS bright yellow, 3 DESERT pale
     * sand, 4 JUNGLE green, 5 END purple, 6 SNOW icy blue, 7 SWAMP murky olive.</li>
     * <li>Extra Bees {@code hive} metas: 0 WATER blue, 1 ROCK gray, 2 NETHER crimson, 3 MARBLE white.</li>
     * <li>ChromatiCraft crystal hive metas: 0 crystal - {@link #RAINBOW rainbow-cycling}, of course - and 1 pure,
     * white.</li>
     * <li>Anything else that calls itself a hive: Argia light-gray, the goggles' own colour.</li>
     * </ul>
     */
    private static int getHiveColor(Block b, int meta) {
        UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(b);
        if (uid == null) {
            return CrystalElement.LIGHTGRAY.getColor();
        }
        String mod = uid.modId.toLowerCase(Locale.ROOT);
        if (mod.equals("forestry")) {
            switch (meta) {
                case 1:
                    return 0xE28C25; // FOREST
                case 2:
                    return 0xD8453C; // MEADOWS
                case 3:
                    return 0xE8DCA4; // DESERT (Modest)
                case 4:
                    return 0x36BE49; // JUNGLE (Tropical)
                case 5:
                    return 0xB666E8; // END (Ended)
                case 6:
                    return 0xA8E1F0; // SNOW (Wintry)
                case 7:
                    return 0x6B7D2A; // SWAMP (Marshy)
                default:
                    return 0xE0B040;
            }
        }
        if (mod.equals("extrabees")) {
            switch (meta) {
                case 0:
                    return 0x3B7BE8; // WATER
                case 1:
                    return 0x50505E; // ROCK - dark slate; dim in the additive glow, far from the pure hive's white
                case 2:
                    return 0xE83B3B; // NETHER
                case 3:
                    return 0xEFE3BC; // MARBLE - warm cream, also distinct from pure white
                default:
                    return 0x3B7BE8;
            }
        }
        if (mod.equals("chromaticraft")) {
            return meta == 0 ? RAINBOW : PURE_BLUE_WHITE; // 0 crystal hive, 1 pure hive
        }
        return CrystalElement.LIGHTGRAY.getColor();
    }

    /**
     * The current colour of a rainbow-cycling marker: ChromatiCraft's own 16-crystal-colour blend cycle
     * ({@code CrystalElement.getBlendedColor}), ~1 second per element, so the crystal hive shimmers through the
     * actual element palette.
     */
    private static int rainbowColor(World world) {
        return CrystalElement.getBlendedColor((int) world.getTotalWorldTime(), 20);
    }

    /** Two-tone colours resolve per particle: the pure hive rolls white or blue for every halo/sparkle it emits. */
    private static int resolvePerParticle(int color) {
        return color == PURE_BLUE_WHITE ? (rand.nextBoolean() ? 0xFFFFFF : PURE_BLUE) : color;
    }

    /**
     * The Unknown-Artefact reveal in the hive's colour: an occasional large soft halo plus small sparkles, all with
     * the depth test off so they glow through the terrain between you and the hive. {@code intensity} (0..1, from
     * player distance) scales how often both spawn, so distant hives shimmer gently instead of strobing.
     */
    private void spawnHiveFX(World world, int x, int y, int z, int color, float intensity) {
        if (color == RAINBOW) {
            color = rainbowColor(world);
        }
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;

        if (rand.nextFloat() < intensity / 3F) {
            double px = ReikaRandomHelper.getRandomPlusMinus(cx, 0.75);
            double py = ReikaRandomHelper.getRandomPlusMinus(cy, 0.75);
            double pz = ReikaRandomHelper.getRandomPlusMinus(cz, 0.75);
            int l = ReikaRandomHelper.getRandomBetween(20, 45);
            float s = (float) (4 + rand.nextDouble() * 4);
            // lighten only slightly so the hive colour stays readable in the big halo
            int c = ReikaColorAPI.mixColors(resolvePerParticle(color), 0xffffff, 0.75F);
            EntityCCBlurFX fx = new EntityCCBlurFX(world, px, py, pz);
            fx.setIcon(ChromaIcons.FADE_CLOUD)
                .setColor(c)
                .setLife(l)
                .setScale(s)
                .setAlphaFading()
                .setNoDepthTest();
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }

        int n = (rand.nextFloat() < 0.7F * intensity ? 1 : 0) + (intensity >= 0.8F && rand.nextBoolean() ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double px = ReikaRandomHelper.getRandomPlusMinus(cx, 1D);
            double py = ReikaRandomHelper.getRandomPlusMinus(cy, 1D);
            double pz = ReikaRandomHelper.getRandomPlusMinus(cz, 1D);
            int l = ReikaRandomHelper.getRandomBetween(8, 14);
            // sparkles carry the hive colour half-mixed toward white: bright, but still tinted. Zero velocity -
            // these are markers, not ambience, and any drift can read as the particle "shooting off".
            int c = ReikaColorAPI.mixColors(resolvePerParticle(color), 0xffffff, 0.5F);
            EntityCCBlurFX fx = new EntityCCBlurFX(world, px, py, pz, 0, 0, 0);
            fx.setIcon(ChromaIcons.FADE_STAR)
                .setColor(c)
                .setLife(l)
                .setRapidExpand()
                .setNoDepthTest();
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }
    }
}
