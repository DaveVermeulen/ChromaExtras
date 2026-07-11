package com.tryrodave.chromaextras.compat;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.tryrodave.chromaextras.blocks.BlockVoidVault;
import com.tryrodave.chromaextras.blocks.TileEntityVoidVault;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * WAILA tooltip for the Void Vault: shows who it is bound to. Registered via the standard Waila IMC callback
 * ({@code FMLInterModComms.sendMessage("Waila", "register", ...)} in the mod class); the NBT provider ships the owner
 * name from the server so the tooltip is correct even before the tile's description packet arrives.
 */
public class WailaVoidVault implements IWailaDataProvider {

    @SuppressWarnings("unused") // referenced by name in the Waila IMC message
    public static void callbackRegister(IWailaRegistrar registrar) {
        WailaVoidVault provider = new WailaVoidVault();
        registrar.registerBodyProvider(provider, BlockVoidVault.class);
        registrar.registerNBTProvider(provider, BlockVoidVault.class);
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> tooltip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        String owner = accessor.getNBTData() != null ? accessor.getNBTData()
            .getString("OwnerName") : "";
        tooltip.add(owner == null || owner.isEmpty() ? "Unbound" : "Bound to: " + owner);
        return tooltip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (te instanceof TileEntityVoidVault) {
            tag.setString("OwnerName", ((TileEntityVoidVault) te).getOwnerName());
        }
        return tag;
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> tooltip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tooltip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> tooltip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tooltip;
    }
}
