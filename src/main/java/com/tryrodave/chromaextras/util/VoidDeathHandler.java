package com.tryrodave.chromaextras.util;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import com.tryrodave.chromaextras.util.VoidVaultRegistry.VaultLocation;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Death capture for the vaults. If the dying player has a bound vault (see {@code VoidVaultRegistry}) and the death
 * qualifies - void deaths for the Void Vault, any death for the Death Vault - this intercepts {@link PlayerDropsEvent}
 * and stashes every drop in the vault's pending limbo buffer, where items can never be lost. Recovering them into the
 * vault's accessible inventory is what costs RF, one item per second (see {@code TileEntityVoidVault.updateEntity}).
 * Without a valid bound vault, vanilla behaviour is untouched.
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
        // height check excludes /kill).
        boolean voidDeath = "outOfWorld".equals(event.source.getDamageType()) && player.posY <= 0;
        if (loc.type != VoidVaultRegistry.TYPE_DEATH && !voidDeath) {
            return;
        }

        // Every drop goes into the PLAYER's pending limbo, world-save data in the registry - never into the tile.
        // Items cannot be lost: they survive the vault being broken, unloaded, or in another dimension, and any
        // placed, powered vault owned by the player recovers them one at a time for RF. No chunk is ever loaded here.
        int caught = 0;
        for (EntityItem drop : event.drops) {
            ItemStack stack = drop.getEntityItem();
            if (stack == null) {
                continue;
            }
            caught += stack.stackSize;
            registry.enqueuePending(player.getUniqueID(), stack);
        }
        event.drops.clear();
        event.setCanceled(true);

        String vaultName = loc.type == VoidVaultRegistry.TYPE_DEATH ? "Death Vault" : "Void Vault";
        player.addChatMessage(
            new ChatComponentText(
                "Your " + vaultName + " caught " + caught + " item(s). Keep it powered with RF to recover them."));
    }
}
