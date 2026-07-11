package com.tryrodave.chromaextras.blocks;

/**
 * The Death Vault's tile: the {@link TileEntityVoidVault} machinery with upgrade pricing. It catches drops on
 * <em>any</em> death (see {@code VoidDeathHandler}), so its recovery costs more RF per item and it carries a larger
 * buffer to pay for it.
 */
public class TileEntityDeathVault extends TileEntityVoidVault {

    @Override
    public int getCapacity() {
        return 1_000_000;
    }

    @Override
    public int getCostPerItem() {
        return 25_600;
    }

    @Override
    public String getInventoryName() {
        return "container.chromaextras.death_vault";
    }
}
