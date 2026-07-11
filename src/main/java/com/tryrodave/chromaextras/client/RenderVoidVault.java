package com.tryrodave.chromaextras.client;

import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.tryrodave.chromaextras.blocks.TileEntityVoidVault;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renders the Void Vault with the vanilla single-chest model and a custom texture
 * ({@code textures/blocks/void_vault.png}, vanilla 64x64 chest layout). The nested {@link ItemRender} draws the same
 * model for the inventory/held item, since a TESR block with render type -1 otherwise shows nothing as an item.
 */
@SideOnly(Side.CLIENT)
public class RenderVoidVault extends TileEntitySpecialRenderer {

    public static final ResourceLocation TEXTURE_VOID = new ResourceLocation(
        "chromaextras",
        "textures/blocks/void_vault.png");
    public static final ResourceLocation TEXTURE_DEATH = new ResourceLocation(
        "chromaextras",
        "textures/blocks/death_vault.png");

    private final ModelChest model = new ModelChest();

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        int meta = te.hasWorldObj() ? te.getBlockMetadata() : 2;
        this.bindTexture(
            te instanceof com.tryrodave.chromaextras.blocks.TileEntityDeathVault ? TEXTURE_DEATH : TEXTURE_VOID);
        GL11.glPushMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
        // standard vanilla chest transform: the model is built upside-down
        GL11.glTranslated(x, y + 1.0, z + 1.0);
        GL11.glScalef(1.0F, -1.0F, -1.0F);
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        int angle = meta == 2 ? 180 : meta == 3 ? 0 : meta == 4 ? 90 : meta == 5 ? -90 : 0;
        GL11.glRotatef(angle, 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        // vanilla chest lid easing: interpolate, invert, cube - gives the springy open/close
        if (te instanceof TileEntityVoidVault) {
            TileEntityVoidVault vault = (TileEntityVoidVault) te;
            float f = vault.prevLidAngle + (vault.lidAngle - vault.prevLidAngle) * partialTicks;
            f = 1.0F - f;
            f = 1.0F - f * f * f;
            model.chestLid.rotateAngleX = -(f * (float) Math.PI / 2.0F);
        } else {
            model.chestLid.rotateAngleX = 0.0F;
        }
        model.renderAll();
        GL11.glPopMatrix();
    }

    /** Draws the chest model for the block's item form (inventory, hand, ground). One instance per vault texture. */
    @SideOnly(Side.CLIENT)
    public static class ItemRender implements IItemRenderer {

        private final ModelChest model = new ModelChest();
        private final ResourceLocation texture;

        public ItemRender(ResourceLocation texture) {
            this.texture = texture;
        }

        @Override
        public boolean handleRenderType(ItemStack item, ItemRenderType type) {
            return true;
        }

        @Override
        public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
            return true;
        }

        @Override
        public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
            net.minecraft.client.Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            GL11.glPushMatrix();
            GL11.glColor4f(1F, 1F, 1F, 1F);
            if (type == ItemRenderType.INVENTORY) {
                GL11.glTranslatef(0.0F, 0.1F, 0.0F);
            }
            GL11.glRotatef(180F, 0F, 1F, 0F);
            GL11.glTranslatef(-0.5F, 1.0F, 0.5F);
            GL11.glScalef(1.0F, -1.0F, -1.0F);
            model.chestLid.rotateAngleX = 0.0F;
            model.renderAll();
            GL11.glPopMatrix();
        }
    }
}
