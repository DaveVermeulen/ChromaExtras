package com.tryrodave.chromaextras.util;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import com.tryrodave.chromaextras.blocks.TileEntityVoidVault;
import com.tryrodave.chromaextras.util.VoidVaultRegistry.VaultLocation;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * When a player dies to the void, their drops would fall out of the world and be destroyed. If the player has a bound
 * {@link TileEntityVoidVault Void Vault} (see {@code VoidVaultRegistry}), this intercepts {@link PlayerDropsEvent} and
 * delivers every drop into the vault instead; anything that does not fit is dropped on top of the vault, safely above
 * ground. Without a valid bound vault, vanilla behaviour is untouched.
 *
 * <p>
 * "Died to the void" = the {@code outOfWorld} damage type <em>and</em> the player below y=0 - the y-check excludes
 * {@code /kill} (which also uses {@code outOfWorld}) at normal heights. HIGH priority so the drops are rescued before
 * other death-loot handlers see them.
 */
public class VoidDeathHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerDrops(PlayerDropsEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote || event.drops.isEmpty()) {
            return;
        }

        VoidVaultRegistry registry = VoidVaultRegistry.get();
        if (registry == null) {
            return;
        }
        VaultLocation loc = registry.getBinding(player.getUniqueID());
        if (loc == null) {
            return;
        }
        // A Death Vault catches every death; the plain Void Vault only void deaths ("outOfWorld" below y=0 - the
        // height check excludes /kill). Checked from the binding's stored type BEFORE touching the vault's chunk, so
        // ordinary deaths with a plain vault never force a chunk load.
        boolean voidDeath = "outOfWorld".equals(event.source.getDamageType()) && player.posY <= 0;
        if (loc.type != VoidVaultRegistry.TYPE_DEATH && !voidDeath) {
            return;
        }
        WorldServer world = DimensionManager.getWorld(loc.dimension);
        if (world == null) {
            return; // vault's dimension not loaded; fall back to vanilla drops
        }
        TileEntity te = world.getTileEntity(loc.x, loc.y, loc.z); // loads the chunk from disk if needed
        if (!(te instanceof TileEntityVoidVault)) {
            // vault is gone (broken without unbind, chunk regenerated, ...) - clear the stale binding
            registry.unbindIfAt(player.getUniqueID(), loc.dimension, loc.x, loc.y, loc.z);
            return;
        }

        TileEntityVoidVault vault = (TileEntityVoidVault) te;
        int overflowed = 0;
        for (EntityItem drop : event.drops) {
            ItemStack stack = drop.getEntityItem();
            if (stack == null) {
                continue;
            }
            ItemStack leftover = vault.insert(stack);
            if (leftover != null) {
                overflowed++;
                world.spawnEntityInWorld(new EntityItem(world, loc.x + 0.5, loc.y + 1.25, loc.z + 0.5, leftover));
            }
        }
        event.drops.clear();
        event.setCanceled(true);

        String vaultName = loc.type == VoidVaultRegistry.TYPE_DEATH ? "Death Vault" : "Void Vault";
        player.addChatMessage(
            new ChatComponentText(
                "Your " + vaultName
                    + " caught your items"
                    + (overflowed > 0 ? " (" + overflowed + " stack(s) did not fit and dropped on top of it)" : "")
                    + "."));
    }
}
