package com.tryrodave.chromaextras.blocks;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.tryrodave.chromaextras.util.VoidVaultRegistry;
import com.tryrodave.chromaextras.util.VoidVaultRegistry.VaultLocation;

/**
 * Enforces the one-Void-Vault-per-player rule at placement time. If the placer already has a bound vault that still
 * exists, placement is refused and they are told where it is. A stale binding (the old vault was destroyed without
 * unbinding, e.g. by another mod) is detected, cleaned up, and placement proceeds.
 */
public class ItemBlockVoidVault extends ItemBlock {

    public ItemBlockVoidVault(Block block) {
        super(block);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        if (!world.isRemote) {
            VoidVaultRegistry registry = VoidVaultRegistry.get();
            if (registry != null) {
                VaultLocation loc = registry.getBinding(player.getUniqueID());
                if (loc != null && vaultStillExists(loc)) {
                    String existing = loc.type == VoidVaultRegistry.TYPE_DEATH ? "Death Vault" : "Void Vault";
                    player.addChatMessage(
                        new ChatComponentText(
                            EnumChatFormatting.RED + "You already have a "
                                + existing
                                + " at "
                                + loc.x
                                + ", "
                                + loc.y
                                + ", "
                                + loc.z
                                + " in dimension "
                                + loc.dimension
                                + ". Break it first."));
                    return false;
                }
                if (loc != null) {
                    // the bound vault is verifiably gone; clear the stale binding and let this placement rebind
                    registry.unbindIfAt(player.getUniqueID(), loc.dimension, loc.x, loc.y, loc.z);
                }
            }
        }
        return super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
    }

    /**
     * True when the bound vault verifiably still exists - or cannot be checked (dimension unloaded), in which case we
     * assume it exists rather than let a player bypass the limit by placing from another dimension.
     */
    private static boolean vaultStillExists(VaultLocation loc) {
        WorldServer world = DimensionManager.getWorld(loc.dimension);
        if (world == null) {
            return true;
        }
        TileEntity te = world.getTileEntity(loc.x, loc.y, loc.z);
        return te instanceof TileEntityVoidVault;
    }
}
