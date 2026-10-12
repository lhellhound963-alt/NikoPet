package com.nikopet;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * Owner-commanded fuel rod change: for each marked column, walk there, take the old rod out, put a fresh rod
 * (from her bag) in, walk to the marked storage pool and drop the old rod in. Refuses hot rods or a full pool.
 */
public class EntityAIRodSwap extends EntityAIBase {
    private static final int[][] OFFS = { { 0, 0 }, { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }, { 3, 3 }, { -3, -3 } };
    private final EntityNiko n;
    private int stage, timer, fails; // 0 -> column, 2 -> pool

    public EntityAIRodSwap(EntityNiko n) {
        this.n = n;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() { return n.swapping && n.isTamed() && !n.isSitting(); }

    @Override
    public boolean continueExecuting() { return n.swapping && !n.isSitting(); }

    @Override
    public void startExecuting() {
        stage = n.carrying != null ? 2 : 0;
        timer = 0;
        fails = 0;
        n.task = "changing fuel rods";
    }

    private boolean near(int[] p) {
        double dx = n.posX - (p[0] + 0.5D), dz = n.posZ - (p[2] + 0.5D);
        return dx * dx + dz * dz < 9.0D && Math.abs(n.posY - (p[1] + 1)) < 4.0D;
    }

    private void walk(int[] p) {
        if (timer % 20 != 1) return;
        int[] o = OFFS[fails % OFFS.length];
        boolean started = n.getNavigator().tryMoveToXYZ(p[0] + 0.5D + o[0], p[1] + 1, p[2] + 0.5D + o[1], 1.1D);
        if (!started) fails++;
    }

    @Override
    public void updateTask() {
        timer++;
        if (n.pool == null) { n.abortSwap("no storage pool marked."); return; }
        if (stage == 0) {
            if (n.swapIdx >= n.cols.size()) { n.finishSwap(); return; }
            int[] col = n.cols.get(n.swapIdx);
            if (timer > 600) { n.abortSwap("could not reach column " + (n.swapIdx + 1) + "."); return; }
            if (!near(col)) { walk(col); return; }
            TileEntity ct = RodHelper.findLoadable(n.worldObj, col[0], col[1], col[2]);
            TileEntity pt = RodHelper.findPool(n.worldObj, n.pool[0], n.pool[1], n.pool[2]);
            if (ct == null) { skip("no RBMK column at the marked block."); return; }
            if (pt == null) { n.abortSwap("storage pool not found at the marked block."); return; }
            int slot = n.findRodSlot();
            if (slot < 0) { n.abortSwap("no fresh fuel rod in my bag."); return; }
            ItemStack[] res = new ItemStack[1];
            String err = RodHelper.swapAtColumn(ct, pt, n.inv.getStackInSlot(slot), res);
            if (err != null) { n.abortSwap("column " + (n.swapIdx + 1) + ": " + err + ". Nothing changed."); return; }
            ItemStack f = n.inv.getStackInSlot(slot);
            f.stackSize--;
            if (f.stackSize <= 0) n.inv.setInventorySlotContents(slot, null);
            n.carrying = res[0];
            n.tell("Column " + (n.swapIdx + 1) + ": new rod in. Taking the old one to the pool.");
            stage = 2;
            timer = 0;
            fails = 0;
        } else {
            if (n.carrying == null) { stage = 0; timer = 0; return; }
            if (timer > 600) { n.abortSwap("could not reach the pool. I'm still holding the old rod."); return; }
            if (!near(n.pool)) { walk(n.pool); return; }
            TileEntity pt = RodHelper.findPool(n.worldObj, n.pool[0], n.pool[1], n.pool[2]);
            if (pt != null && RodHelper.poolLoad(pt, n.carrying)) {
                n.carrying = null;
                n.cols.remove(n.swapIdx); // done: this column mark is consumed
                n.tell("Old rod stored in the pool.");
                stage = 0;
                timer = 0;
                fails = 0;
            } else {
                n.abortSwap("pool refused the old rod. I'm still holding it.");
            }
        }
    }

    private void skip(String why) {
        n.tell("Column " + (n.swapIdx + 1) + ": " + why + " Skipping.");
        n.swapIdx++;
        timer = 0;
        fails = 0;
    }

    @Override
    public void resetTask() { n.task = "idle"; }
}
