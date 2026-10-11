package com.nikopet;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/** Walks through the owner's checkpoints, inspects each room, then returns to report. */
public class EntityAIPatrol extends EntityAIBase {
    private final EntityNiko n;
    private int timer;

    public EntityAIPatrol(EntityNiko n) {
        this.n = n;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() { return n.patrolling && n.isTamed(); }

    @Override
    public void startExecuting() { timer = 0; n.task = "inspecting rooms"; }

    @Override
    public boolean continueExecuting() { return n.patrolling; }

    @Override
    public void updateTask() {
        timer++;
        if (n.patrolIndex < n.points.size()) {
            int[] p = n.points.get(n.patrolIndex);
            double tx = p[0] + 0.5D, tz = p[2] + 0.5D;
            if (timer % 20 == 1) n.getNavigator().tryMoveToXYZ(tx, p[1], tz, 1.1D);
            boolean reached = n.getDistanceSq(tx, p[1], tz) < 6.0D;
            if (reached || timer > 400) {
                n.findings.add(n.inspect(p, reached));
                n.patrolIndex++;
                timer = 0;
            }
        } else {
            EntityLivingBase owner = n.getOwner();
            if (owner == null || n.getDistanceToEntity(owner) < 3.0F || timer > 600) {
                n.finishPatrol();
                return;
            }
            if (timer % 20 == 1) n.getNavigator().tryMoveToEntityLiving(owner, 1.1D);
        }
    }

    @Override
    public void resetTask() { n.task = "idle"; }
}
