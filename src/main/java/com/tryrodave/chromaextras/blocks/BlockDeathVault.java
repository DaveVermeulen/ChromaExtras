package com.tryrodave.chromaextras.blocks;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.tryrodave.chromaextras.util.VoidVaultRegistry;

/**
 * The Death Vault: the Void Vault's upgrade. Same player-bound single-chest strongbox (orange), but its owner's drops
 * are delivered here on <em>any</em> death, not just void deaths. Shares the one-vault-per-player binding with the
 * Void Vault, so a player can have one of the two, never both.
 */
public class BlockDeathVault extends BlockVoidVault {

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityDeathVault();
    }

    @Override
    protected int vaultType() {
        return VoidVaultRegistry.TYPE_DEATH;
    }
}
