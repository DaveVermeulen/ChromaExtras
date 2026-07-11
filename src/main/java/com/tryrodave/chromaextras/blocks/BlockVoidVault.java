package com.tryrodave.chromaextras.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.tryrodave.chromaextras.util.VoidVaultRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Void Vault: a player-bound chest that catches its owner's drops when they die to the void (see
 * {@code VoidDeathHandler}). Single-chest shell (rendered by a TESR with the vanilla chest model and a custom
 * texture), double-chest capacity (54 slots). Placing a vault binds it to the placer - one active vault per player;
 * placing a new one moves the binding. Opened with the vanilla chest GUI.
 */
public class BlockVoidVault extends BlockContainer {

    public BlockVoidVault() {
        super(Material.rock);
        this.setHardness(3.0F);
        this.setResistance(2000.0F); // it guards your void-death loot; make it creeper-proof
        this.setStepSound(soundTypeStone);
        this.setBlockBounds(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityVoidVault();
    }

    @Override
    public int getRenderType() {
        return -1; // TESR-rendered
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // face the placer, like a vanilla chest (meta 2-5)
        int dir = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int meta = dir == 0 ? 2 : dir == 1 ? 5 : dir == 2 ? 3 : 4;
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);

        if (!world.isRemote && placer instanceof EntityPlayer) {
            EntityPlayer ep = (EntityPlayer) placer;
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityVoidVault) {
                ((TileEntityVoidVault) te).setOwner(ep);
            }
            VoidVaultRegistry.get()
                .bind(ep.getUniqueID(), world.provider.dimensionId, x, y, z, this.vaultType());
        }
    }

    /** Which trigger this vault type registers with; the Death Vault overrides. */
    protected int vaultType() {
        return VoidVaultRegistry.TYPE_VOID;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer ep, int side, float hx, float hy,
        float hz) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityVoidVault) {
                ep.displayGUIChest((TileEntityVoidVault) te); // 54 slots -> vanilla double-chest GUI
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityVoidVault) {
            TileEntityVoidVault vault = (TileEntityVoidVault) te;
            // spill the accessible inventory AND the pending limbo buffer - breaking the vault must never lose items
            for (ItemStack in : vault.getAllContentsForDrop()) {
                world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, in));
            }
            if (!world.isRemote && vault.getOwnerId() != null) {
                VoidVaultRegistry.get()
                    .unbindIfAt(vault.getOwnerId(), world.provider.dimensionId, x, y, z);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister ico) {
        // rendered entirely by the TESR; blockIcon is only the particle/fallback texture
        blockIcon = ico.registerIcon("planks_oak");
    }
}
