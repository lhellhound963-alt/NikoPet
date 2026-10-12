package com.nikopet;

import net.minecraft.entity.ai.EntityAIAttackOnCollide;

/** Close combat with whatever is in her hand (HBM swords, chainsaw, tools) or bare hands, when she has no working gun. */
public class EntityAINikoMelee extends EntityAIAttackOnCollide {
    private final EntityNiko n;

    public EntityAINikoMelee(EntityNiko n) {
        super(n, 1.25D, true);
        this.n = n;
    }

    private boolean allowed() {
        return n.isTamed() && !n.isSitting() && !n.isNikoSleeping() && !n.fleeing && !n.swapping
                && (!GunHelper.isGun(n.getEquipmentInSlot(0)) || n.gunDry);
    }

    @Override
    public boolean shouldExecute() { return allowed() && super.shouldExecute(); }

    @Override
    public boolean continueExecuting() { return allowed() && super.continueExecuting(); }

    @Override
    public void startExecuting() {
        super.startExecuting();
        n.task = "fighting";
    }

    @Override
    public void resetTask() {
        super.resetTask();
        n.task = "idle";
    }
}
