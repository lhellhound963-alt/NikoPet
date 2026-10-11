package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.item.ItemStack;

/** Shoots the current attack target with an HBM gun held in Niko's hand slot. */
public class EntityAIShootGun extends EntityAIBase {
    private final EntityNiko n;
    private int t;

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
    public void startExecuting() { t = 0; n.task = "shooting"; }

    @Override
    public void updateTask() {
        EntityLivingBase tg = n.getAttackTarget();
        ItemStack g = n.getEquipmentInSlot(0);
        if (tg == null || g == null) return;
        n.getLookHelper().setLookPositionWithEntity(tg, 30.0F, 30.0F);
        boolean see = n.getEntitySenses().canSee(tg);
        if (!see || n.getDistanceSqToEntity(tg) > 196.0D) n.getNavigator().tryMoveToEntityLiving(tg, 1.0D);
        else n.getNavigator().clearPathEntity();
        t++;
        int c = t % 60;
        if (see && c < 40) {
            if (c % 8 == 0) GunHelper.key(n, g, false, true);
            else if (c % 8 == 2) GunHelper.key(n, g, false, false);
        } else if (c == 40) {
            GunHelper.key(n, g, false, false);
            GunHelper.key(n, g, true, true);
        } else if (c == 42) {
            GunHelper.key(n, g, true, false);
        }
        if (c == 0) FMLLog.info("[NikoPet] shooting cycle, target=%s dist=%.1f see=%s", tg.getCommandSenderName(), n.getDistanceToEntity(tg), String.valueOf(see));
    }

    @Override
    public void resetTask() {
        ItemStack g = n.getEquipmentInSlot(0);
        if (g != null && GunHelper.isGun(g)) {
            GunHelper.key(n, g, false, false);
            GunHelper.key(n, g, true, false);
        }
        n.task = "idle";
    }
}
