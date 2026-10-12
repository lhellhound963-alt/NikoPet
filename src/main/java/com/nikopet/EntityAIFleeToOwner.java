package com.nikopet;

import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityMob;

/** Low health (or swarmed): stop fighting, run to the owner and call for help. */
public class EntityAIFleeToOwner extends EntityAIBase {
    private final EntityNiko n;
    private EntityLivingBase owner;
    private int lastTell = -10000;

    public EntityAIFleeToOwner(EntityNiko n) {
        this.n = n;
        setMutexBits(3);
    }

    private int hostiles() {
        List l = n.worldObj.getEntitiesWithinAABB(EntityMob.class, n.boundingBox.expand(8.0D, 3.0D, 8.0D));
        int c = 0;
        for (Object o : l) if (((EntityMob) o).isEntityAlive()) c++;
        return c;
    }

    private float frac() { return n.getHealth() / n.getMaxHealth(); }

    @Override
    public boolean shouldExecute() {
        if (!n.isTamed() || n.isSitting() || n.isNikoSleeping()) return false;
        owner = n.getOwner();
        if (owner == null || !owner.isEntityAlive()) return false;
        float f = frac();
        if (f >= 0.7F) return false;
        int mobs = hostiles();
        return mobs > 0 && (f < 0.4F || mobs >= 4);
    }

    @Override
    public boolean continueExecuting() {
        owner = n.getOwner();
        return owner != null && owner.isEntityAlive() && !n.isSitting() && frac() < 0.6F && hostiles() > 0;
    }

    @Override
    public void startExecuting() {
        n.fleeing = true;
        n.task = "retreating";
        n.setAttackTarget(null);
        if (n.ticksExisted - lastTell >= 600) {
            lastTell = n.ticksExisted;
            n.tell("Monsters are swarming me! HP " + (int) n.getHealth() + "/" + (int) n.getMaxHealth() + ". Running to you, please help!");
        }
    }

    @Override
    public void updateTask() {
        n.setAttackTarget(null);
        if (owner == null) return;
        if (n.getDistanceSqToEntity(owner) > 9.0D) {
            if (n.ticksExisted % 10 == 0) n.getNavigator().tryMoveToEntityLiving(owner, 1.5D);
        } else {
            n.getNavigator().clearPathEntity();
        }
    }

    @Override
    public void resetTask() {
        n.fleeing = false;
        n.task = "idle";
    }
}
