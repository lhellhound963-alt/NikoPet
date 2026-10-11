package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.item.ItemStack;

/** Shoots the current attack target with an HBM gun held in Niko's hand slot (mimics HBM's own mob gun AI). */
public class EntityAIShootGun extends EntityAIBase {
    private final EntityNiko n;
    private int mode; // 0 wait, 1 firing, 2 reloading, 3 out of ammo
    private int timer, reloadTicks, logTimer;
    private boolean warned;

    public EntityAIShootGun(EntityNiko n) {
        this.n = n;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase tg = n.getAttackTarget();
        return tg != null && tg.isEntityAlive() && !n.isSitting() && !n.isNikoSleeping()
                && GunHelper.isGun(n.getEquipmentInSlot(0)) && n.getDistanceSqToEntity(tg) < 26.0D * 26.0D;
    }

    @Override
    public boolean continueExecuting() { return shouldExecute(); }

    @Override
    public void startExecuting() {
        mode = 0; timer = 10; reloadTicks = 0; warned = false;
        n.task = "shooting";
        EntityLivingBase tg = n.getAttackTarget();
        if (tg != null) {
            int gap = n.ticksExisted - n.lastTargetTell;
            if ((tg.getEntityId() != n.lastTargetId && gap >= 100) || gap >= 1200) {
                n.tell("Target: " + tg.getCommandSenderName());
                n.lastTargetId = tg.getEntityId();
                n.lastTargetTell = n.ticksExisted;
            }
        }
    }

    @Override
    public void updateTask() {
        EntityLivingBase tg = n.getAttackTarget();
        ItemStack g = n.getEquipmentInSlot(0);
        if (tg == null || g == null) return;
        n.getLookHelper().setLookPositionWithEntity(tg, 30.0F, 30.0F);
        boolean see = n.getEntitySenses().canSee(tg);
        double d2 = n.getDistanceSqToEntity(tg);
        double range = GunHelper.range(g);
        if (!see || d2 > range * range * 0.5D) n.getNavigator().tryMoveToEntityLiving(tg, 1.0D);
        else n.getNavigator().clearPathEntity();

        aimAt(tg);
        timer--;
        int amt = GunHelper.ammo(n, g);
        if (amt == 0 && mode < 2) { mode = 2; reloadTicks = 0; }
        if (amt != 0 && mode >= 2) { mode = 0; timer = 10; warned = false; }

        if (mode == 2) {
            GunHelper.setKeys(n, g, false, true);
            if (++reloadTicks > 120) {
                mode = 3;
                if (!warned) { n.tell("Out of ammo!"); warned = true; }
            }
        } else if (mode == 3) {
            GunHelper.setKeys(n, g, false, false);
        } else if (mode == 0) {
            GunHelper.setKeys(n, g, false, false);
            if (timer <= 0 && see && d2 < range * range) { mode = 1; timer = 15 + n.getRNG().nextInt(25); }
        } else {
            if (!see || timer <= 0) { mode = 0; timer = 10 + n.getRNG().nextInt(20); GunHelper.setKeys(n, g, false, false); }
            else GunHelper.setKeys(n, g, true, false);
        }
        if (++logTimer % 100 == 0) FMLLog.info("[NikoPet] shoot: mode=%s ammo=%s dist=%.1f see=%s err=%s", String.valueOf(mode), String.valueOf(amt), Math.sqrt(d2), String.valueOf(see), GunHelper.lastError);
    }

    /** Point the body and gun exactly at the target's center (small random spread). */
    private void aimAt(EntityLivingBase tg) {
        double dx = tg.posX - n.posX, dz = tg.posZ - n.posZ;
        double dy = (tg.posY + tg.height * 0.5D) - (n.posY + n.getEyeHeight());
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F + (n.getRNG().nextFloat() - 0.5F) * 3.0F;
        float pitch = (float) -(Math.atan2(dy, flat) * 180.0D / Math.PI) + (n.getRNG().nextFloat() - 0.5F) * 3.0F;
        n.rotationYaw = yaw;
        n.rotationYawHead = yaw;
        n.renderYawOffset = yaw;
        n.rotationPitch = pitch;
    }

    @Override
    public void resetTask() {
        ItemStack g = n.getEquipmentInSlot(0);
        if (g != null && GunHelper.isGun(g)) GunHelper.setKeys(n, g, false, false);
        n.task = "idle";
    }
}
