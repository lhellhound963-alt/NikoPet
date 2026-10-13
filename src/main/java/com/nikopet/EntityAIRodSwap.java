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
    private int cstage, ctimer; // crane mode: 0 check, 1 move, 2 take old rod, 3 insert fresh rod, 9 put old rod back
    private String revert;

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
        cstage = 0;
        ctimer = 0;
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
            TileEntity crane = n.useCrane ? CraneHelper.find(n.worldObj, col[0], col[2], 96) : null;
            int[] spot = crane != null ? new int[] { crane.xCoord, crane.yCoord, crane.zCoord } : col; // operator stands at the crane console
            if (timer > 600) { n.abortSwap("could not reach " + (crane != null ? "the crane console." : "column " + label() + ".")); return; }
            if (!near(spot)) { walk(spot); return; }
            TileEntity ct = RodHelper.findLoadable(n.worldObj, col[0], col[1], col[2]);
            TileEntity pt = RodHelper.findPool(n.worldObj, n.pool[0], n.pool[1], n.pool[2]);
            if (ct == null) { skip("no RBMK column at the marked block."); return; }
            if (pt == null) { n.abortSwap("storage pool not found at the marked block."); return; }
            if (crane != null) { craneStep(col, ct, pt, crane); return; }
            int slot = n.findRodSlot();
            if (slot < 0) { n.abortSwap("no fresh fuel rod in my bag."); return; }
            ItemStack[] res = new ItemStack[1];
            String err = RodHelper.swapAtColumn(ct, pt, n.inv.getStackInSlot(slot), res);
            if (err != null) { n.abortSwap("column " + label() + ": " + err + ". Nothing changed."); return; }
            ItemStack f = n.inv.getStackInSlot(slot);
            f.stackSize--;
            if (f.stackSize <= 0) n.inv.setInventorySlotContents(slot, null);
            n.carrying = res[0];
            n.tell("Column " + label() + ": new rod in. Taking the old one to the pool.");
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

    /** Take one fresh rod out of the bag (stack of 1) or null. */
    private ItemStack takeFresh() {
        int slot = n.findRodSlot();
        if (slot < 0) return null;
        ItemStack f = n.inv.getStackInSlot(slot);
        ItemStack one = f.copy();
        one.stackSize = 1;
        f.stackSize--;
        if (f.stackSize <= 0) n.inv.setInventorySlotContents(slot, null);
        return one;
    }

    private void startRevert(TileEntity crane, String why) {
        revert = why;
        if (n.carrying != null) {
            CraneHelper.give(crane, n.carrying);
            n.carrying = null;
            CraneHelper.startCycle(crane);
            cstage = 9;
            ctimer = 0;
        } else {
            n.abortSwap(why);
        }
    }

    /** Fuel change through the RBMK Crane Console: the crane moves over the column, lifts the old rod out and drops the fresh one in. */
    private void craneStep(int[] col, TileEntity ct, TileEntity pt, TileEntity crane) {
        ctimer++;
        if (cstage == 0) {
            int slot = n.findRodSlot();
            if (slot < 0) { n.abortSwap("no fresh fuel rod in my bag."); return; }
            String err = RodHelper.preflight(ct, pt, n.inv.getStackInSlot(slot));
            if (err != null) { n.abortSwap("column " + label() + ": " + err + ". Nothing changed."); return; }
            if (CraneHelper.busy(crane) || CraneHelper.hasItem(crane)) { n.abortSwap("the crane is busy or holding something (" + CraneHelper.state(crane) + "). Empty it first."); return; }
            n.tell("Using the crane at " + crane.xCoord + "," + crane.yCoord + "," + crane.zCoord + ".");
            cstage = 1;
            ctimer = 0;
        } else if (cstage == 1) {
            if (CraneHelper.moveToward(crane, col[0], col[2])) {
                CraneHelper.startCycle(crane); // lowers over the column; with an empty hook it lifts the old rod out
                cstage = 2;
                ctimer = 0;
            } else if (ctimer > 1500) n.abortSwap("the crane could not get over column " + label() + " (" + CraneHelper.lastError + "). Nothing changed.");
        } else if (cstage == 2) {
            if (CraneHelper.busy(crane)) { if (ctimer > 800) n.abortSwap("the crane is stuck. Nothing was taken out that I know of."); return; }
            ItemStack old = CraneHelper.takeLoaded(crane);
            if (old == null) { n.abortSwap("the crane did not lift a rod out of column " + label() + ". Nothing changed."); return; }
            n.carrying = old;
            int fs = n.findRodSlot();
            ItemStack fresh = (fs >= 0 && RodHelper.canLoadInto(ct, n.inv.getStackInSlot(fs))) ? takeFresh() : null;
            if (fresh == null) { startRevert(crane, "the column would not take the new rod, old rod is being put back."); return; }
            CraneHelper.give(crane, fresh);
            CraneHelper.startCycle(crane); // with a rod on the hook it lowers it into the column
            cstage = 3;
            ctimer = 0;
        } else if (cstage == 3) {
            if (CraneHelper.busy(crane)) { if (ctimer > 800) n.abortSwap("the crane is stuck while inserting the new rod. I'm holding the old rod."); return; }
            if (CraneHelper.hasItem(crane)) { // column refused: take the new rod back and restore the old one
                ItemStack back = CraneHelper.takeLoaded(crane);
                if (back != null) n.addToInventory(back);
                startRevert(crane, "the column refused the new rod, old rod is being put back.");
                return;
            }
            n.tell("Column " + label() + ": new rod in (by crane). Taking the old one to the pool.");
            cstage = 0;
            stage = 2;
            timer = 0;
            fails = 0;
        } else if (cstage == 9) {
            if (CraneHelper.busy(crane)) { if (ctimer > 800) n.abortSwap("the crane is stuck while putting the old rod back."); return; }
            ItemStack left = CraneHelper.hasItem(crane) ? CraneHelper.takeLoaded(crane) : null;
            if (left != null) n.carrying = left; // still on the hook: keep it, abortSwap warns about it
            n.abortSwap(revert == null ? "crane change failed." : revert);
        }
    }

    /** Column coordinates for chat messages (marks are removed as they finish, so a running number would repeat). */
    private String label() {
        if (n.swapIdx < n.cols.size()) {
            int[] c = n.cols.get(n.swapIdx);
            return c[0] + "," + c[1] + "," + c[2];
        }
        return "?";
    }

    private void skip(String why) {
        n.tell("Column " + label() + ": " + why + " Skipping.");
        n.swapIdx++;
        timer = 0;
        fails = 0;
    }

    @Override
    public void resetTask() { n.task = "idle"; }
}
