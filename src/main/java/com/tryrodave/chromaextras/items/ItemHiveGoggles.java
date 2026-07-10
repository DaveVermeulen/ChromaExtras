package com.tryrodave.chromaextras.items;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.tryrodave.chromaextras.ChromaExtras;

import Reika.ChromatiCraft.Auxiliary.TemporaryCrystalReceiver;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalSource;
import Reika.ChromatiCraft.Magic.Network.CrystalNetworker;
import Reika.ChromatiCraft.Magic.Progression.ResearchLevel;
import Reika.ChromatiCraft.Registry.ChromaIcons;
import Reika.ChromatiCraft.Registry.CrystalElement;
import Reika.ChromatiCraft.Render.Particle.EntityCCBlurFX;
import Reika.DragonAPI.DragonAPIInit;
import Reika.DragonAPI.Instantiable.Data.Immutable.WorldLocation;
import Reika.DragonAPI.Libraries.IO.ReikaPacketHelper;
import Reika.DragonAPI.Libraries.Java.ReikaRandomHelper;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Argia's Apiary Goggles - a helmet that reveals nearby bee hives through walls, charged with ChromatiCraft energy.
 *
 * <p>
 * Charging works like ChromatiCraft's powered tools: toss the goggles on the ground near a light-gray-conducting
 * pylon (or other crystal source) and they charge from it. ChromatiCraft's own {@code ToolChargingSystem} cannot be
 * reused directly - it requires the item to implement {@code PoweredItem}, whose {@code getColor(ItemStack)} returning
 * a {@code CrystalElement} clashes with {@code ItemArmor.getColor(ItemStack)} returning an int (leather dye), so an
 * armor piece can never be a {@code PoweredItem}. Instead {@link #onEntityItemUpdate} replicates
 * {@code ToolChargingSystem.tickItem} faithfully: a {@link TemporaryCrystalReceiver} restricted to
 * {@link CrystalElement#LIGHTGRAY} (Argia, the light/vision element) finds the nearest conducting
 * {@link CrystalSource} within 32 blocks, drains it at the same 4x rate, applies the same pylon charge-rate bonuses,
 * and stores the charge in the stack NBT. The dropped item never despawns while waiting ({@link #getEntityLifespan}).
 *
 * <p>
 * While worn, {@link #onArmorTick} drains one charge per tick unless the wearer is in creative. The see-through hive
 * highlight itself lives client-side in {@code HiveVisionHandler}; this item owns only the charge.
 */
public class ItemHiveGoggles extends ItemArmor {

    /** Full charge. At {@link #DRAIN_PER_TICK} per tick this is ~40 minutes of continuous wear. */
    public static final int MAX_CHARGE = 48000;

    /** Charge drained per tick while worn (20 t/s), unless the wearer is in creative. */
    private static final int DRAIN_PER_TICK = 1;

    /** Same source search radius as ToolChargingSystem.CHARGE_RANGE. */
    private static final int CHARGE_RANGE = 32;

    private static final String NBT_CHARGE = "charge";

    private IIcon icon;

    public ItemHiveGoggles(ArmorMaterial material) {
        super(material, 0, 0 /* armorType 0 = helmet */);
        this.maxStackSize = 1;
        this.setMaxDamage(0); // unbreakable; charge lives in NBT, never in item damage
        this.setNoRepair();
    }

    // --- charge storage -------------------------------------------------------

    public static int getCharge(ItemStack is) {
        return is.stackTagCompound != null ? is.stackTagCompound.getInteger(NBT_CHARGE) : 0;
    }

    private static void setCharge(ItemStack is, int amt) {
        if (is.stackTagCompound == null) {
            is.stackTagCompound = new NBTTagCompound();
        }
        is.stackTagCompound.setInteger(NBT_CHARGE, MathHelper.clamp_int(amt, 0, MAX_CHARGE));
    }

    /** True when the reveal should render: the wearer is in creative, or the goggles still hold charge. */
    public static boolean isPowered(EntityPlayer ep, ItemStack is) {
        return ep.capabilities.isCreativeMode || getCharge(is) > 0;
    }

    // --- draining while worn ----------------------------------------------------

    @Override
    public void onArmorTick(World world, EntityPlayer ep, ItemStack is) {
        if (world.isRemote || ep.capabilities.isCreativeMode) {
            return;
        }
        int charge = getCharge(is);
        if (charge > 0) {
            setCharge(is, charge - DRAIN_PER_TICK);
        }
    }

    // --- pylon charging while dropped (mirrors ToolChargingSystem.tickItem) -----

    @Override
    public int getEntityLifespan(ItemStack is, World world) {
        return Integer.MAX_VALUE; // never despawn while sitting by a pylon
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem ei) {
        ItemStack is = ei.getEntityItem();
        int charge = getCharge(is);
        if (!ei.worldObj.isRemote) {
            if (charge < MAX_CHARGE) {
                WorldLocation loc = new WorldLocation(ei);
                TemporaryCrystalReceiver r = new TemporaryCrystalReceiver(
                    loc,
                    0,
                    CHARGE_RANGE,
                    0.0625,
                    ResearchLevel.ENDGAME);
                r.addColorRestriction(CrystalElement.LIGHTGRAY);
                int amt = this.getChargeRate(charge);
                CrystalSource s = CrystalNetworker.instance.getNearestTileOfType(r, CrystalSource.class, CHARGE_RANGE);
                if (s != null && s.isConductingElement(CrystalElement.LIGHTGRAY)) {
                    float rate = s.getDroppedItemChargeRate(is);
                    if (rate > 0) {
                        amt *= rate;
                        s.drain(CrystalElement.LIGHTGRAY, amt * 4);
                        // Same pylon boosts as ToolChargingSystem. Checked reflectively: naming
                        // TileEntityCrystalPylon directly drags in its Thaumcraft INode superinterface (injected by
                        // DragonAPI ASM at runtime), which is not on the compile classpath.
                        if (s.getClass()
                            .getSimpleName()
                            .equals("TileEntityCrystalPylon")) {
                            amt *= 1.25; // 25% pylon boost
                            try {
                                if (Boolean.TRUE.equals(
                                    s.getClass()
                                        .getMethod("isEnhanced")
                                        .invoke(s))) {
                                    amt *= 1.6; // net 2x for enhanced pylons
                                }
                            } catch (Exception ignored) {
                                // no enhanced boost if the method is unavailable; base boost still applies
                            }
                        }
                        setCharge(is, charge + amt);
                        ReikaPacketHelper.sendEntitySyncPacket(DragonAPIInit.packetChannel, ei, 32);
                    }
                }
                r.destroy();
            }
        } else if (charge > 0) {
            this.doChargingFX(ei, charge);
        }
        return false;
    }

    /** Same easing curve as ToolChargingSystem.getChargeRate: fast when empty, slowing as it fills. */
    private int getChargeRate(int charge) {
        return (int) (5 * Math.min(20, 1 + 100 * ReikaMathLibrary.cosInterpolation(0, MAX_CHARGE, charge)));
    }

    @SideOnly(Side.CLIENT)
    private void doChargingFX(EntityItem ei, int charge) {
        if (ei.worldObj.rand.nextInt(charge >= MAX_CHARGE ? 2 : 6) > 0) {
            return;
        }
        double px = ReikaRandomHelper.getRandomPlusMinus(ei.posX, 0.5);
        double py = ReikaRandomHelper.getRandomPlusMinus(ei.posY + 0.25, 0.25);
        double pz = ReikaRandomHelper.getRandomPlusMinus(ei.posZ, 0.5);
        EntityCCBlurFX fx = new EntityCCBlurFX(ei.worldObj, px, py, pz, 0, 0.03125, 0);
        fx.setIcon(ChromaIcons.FADE)
            .setColor(CrystalElement.LIGHTGRAY.getColor())
            .setLife(30)
            .setScale(1.5F)
            .setAlphaFading();
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    // --- creative tab / tooltip --------------------------------------------------

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List li) {
        ItemStack empty = new ItemStack(item);
        li.add(empty);
        ItemStack full = new ItemStack(item);
        setCharge(full, MAX_CHARGE);
        li.add(full);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack is, EntityPlayer ep, List li, boolean vb) {
        li.add(String.format("Energy: %.1f%%", 100F * getCharge(is) / MAX_CHARGE));
        li.add(EnumChatFormatting.GRAY + "Reveals nearby bee hives through walls.");
        li.add(EnumChatFormatting.DARK_GRAY + "Charge near a Light Gray (Argia) pylon.");
    }

    // --- icon / armor texture ------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister ico) {
        icon = ico.registerIcon(ChromaExtras.MODID + ":hive_goggles");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int dmg) {
        return icon;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, int slot, String type) {
        return ChromaExtras.MODID + ":textures/models/armor/hive_goggles_layer_1.png";
    }
}
