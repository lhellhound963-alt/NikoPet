package com.nikopet;

import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

/** Tamed Niko walks to nearby dropped items and stores them. */
public class EntityAIPickupItems extends EntityAIBase {
    private final EntityNiko n;
    private EntityItem target;

    public EntityAIPickupItems(EntityNiko n) {
        this.n = n;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!n.isTamed() || n.isSitting() || n.getRNG().nextInt(10) != 0) return false;
        EntityLivingBase owner = n.getOwner();
        if (owner != null && n.getDistanceToEntity(owner) > 16.0F) return false;
        List l = n.worldObj.getEntitiesWithinAABB(EntityItem.class, n.boundingBox.expand(10.0D, 3.0D, 10.0D));
        double best = Double.MAX_VALUE;
        target = null;
        for (Object o : l) {
            EntityItem it = (EntityItem) o;
            double d = n.getDistanceSqToEntity(it);
            if (!it.isDead && d < best) { best = d; target = it; }
        }
        return target != null;
    }

    @Override
    public void startExecuting() { n.task = "gathering items"; }

    @Override
    public boolean continueExecuting() {
        return target != null && !target.isDead && !n.isSitting() && n.getDistanceSqToEntity(target) < 196.0D;
    }

    @Override
    public void updateTask() {
        n.getNavigator().tryMoveToEntityLiving(target, 1.1D);
        if (n.getDistanceSqToEntity(target) < 2.5D) {
            ItemStack left = n.addToInventory(target.getEntityItem());
            if (left == null) target.setDead(); else target.setEntityItemStack(left);
            target = null;
        }
    }

    @Override
    public void resetTask() { target = null; n.task = "idle"; }
}
