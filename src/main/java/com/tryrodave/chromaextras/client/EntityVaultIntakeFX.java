package com.tryrodave.chromaextras.client;

import net.minecraft.client.particle.EntitySpellParticleFX;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A vanilla potion-swirl particle that gets <em>sucked in</em>: it keeps the spell swirl's look and texture animation
 * but, like the enchanting table's glyphs, homes from its spawn point into a fixed target (the recovering vault's
 * centre) with an ease-in curve - slow drift at first, accelerating as it dives into the chest - and dies on arrival.
 */
@SideOnly(Side.CLIENT)
public class EntityVaultIntakeFX extends EntitySpellParticleFX {

    private final double startX;
    private final double startY;
    private final double startZ;
    private final double targetX;
    private final double targetY;
    private final double targetZ;

    public EntityVaultIntakeFX(World world, double x, double y, double z, double targetX, double targetY,
        double targetZ) {
        super(world, x, y, z, 0, 0, 0);
        startX = x;
        startY = y;
        startZ = z;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        motionX = motionY = motionZ = 0;
        particleMaxAge = 15 + rand.nextInt(10);
        noClip = true; // must be able to pass into the chest, not bounce off it
        this.setRBGColorF(1.0F, 1.0F, 100 / 255F); // 0xFFFF64
    }

    @Override
    public void onUpdate() {
        // let the spell particle age, animate its texture frames and expire...
        super.onUpdate();
        // ...then override its drift entirely with the enchanting-table-style homing curve
        float t = particleAge / (float) particleMaxAge;
        float eased = t * t; // ease-in: gentle at the rim, fast as it plunges into the vault
        posX = startX + (targetX - startX) * eased;
        posY = startY + (targetY - startY) * eased;
        posZ = startZ + (targetZ - startZ) * eased;
        motionX = motionY = motionZ = 0;
    }
}
