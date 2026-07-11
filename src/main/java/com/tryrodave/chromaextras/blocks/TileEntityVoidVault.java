package com.tryrodave.chromaextras.blocks;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

/**
 * Inventory of the {@link BlockVoidVault Void Vault}: 54 slots (double-chest size) behind a single-chest shell. Bound
 * to the player who placed it; when that player dies to the void, {@code VoidDeathHandler} delivers their drops here
 * (see {@code VoidVaultRegistry} for the per-player binding). Opened with the vanilla chest GUI via
 * {@code displayGUIChest}, so no custom container/GUI/network code is needed.
 */
public class TileEntityVoidVault extends TileEntity implements IInventory {

    public static final int SIZE = 54;

    private final ItemStack[] items = new ItemStack[SIZE];
    private UUID ownerId;
    private String ownerName = "";

    /** Vanilla-chest lid animation state (see updateEntity, copied from TileEntityChest). */
    public float lidAngle;
    public float prevLidAngle;
    private int numPlayersUsing;

    public void setOwner(EntityPlayer ep) {
        ownerId = ep.getUniqueID();
        ownerName = ep.getCommandSenderName();
        this.markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord); // push the owner name to clients right away
        }
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /** Inserts as much of the stack as fits; returns the leftover (null when fully inserted). */
    public ItemStack insert(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        // First pass: top up existing matching stacks
        for (int i = 0; i < SIZE && stack.stackSize > 0; i++) {
            ItemStack in = items[i];
            if (in != null && in.isItemEqual(stack)
                && ItemStack.areItemStackTagsEqual(in, stack)
                && in.stackSize < in.getMaxStackSize()) {
                int move = Math.min(stack.stackSize, in.getMaxStackSize() - in.stackSize);
                in.stackSize += move;
                stack.stackSize -= move;
            }
        }
        // Second pass: empty slots
        for (int i = 0; i < SIZE && stack.stackSize > 0; i++) {
            if (items[i] == null) {
                items[i] = stack.copy();
                stack.stackSize = 0;
            }
        }
        this.markDirty();
        return stack.stackSize > 0 ? stack : null;
    }

    // --- IInventory -----------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return items[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amt) {
        ItemStack in = items[slot];
        if (in == null) {
            return null;
        }
        if (in.stackSize <= amt) {
            items[slot] = null;
            this.markDirty();
            return in;
        }
        ItemStack split = in.splitStack(amt);
        this.markDirty();
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack in = items[slot];
        items[slot] = null;
        return in;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        items[slot] = stack;
        if (stack != null && stack.stackSize > this.getInventoryStackLimit()) {
            stack.stackSize = this.getInventoryStackLimit();
        }
        this.markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.chromaextras.void_vault";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer ep) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && ep.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    // --- lid animation + sounds (the vanilla TileEntityChest behaviour) --------

    /** Lid open/close lerp and the chest open/close sounds; runs on both sides like the vanilla chest. */
    @Override
    public void updateEntity() {
        prevLidAngle = lidAngle;
        if (numPlayersUsing > 0 && lidAngle == 0.0F) {
            worldObj.playSoundEffect(
                xCoord + 0.5,
                yCoord + 0.5,
                zCoord + 0.5,
                "random.chestopen",
                0.5F,
                worldObj.rand.nextFloat() * 0.1F + 0.9F);
        }
        if ((numPlayersUsing == 0 && lidAngle > 0.0F) || (numPlayersUsing > 0 && lidAngle < 1.0F)) {
            float prev = lidAngle;
            lidAngle += numPlayersUsing > 0 ? 0.1F : -0.1F;
            if (lidAngle > 1.0F) {
                lidAngle = 1.0F;
            }
            if (lidAngle < 0.5F && prev >= 0.5F) {
                worldObj.playSoundEffect(
                    xCoord + 0.5,
                    yCoord + 0.5,
                    zCoord + 0.5,
                    "random.chestclosed",
                    0.5F,
                    worldObj.rand.nextFloat() * 0.1F + 0.9F);
            }
            if (lidAngle < 0.0F) {
                lidAngle = 0.0F;
            }
        }
    }

    @Override
    public boolean receiveClientEvent(int id, int value) {
        if (id == 1) {
            numPlayersUsing = value; // authoritative viewer count, broadcast by openInventory/closeInventory
            return true;
        }
        return super.receiveClientEvent(id, value);
    }

    @Override
    public void openInventory() {
        if (numPlayersUsing < 0) {
            numPlayersUsing = 0;
        }
        numPlayersUsing++;
        worldObj.addBlockEvent(xCoord, yCoord, zCoord, this.getBlockType(), 1, numPlayersUsing);
    }

    @Override
    public void closeInventory() {
        numPlayersUsing--;
        worldObj.addBlockEvent(xCoord, yCoord, zCoord, this.getBlockType(), 1, numPlayersUsing);
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    // --- client sync (owner name for WAILA/tooltips; items stay server-side) ---

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("OwnerName", ownerName);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
        net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        ownerName = pkt.func_148857_g()
            .getString("OwnerName");
    }

    // --- NBT ------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        NBTTagList list = tag.getTagList("Items", 10);
        for (int i = 0; i < SIZE; i++) {
            items[i] = null;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int slot = e.getByte("Slot") & 0xFF;
            if (slot < SIZE) {
                items[slot] = ItemStack.loadItemStackFromNBT(e);
            }
        }
        if (tag.hasKey("OwnerIdMost")) {
            ownerId = new UUID(tag.getLong("OwnerIdMost"), tag.getLong("OwnerIdLeast"));
        }
        ownerName = tag.getString("OwnerName");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SIZE; i++) {
            if (items[i] != null) {
                NBTTagCompound e = new NBTTagCompound();
                e.setByte("Slot", (byte) i);
                items[i].writeToNBT(e);
                list.appendTag(e);
            }
        }
        tag.setTag("Items", list);
        if (ownerId != null) {
            tag.setLong("OwnerIdMost", ownerId.getMostSignificantBits());
            tag.setLong("OwnerIdLeast", ownerId.getLeastSignificantBits());
        }
        tag.setString("OwnerName", ownerName);
    }
}
