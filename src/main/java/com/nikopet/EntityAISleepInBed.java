package com.nikopet;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;

/** At night a tamed Niko walks to the nearest bed and sleeps on it until morning. */
public class EntityAISleepInBed extends EntityAIBase {
    private final EntityNiko n;
    private int[] bed;
    private int timer;

    public EntityAISleepInBed(EntityNiko n) {
        this.n = n;
        setMutexBits(7);
    }

    private int[] findBed() {
        int px = MathHelper.floor_double(n.posX), py = MathHelper.floor_double(n.posY), pz = MathHelper.floor_double(n.posZ);
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -12; dx <= 12; dx++)
            for (int dy = -3; dy <= 3; dy++)
                for (int dz = -12; dz <= 12; dz++) {
                    if (n.worldObj.getBlock(px + dx, py + dy, pz + dz) == Blocks.bed) {
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d < bd) { bd = d; best = new int[] { px + dx, py + dy, pz + dz }; }
                    }
                }
        return best;
    }

    @Override
    public boolean shouldExecute() {
        if (!n.isTamed() || n.isSitting() || n.patrolling || n.worldObj.isDaytime() || n.getRNG().nextInt(40) != 0) return false;
        bed = findBed();
        return bed != null;
    }

    @Override
    public void startExecuting() { timer = 0; }

    @Override
    public boolean continueExecuting() {
        return bed != null && !n.worldObj.isDaytime() && !n.isSitting() && !n.patrolling && n.hurtTime <= 0 && timer < 1200;
    }

    @Override
    public void updateTask() {
        timer++;
        double bx = bed[0] + 0.5D, by = bed[1] + 0.5625D, bz = bed[2] + 0.5D;
        if (n.getDistanceSq(bx, by, bz) > 2.0D) {
            n.setNikoSleeping(false);
            n.task = "going to bed";
            if (timer % 20 == 1) n.getNavigator().tryMoveToXYZ(bx, bed[1], bz, 1.0D);
        } else {
            n.getNavigator().clearPathEntity();
            // lie along the bed: head toward the pillow, body centered on the two bed blocks
            int meta = n.worldObj.getBlockMetadata(bed[0], bed[1], bed[2]);
            int[][] head = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } }; // foot -> head offset per bed direction (x, z)
            int hx = head[meta & 3][0], hz = head[meta & 3][1];
            int fx = (meta & 8) != 0 ? bed[0] - hx : bed[0], fz = (meta & 8) != 0 ? bed[2] - hz : bed[2]; // foot block
            double cx = fx + 0.5D + 0.5D * hx, cz = fz + 0.5D + 0.5D * hz;                                  // middle of the bed
            float yaw = 180.0F - (float) Math.toDegrees(Math.atan2(hz, -hx));
            n.setPosition(cx - 0.9D * hx, by, cz - 0.9D * hz);
            n.motionX = n.motionY = n.motionZ = 0.0D;
            n.rotationYaw = n.renderYawOffset = n.rotationYawHead = yaw;
            n.prevRotationYaw = n.prevRenderYawOffset = n.prevRotationYawHead = yaw;
            n.setNikoSleeping(true);
            n.task = "sleeping";
            timer = Math.min(timer, 1000);
        }
    }

    @Override
    public void resetTask() {
        n.setNikoSleeping(false);
        n.task = "idle";
        bed = null;
        timer = 0;
    }
}
