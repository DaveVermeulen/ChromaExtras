package com.tryrodave.chromaextras.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.tryrodave.chromaextras.util.VoidVaultRegistry;

import cofh.api.energy.IEnergyReceiver;

/**
 * Inventory + recovery logic of the {@link BlockVoidVault Void Vault}: 54 accessible slots (double-chest size) behind
 * a single-chest shell, bound to the player who placed it.
 *
 * <p>
 * When the owner dies (void deaths for this vault; any death for the {@link TileEntityDeathVault Death Vault}),
 * {@code VoidDeathHandler} places every drop into this tile's <b>pending buffer</b> - a separate, unlimited limbo
 * where items can never be lost, but cannot be taken out either. Recovery costs energy: while the vault is powered
 * (standard RF via {@code cofh.api.energy.IEnergyReceiver} - Mekanism cables, Draconic, etc.), it moves items one at
 * a time (every {@code RETURN_INTERVAL} ticks) from the buffer into the accessible inventory, paying
 * {@link #getCostPerItem()} RF per item. While recovering, the lid opens and each item plays an insert sound; when
 * the buffer empties, a final sound plays and the lid shuts.
 */
public class TileEntityVoidVault extends TileEntity implements IInventory, IEnergyReceiver {

    public static final int SIZE = 54;

    /** Ticks between recovered items (10 ticks = 2 items per second). */
    private static final int RETURN_INTERVAL = 15;

    /** Max RF accepted per tick from a single side. */
    private static final int MAX_RECEIVE = 38_400;

    private final ItemStack[] items = new ItemStack[SIZE];
    /**
     * Pending items read from pre-registry tile NBT; migrated into {@link VoidVaultRegistry}'s per-player limbo on
     * the first server tick. The limbo itself lives in the world save, not here, so breaking the vault never touches
     * it.
     */
    private final List<ItemStack> legacyPending = new ArrayList<>();

    private UUID ownerId;
    private String ownerName = "";
    private int energy;

    /** Vanilla-chest lid animation state (see updateEntity, adapted from TileEntityChest). */
    public float lidAngle;
    public float prevLidAngle;
    private int numPlayersUsing;
    /** True while paid recovery is running; mirrored to the client via block event 2 so the lid animates. */
    private boolean returning;
    private int returnTimer;

    // --- balance (the Death Vault overrides both - it is the more expensive upgrade) ---

    /** RF buffer size. */
    public int getCapacity() {
        return 250_000;
    }

    /** RF paid per item recovered from the pending buffer. */
    public int getCostPerItem() {
        return 12_800;
    }

    // --- ownership --------------------------------------------------------------

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

    // --- pending limbo (lives in VoidVaultRegistry, keyed by owner) -----------------

    /** The owner's limbo registry, or null client-side / before the server world exists. */
    private VoidVaultRegistry registry() {
        return worldObj == null || worldObj.isRemote || ownerId == null ? null : VoidVaultRegistry.get();
    }

    /** Items awaiting recovery for this vault's owner (server-side; 0 on the client). */
    public int getPendingItemCount() {
        VoidVaultRegistry reg = this.registry();
        return reg == null ? 0 : reg.getPendingItemCount(ownerId);
    }

    /**
     * For breakBlock: only the accessible inventory spills. The pending limbo is per-player world data and survives
     * the block - place and power a new vault to resume recovery.
     */
    public List<ItemStack> getAllContentsForDrop() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack is : items) {
            if (is != null) {
                out.add(is);
            }
        }
        return out;
    }

    public int getEnergy() {
        return energy;
    }

    /** True while paid recovery runs; drives the lid, the beam (see RenderVoidVault) and the intake particles. */
    public boolean isReturning() {
        return returning;
    }

    // --- recovery + lid animation --------------------------------------------------

    @Override
    public void updateEntity() {
        // server: run paid recovery and keep the client's "returning" flag in sync
        if (!worldObj.isRemote) {
            VoidVaultRegistry reg = this.registry();
            // one-time migration of pre-registry tile NBT into the owner's world-save limbo
            if (!legacyPending.isEmpty() && reg != null) {
                for (ItemStack is : legacyPending) {
                    reg.enqueuePending(ownerId, is);
                }
                legacyPending.clear();
                this.markDirty();
            }
            boolean canWork = reg != null && energy >= this.getCostPerItem() && this.hasRoomForNextPending(reg);
            if (canWork != returning) {
                returning = canWork;
                returnTimer = 0;
                worldObj.addBlockEvent(xCoord, yCoord, zCoord, this.getBlockType(), 2, returning ? 1 : 0);
            }
            if (returning) {
                returnTimer++;
                if (returnTimer >= RETURN_INTERVAL) {
                    returnTimer = 0;
                    this.recoverOneItem();
                }
            }
        }

        // client: the "sucked in" swirl particles while recovering (the beam itself is drawn by the TESR)
        if (worldObj.isRemote && returning && lidAngle > 0.9F) {
            this.spawnIntakeParticles();
        }

        // both sides: lid animation + open/close sounds (sounds emitted server-side only, broadcast to clients)
        boolean open = numPlayersUsing > 0 || returning;
        prevLidAngle = lidAngle;
        if (open && lidAngle == 0.0F && !worldObj.isRemote) {
            worldObj.playSoundEffect(
                xCoord + 0.5,
                yCoord + 0.5,
                zCoord + 0.5,
                "chromaextras:vault.open",
                0.6F,
                worldObj.rand.nextFloat() * 0.1F + 0.95F);
        }
        if ((!open && lidAngle > 0.0F) || (open && lidAngle < 1.0F)) {
            float prev = lidAngle;
            lidAngle += open ? 0.1F : -0.1F;
            if (lidAngle > 1.0F) {
                lidAngle = 1.0F;
            }
            if (lidAngle < 0.5F && prev >= 0.5F && !worldObj.isRemote) {
                worldObj.playSoundEffect(
                    xCoord + 0.5,
                    yCoord + 0.5,
                    zCoord + 0.5,
                    "chromaextras:vault.close",
                    0.6F,
                    worldObj.rand.nextFloat() * 0.1F + 0.95F);
            }
            if (lidAngle < 0.0F) {
                lidAngle = 0.0F;
            }
        }
    }

    /**
     * Spell-swirl particles spawned around/above the open vault that home into its centre, enchanting-table style
     * (see {@code EntityVaultIntakeFX}). One or two per tick keeps a steady stream without machine-gunning.
     */
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private void spawnIntakeParticles() {
        java.util.Random r = worldObj.rand;
        int n = 1 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 0.7 + r.nextDouble() * 0.7;
            double px = xCoord + 0.5 + Math.cos(ang) * rad;
            double py = yCoord + 1.0 + r.nextDouble() * 1.0;
            double pz = zCoord + 0.5 + Math.sin(ang) * rad;
            net.minecraft.client.Minecraft.getMinecraft().effectRenderer.addEffect(
                new com.tryrodave.chromaextras.client.EntityVaultIntakeFX(
                    worldObj,
                    px,
                    py,
                    pz,
                    xCoord + 0.5,
                    yCoord + 0.55,
                    zCoord + 0.5));
        }
    }

    /** True when the owner has pending items and the next one could actually be placed in the inventory. */
    private boolean hasRoomForNextPending(VoidVaultRegistry reg) {
        ItemStack next = reg.peekFirstPending(ownerId);
        if (next == null) {
            return false;
        }
        for (ItemStack in : items) {
            if (in == null) {
                return true;
            }
            if (in.isItemEqual(next) && ItemStack.areItemStackTagsEqual(in, next)
                && in.stackSize < in.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Moves exactly one item from the owner's limbo into the inventory, paying its RF cost and playing a sound. */
    private void recoverOneItem() {
        VoidVaultRegistry reg = this.registry();
        if (reg == null || energy < this.getCostPerItem()) {
            return;
        }
        ItemStack one = reg.extractOnePending(ownerId);
        if (one == null) {
            return;
        }
        ItemStack leftover = this.insert(one);
        if (leftover != null) {
            reg.enqueuePending(ownerId, leftover); // inventory filled up mid-move; back into limbo, stall
            return;
        }
        energy -= this.getCostPerItem();
        boolean done = reg.getPendingItemCount(ownerId) == 0;
        worldObj.playSoundEffect(
            xCoord + 0.5,
            yCoord + 0.5,
            zCoord + 0.5,
            done ? "chromaextras:vault.insert_final" : "chromaextras:vault.insert",
            0.7F,
            1.0F);
        this.markDirty();
    }

    /** Inserts as much of the stack as fits into the accessible inventory; returns the leftover (null when all fit). */
    public ItemStack insert(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
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
        for (int i = 0; i < SIZE && stack.stackSize > 0; i++) {
            if (items[i] == null) {
                items[i] = stack.copy();
                stack.stackSize = 0;
            }
        }
        this.markDirty();
        return stack.stackSize > 0 ? stack : null;
    }

    // --- IEnergyReceiver ----------------------------------------------------------

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        int accepted = Math.min(maxReceive, Math.min(MAX_RECEIVE, this.getCapacity() - energy));
        if (!simulate && accepted > 0) {
            energy += accepted;
            this.markDirty();
        }
        return accepted;
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return energy;
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return this.getCapacity();
    }

    // --- viewer count / client events ----------------------------------------------

    @Override
    public boolean receiveClientEvent(int id, int value) {
        if (id == 1) {
            numPlayersUsing = value;
            return true;
        }
        if (id == 2) {
            returning = value != 0;
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

    // --- client sync (owner name + recovery state; items stay server-side) ----------

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("OwnerName", ownerName);
        tag.setBoolean("Returning", returning);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
        net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        NBTTagCompound tag = pkt.func_148857_g();
        ownerName = tag.getString("OwnerName");
        returning = tag.getBoolean("Returning");
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

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
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
        legacyPending.clear();
        NBTTagList plist = tag.getTagList("Pending", 10); // pre-registry format; migrated on first server tick
        for (int i = 0; i < plist.tagCount(); i++) {
            ItemStack is = ItemStack.loadItemStackFromNBT(plist.getCompoundTagAt(i));
            if (is != null) {
                legacyPending.add(is);
            }
        }
        energy = tag.getInteger("Energy");
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
        if (!legacyPending.isEmpty()) {
            // not yet migrated (saved before the first server tick) - keep the legacy data intact
            NBTTagList plist = new NBTTagList();
            for (ItemStack is : legacyPending) {
                NBTTagCompound e = new NBTTagCompound();
                is.writeToNBT(e);
                plist.appendTag(e);
            }
            tag.setTag("Pending", plist);
        }
        tag.setInteger("Energy", energy);
        if (ownerId != null) {
            tag.setLong("OwnerIdMost", ownerId.getMostSignificantBits());
            tag.setLong("OwnerIdLeast", ownerId.getLeastSignificantBits());
        }
        tag.setString("OwnerName", ownerName);
    }
}
