package com.tryrodave.chromaextras.blocks;

/**
 * The Death Vault's tile: identical to the {@link TileEntityVoidVault} except in name - the upgrade is in what
 * triggers delivery ({@code VoidDeathHandler} sends drops here on <em>any</em> death, not just void deaths).
 */
public class TileEntityDeathVault extends TileEntityVoidVault {

    @Override
    public String getInventoryName() {
        return "container.chromaextras.death_vault";
    }
}
