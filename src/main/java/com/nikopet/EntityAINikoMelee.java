package com.nikopet;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * Close combat with the item in her hand (HBM swords, chainsaw, tools, anything with attack damage) or bare hands,
 * used when she has no working gun. Does its own swing / damage / item hit effect instead of relying on vanilla mob code.
 */
public class EntityAINikoMelee extends EntityAIBase {
    private final EntityNiko n;
    private int cd, repath;

    public EntityAINikoMelee(EntityNiko n) {
        this.n = n;
        setMutexBits(3);
    }

    private boolean allowed() {
        return n.isTamed() && !n.isSitting() && !n.isNikoSleeping() && !n.fleeing && !n.swapping
                && (!GunHelper.isGun(n.getEquipmentInSlot(0)) || n.gunDry);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase tg = n.getAttackTarget();
        return allowed() && tg != null && tg.isEntityAlive() && !(tg instanceof EntityPlayer)
                && n.getDistanceSqToEntity(tg) < 24.0D * 24.0D;
    }

    @Override
    public boolean continueExecuting() { return shouldExecute(); }

    @Override
    public void startExecuting() {
        cd = 0;
        repath = 0;
        n.task = "fighting";
    }

    @Override
    public void updateTask() {
        EntityLivingBase tg = n.getAttackTarget();
        if (tg == null) return;
        n.getLookHelper().setLookPositionWithEntity(tg, 30.0F, 30.0F);
        double reach = n.width + tg.width + 1.6D;
        boolean close = n.getDistanceSqToEntity(tg) <= reach * reach && Math.abs(tg.posY - n.posY) < 2.5D;
        if (!close) {
            if (--repath <= 0) {
                repath = 8;
                n.getNavigator().tryMoveToEntityLiving(tg, 1.3D);
            }
        } else {
            n.getNavigator().clearPathEntity();
        }
        if (cd > 0) cd--;
        if (close && cd <= 0) {
            cd = 18;
            attack(tg);
        }
    }

    private void attack(EntityLivingBase tg) {
        ItemStack h = n.getEquipmentInSlot(0);
        float dmg = (float) MeleeHelper.damage(h, 3.0D);
        n.swingItem();
        boolean hit = tg.attackEntityFrom(DamageSource.causeMobDamage(n), dmg);
        if (hit && h != null) {
            try {
                h.getItem().hitEntity(h, tg, n); // weapon effects + durability, like a real swing
            } catch (Throwable t) {
                MeleeHelper.lastError = "hitEntity: " + t;
            }
            if (h.stackSize <= 0) {
                n.inv.setInventorySlotContents(0, null);
                n.setCurrentItemOrArmor(0, null);
            }
        }
    }

    @Override
    public void resetTask() {
        n.getNavigator().clearPathEntity();
        n.task = "idle";
    }
}
