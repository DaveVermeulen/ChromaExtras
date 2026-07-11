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
    private static final ResourceLocation TEXTURE_GRADIENT = new ResourceLocation(
        "chromaextras",
        "textures/blocks/vault_gradient.png");

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
        float lid = 0.0F;
        if (te instanceof TileEntityVoidVault) {
            TileEntityVoidVault vault = (TileEntityVoidVault) te;
            float f = vault.prevLidAngle + (vault.lidAngle - vault.prevLidAngle) * partialTicks;
            f = 1.0F - f;
            lid = 1.0F - f * f * f;
        }
        model.chestLid.rotateAngleX = -(lid * (float) Math.PI / 2.0F);
        model.renderAll();
        GL11.glPopMatrix();

        if (te instanceof TileEntityVoidVault && ((TileEntityVoidVault) te).isReturning() && lid > 0.05F) {
            this.renderIntakeBeam(te, x, y, z, lid);
        }
    }

    /**
     * The recovery beam: a glowing 10x10-pixel column rising from the chest centre, textured with
     * {@code vault_gradient.png}. Pixel-true: the column is 10/16 block wide sampling the centred 10-pixel window of
     * the 16x16 texture, and exactly one block tall so one texture pixel = 1/16 block. Drawn additively at full
     * brightness with no depth write, scrolling slowly upward; alpha follows the lid so it fades in/out with the
     * open/close animation.
     */
    private void renderIntakeBeam(TileEntity te, double x, double y, double z, float lid) {
        this.bindTexture(TEXTURE_GRADIENT);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.625, z + 0.5); // centre of the chest, top of the base box (10/16)

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // additive glow, like the artwork
        net.minecraft.client.renderer.OpenGlHelper
            .setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glColor4f(1F, 1F, 1F, lid);

        double half = 5.0 / 16.0; // 10x10 pixel cross-section
        double height = 1.0; // one block tall: 16 texture pixels over 16/16 block - pixel-perfect
        double u0 = 3.0 / 16.0; // centred 10-pixel window of the 16px texture
        double u1 = 13.0 / 16.0;
        double v0 = 1.0; // static, texture bottom at the chest - no tiling artifacts on the fade-out
        double v1 = 0.0;

        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        // four faces of the column (cull is off, so each is visible from both sides)
        tess.addVertexWithUV(-half, 0, -half, u0, v0);
        tess.addVertexWithUV(half, 0, -half, u1, v0);
        tess.addVertexWithUV(half, height, -half, u1, v1);
        tess.addVertexWithUV(-half, height, -half, u0, v1);

        tess.addVertexWithUV(-half, 0, half, u0, v0);
        tess.addVertexWithUV(half, 0, half, u1, v0);
        tess.addVertexWithUV(half, height, half, u1, v1);
        tess.addVertexWithUV(-half, height, half, u0, v1);

        tess.addVertexWithUV(-half, 0, -half, u0, v0);
        tess.addVertexWithUV(-half, 0, half, u1, v0);
        tess.addVertexWithUV(-half, height, half, u1, v1);
        tess.addVertexWithUV(-half, height, -half, u0, v1);

        tess.addVertexWithUV(half, 0, -half, u0, v0);
        tess.addVertexWithUV(half, 0, half, u1, v0);
        tess.addVertexWithUV(half, height, half, u1, v1);
        tess.addVertexWithUV(half, height, -half, u0, v1);
        tess.draw();

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
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
            // Draw the chest model centered on the origin ([-0.5,0.5]^3) and upright. shouldUseRenderHelper enables
            // Forge's standard block helpers, which apply the correct GUI / hand / dropped-item transforms around a
            // centered block - so this must match how a vanilla block sits (centered), nothing more.
            GL11.glRotatef(90F, 0F, 1F, 0F); // face the front toward the viewer, like a vanilla chest item
            GL11.glTranslatef(-0.5F, 0.5F, 0.5F); // center + the chest model's built-in +1 y/z offset, combined
            GL11.glScalef(1.0F, -1.0F, -1.0F); // the ModelChest is built upside-down
            model.chestLid.rotateAngleX = 0.0F; // closed lid for the item form
            model.renderAll();
            GL11.glPopMatrix();
        }
    }
}
