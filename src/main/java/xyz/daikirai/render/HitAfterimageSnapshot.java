package xyz.daikirai.render;

import net.minecraft.entity.EntityLivingBase;

/** Captured values never follow the victim's later movement or animation. */
public final class HitAfterimageSnapshot {
    public final EntityLivingBase entity;
    public final double x, y, z;
    public final float bodyYaw, headYaw, pitch, limbSwing, limbAmount, age, swing;
    public final boolean sneaking, riding, child, localPlayer;
    public final long time;

    public HitAfterimageSnapshot(EntityLivingBase entity, long time) {
        this.entity = entity;
        localPlayer = entity == net.minecraft.client.Minecraft.getMinecraft().thePlayer;
        x = entity.posX; y = entity.posY; z = entity.posZ;
        bodyYaw = entity.renderYawOffset; headYaw = entity.rotationYawHead;
        pitch = entity.rotationPitch; limbSwing = entity.limbSwing;
        limbAmount = entity.limbSwingAmount; age = entity.ticksExisted;
        swing = entity.getSwingProgress(1);
        sneaking = entity.isSneaking(); riding = entity.isRiding(); child = entity.isChild();
        this.time = time;
    }

    public float alpha(long now, long lifetime) {
        return Math.max(0, Math.min(1, 1 - (float) (now - time) / Math.max(1, lifetime)));
    }
}
