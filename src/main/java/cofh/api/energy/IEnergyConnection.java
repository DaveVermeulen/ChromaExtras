package cofh.api.energy;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Vendored RedstoneFlux (RF) API interface, (c) Team CoFH - the RF API is explicitly redistributable and every
 * RF-capable 1.7.10 mod ships these classes (this pack gets them from Mekanism CE at runtime; the classloader
 * takes whichever copy loads first, and all copies are byte-identical by convention).
 *
 * Implement this interface on TileEntities which should connect to energy transportation blocks.
 */
public interface IEnergyConnection {

    /**
     * Returns TRUE if the TileEntity can connect on a given side.
     */
    boolean canConnectEnergy(ForgeDirection from);
}
