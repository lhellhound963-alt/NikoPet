package com.nikopet;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/** Walks through the owner's checkpoints, inspects each room, then returns to report. */
public class EntityAIPatrol extends EntityAIBase {
    private final EntityNiko n;
    private int timer, fails;
    private static final int[][] OFFS = { { 0, 0 }, { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }, { 3, 3 }, { -3, -3 } };

    public EntityAIPatrol(EntityNiko n) {
        this.n = n;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() { return n.patrolling && n.isTamed(); }

    @Override
    public void startExecuting() { timer = 0; fails = 0; n.task = "inspecting rooms"; }

    @Override
    public boolean continueExecuting() { return n.patrolling; }

    @Override
    public void updateTask() {
        timer++;
        if (n.patrolIndex < n.points.size()) {
            int[] p = n.points.get(n.patrolIndex);
            double tx = p[0] + 0.5D, tz = p[2] + 0.5D;
            double dx = n.posX - tx, dz = n.posZ - tz;
            boolean reached = dx * dx + dz * dz < 6.25D && Math.abs(n.posY - p[1]) < 3.0D;
            if (!reached && timer % 20 == 1) {
                int[] o = OFFS[fails % OFFS.length];
                boolean started = n.getNavigator().tryMoveToXYZ(tx + o[0], p[1], tz + o[1], 1.1D);
                if (!started) fails++; // no path to this spot: try a different approach point next time
            }
            if (reached || timer > 600) {
                n.inspectInto(p, reached, fails >= OFFS.length ? "no walkable path" : "timeout");
                n.patrolIndex++;
                timer = 0;
                fails = 0;
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
