package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * RBMK fuel rod swap via HBM's own crane interface (IRBMKLoadable: canLoad / load / canUnload / provideNext / unload).
 * Reflection only. Everything here is only called when the owner explicitly commands a swap.
 */
public class RodHelper {
    /** Refuse to touch a column or rod hotter than this (degrees C). Room temperature is about 20. */
    public static final double MAX_TOUCH_HEAT = 60.0D;

    private static boolean tried, ok;
    private static Class<?> loadC, rodItemC;
    private static Method canLoad, load, canUnload, provideNext, unload, coreHeat, hullHeat;
    private static Field colHeat;
    private static Method colMax;
    /** Hot rods may be moved to the pool only if the column is below this fraction of its max heat. */
    public static final double HOT_COLUMN_LIMIT = 0.5D;
    /** ...and the reactor's manual control rods are inserted at least this far (0 = fully in). */
    public static final double SCRAM_LEVEL = 0.05D;
    public static String lastError = "none";

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            loadC = Class.forName("com.hbm.tileentity.machine.rbmk.IRBMKLoadable");
            canLoad = loadC.getMethod("canLoad", ItemStack.class);
            load = loadC.getMethod("load", ItemStack.class);
            canUnload = loadC.getMethod("canUnload");
            provideNext = loadC.getMethod("provideNext");
            unload = loadC.getMethod("unload");
            rodItemC = Class.forName("com.hbm.items.machine.ItemRBMKRod");
            try { coreHeat = rodItemC.getMethod("getCoreHeat", ItemStack.class); } catch (Throwable t) { coreHeat = null; }
            try { hullHeat = rodItemC.getMethod("getHullHeat", ItemStack.class); } catch (Throwable t) { hullHeat = null; }
            Class<?> base = Class.forName("com.hbm.tileentity.machine.rbmk.TileEntityRBMKBase");
            for (Class<?> k = base; k != null && colHeat == null; k = k.getSuperclass()) {
                try { colHeat = k.getDeclaredField("heat"); colHeat.setAccessible(true); } catch (NoSuchFieldException e) { /* up */ }
            }
            try { colMax = base.getMethod("maxHeat"); } catch (Throwable t) { colMax = null; }
            ok = true;
            FMLLog.info("[NikoPet] RBMK rod-swap bridge ready");
        } catch (Throwable t) {
            lastError = "init: " + t;
            FMLLog.warning("[NikoPet] RBMK rod-swap bridge unavailable: %s", String.valueOf(t));
        }
    }

    public static boolean ready() { init(); return ok; }

    public static boolean isRodStack(ItemStack s) {
        init();
        return ok && s != null && rodItemC.isInstance(s.getItem());
    }

    /** The loadable tile at the clicked block (a lid or column top may sit above the real tile). */
    public static TileEntity findLoadable(World w, int x, int y, int z) {
        init();
        if (!ok) return null;
        int[] dys = { 0, -1, -2, -3, 1 };
        for (int i = 0; i < dys.length; i++) {
            TileEntity te = w.getTileEntity(x, y + dys[i], z);
            if (te != null && loadC.isInstance(te)) return te;
        }
        return null;
    }

    /** HBM Spent Fuel Pool Drum: a plain 12-slot inventory (not an IRBMKLoadable). */
    public static boolean isDrum(TileEntity te) {
        return te instanceof IInventory && te.getClass().getSimpleName().contains("WasteDrum");
    }

    /** Spent fuel pool at/near the clicked block: Spent Fuel Pool Drum, or an RBMK Storage Column. */
    public static TileEntity findPool(World w, int x, int y, int z) {
        init();
        int[] dys = { 0, -1, 1, -2 };
        for (int i = 0; i < dys.length; i++) {
            TileEntity te = w.getTileEntity(x, y + dys[i], z);
            if (te == null) continue;
            if (isDrum(te)) return te;
            if (ok && loadC.isInstance(te) && isStorage(te)) return te;
        }
        return null;
    }

    private static int drumSlot(TileEntity pool, ItemStack s) {
        IInventory inv = (IInventory) pool;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i) == null && inv.isItemValidForSlot(i, s)) return i;
        }
        return -1;
    }

    public static boolean isStorage(TileEntity te) { return te != null && te.getClass().getSimpleName().contains("Storage"); }

    public static boolean isRodColumn(TileEntity te) { return te != null && !isStorage(te) && te.getClass().getSimpleName().contains("Rod"); }

    private static double staticOrInstance(Method m, ItemStack s) throws Exception {
        Object r = Modifier.isStatic(m.getModifiers()) ? m.invoke(null, s) : m.invoke(s.getItem(), s);
        return ((Number) r).doubleValue();
    }

    /** Hottest of core/hull heat of a rod item. Unreadable counts as too hot (refuse). */
    public static double rodHeat(ItemStack s) {
        double h = 0.0D;
        boolean any = false;
        try {
            if (coreHeat != null) { h = Math.max(h, staticOrInstance(coreHeat, s)); any = true; }
            if (hullHeat != null) { h = Math.max(h, staticOrInstance(hullHeat, s)); any = true; }
        } catch (Throwable t) {
            lastError = "rodHeat: " + (t.getCause() != null ? t.getCause() : t);
            return Double.MAX_VALUE;
        }
        return any ? h : Double.MAX_VALUE;
    }

    public static double columnHeat(TileEntity te) {
        try {
            if (colHeat != null) return colHeat.getDouble(te);
        } catch (Throwable t) { lastError = "columnHeat: " + t; }
        return Double.MAX_VALUE;
    }

    private static boolean can(Method m, TileEntity te, ItemStack s) throws Exception {
        return ((Boolean) m.invoke(te, s)).booleanValue();
    }

    /** Can the storage pool at this tile accept the stack right now? */
    public static boolean poolAccepts(TileEntity pool, ItemStack s) {
        if (isDrum(pool)) return drumSlot(pool, s) >= 0;
        try { return can(canLoad, pool, s); } catch (Throwable t) { lastError = "canLoad: " + t; return false; }
    }

    public static boolean poolLoad(TileEntity pool, ItemStack s) {
        if (isDrum(pool)) {
            int slot = drumSlot(pool, s);
            if (slot < 0) return false;
            ItemStack one = s.copy();
            one.stackSize = 1;
            ((IInventory) pool).setInventorySlotContents(slot, one);
            pool.markDirty();
            return true;
        }
        try {
            if (!can(canLoad, pool, s)) return false;
            load.invoke(pool, s);
            pool.markDirty();
            return true;
        } catch (Throwable t) { lastError = "poolLoad: " + t; return false; }
    }

    /**
     * Take the old rod out of the column and put a fresh one in. Refuses (returns an error text, nothing changed)
     * if anything is unsafe. On success returns null and result[0] holds the removed rod.
     */
    /** Same safety checks as swapAtColumn, but changes nothing. Returns an error text or null if it is OK to go ahead. */
    public static final double ONLINE_ROD_LIMIT = 900.0D;      // HBM's autoloader itself refuses rods at 1000 C hull heat
    public static final double ONLINE_REACTOR_LIMIT = 0.70D;   // refuel online only while the hottest column is below this share of its max

    public static String preflight(TileEntity col, TileEntity pool, ItemStack fresh) { return preflight(col, pool, fresh, false); }

    public static String preflight(TileEntity col, TileEntity pool, ItemStack fresh, boolean online) {
        init();
        if (!ok) return "rod bridge unavailable: " + lastError;
        try {
            if (!((Boolean) canUnload.invoke(col)).booleanValue()) return "this column has no rod I can take out";
            ItemStack old = (ItemStack) provideNext.invoke(col);
            if (old == null) return "this column has no rod I can take out";
            double ch = columnHeat(col);
            double rh = rodHeat(old);
            if (online) {
                if (rh >= ONLINE_ROD_LIMIT) return "rod is too hot even for the crane (" + Math.round(rh) + " C)";
                RbmkHelper.Result rr0 = RbmkHelper.scan(col.getWorldObj(), col.xCoord, col.yCoord, col.zCoord, 32, 24, 32);
                if (rr0 == null || rr0.columns == 0) return "I cannot read the reactor state, so no online refuelling";
                if (rr0.worstRatio > ONLINE_REACTOR_LIMIT) return "reactor too hot to refuel while running (" + Math.round(rr0.worstRatio * 100.0D) + "% of max, limit " + Math.round(ONLINE_REACTOR_LIMIT * 100.0D) + "%)";
            } else if (ch > MAX_TOUCH_HEAT || rh > MAX_TOUCH_HEAT) {
                double mh = 0.0D;
                try { if (colMax != null) mh = ((Number) colMax.invoke(col)).doubleValue(); } catch (Throwable t) { mh = 0.0D; }
                if (mh <= 0.0D || ch > mh * HOT_COLUMN_LIMIT)
                    return "column too hot to touch (" + Math.round(ch) + " C" + (mh > 0 ? ", " + Math.round(ch / mh * 100.0D) + "% of max" : "") + "), let it cool first";
                RbmkHelper.Result rr = RbmkHelper.scan(col.getWorldObj(), col.xCoord, col.yCoord, col.zCoord, 32, 24, 32);
                if (rr == null || rr.allControls == 0) return "rod is hot (" + Math.round(rh) + " C) and I cannot see all the control rods to confirm the reactor is shut down";
                if (rr.maxCtrlLevel > SCRAM_LEVEL) return "rod is hot (" + Math.round(rh) + " C): at least one control rod is still out (" + Math.round(rr.maxCtrlLevel * 100.0D) + "%), insert all control rods first (AZ-5)";
            }
            if (!poolAccepts(pool, old)) return "storage pool has no room for this rod";
            return null;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            lastError = "preflight: " + c;
            return "internal error: " + c;
        }
    }

    /** Would this (now empty) column accept that fresh rod? */
    public static boolean canLoadInto(TileEntity col, ItemStack fresh) {
        init();
        if (!ok) return false;
        try {
            ItemStack one = fresh.copy();
            one.stackSize = 1;
            return can(canLoad, col, one);
        } catch (Throwable t) {
            lastError = "canLoadInto: " + t;
            return false;
        }
    }

    public static String swapAtColumn(TileEntity col, TileEntity pool, ItemStack fresh, ItemStack[] result) {
        init();
        if (!ok) return "rod bridge unavailable: " + lastError;
        try {
            if (!((Boolean) canUnload.invoke(col)).booleanValue()) return "this column has no rod I can take out";
            ItemStack old = (ItemStack) provideNext.invoke(col);
            if (old == null) return "this column has no rod I can take out";
            old = old.copy();
            double ch = columnHeat(col);
            double rh = rodHeat(old);
            boolean hot = ch > MAX_TOUCH_HEAT || rh > MAX_TOUCH_HEAT;
            if (hot) {
                // hot fuel goes to the pool to cool, but only from a column that is not near meltdown and a scrammed reactor
                double mh = 0.0D;
                try { if (colMax != null) mh = ((Number) colMax.invoke(col)).doubleValue(); } catch (Throwable t) { mh = 0.0D; }
                if (mh <= 0.0D || ch > mh * HOT_COLUMN_LIMIT)
                    return "column too hot to touch (" + Math.round(ch) + " C" + (mh > 0 ? ", " + Math.round(ch / mh * 100.0D) + "% of max" : "") + "), let it cool first";
                RbmkHelper.Result rr = RbmkHelper.scan(col.getWorldObj(), col.xCoord, col.yCoord, col.zCoord, 32, 24, 32);
                if (rr == null || rr.allControls == 0) return "rod is hot (" + Math.round(rh) + " C) and I cannot see all the control rods to confirm the reactor is shut down";
                if (rr.maxCtrlLevel > SCRAM_LEVEL) return "rod is hot (" + Math.round(rh) + " C): at least one control rod is still out (" + Math.round(rr.maxCtrlLevel * 100.0D) + "%), insert all control rods first (AZ-5)";
            }
            if (!poolAccepts(pool, old)) return "storage pool has no room for this rod";
            ItemStack one = fresh.copy();
            one.stackSize = 1;
            unload.invoke(col);
            if (!can(canLoad, col, one)) {
                load.invoke(col, old); // put it back exactly as it was
                col.markDirty();
                return "column refused the new rod, old rod put back";
            }
            load.invoke(col, one);
            col.markDirty();
            result[0] = old;
            return null;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            lastError = "swap: " + c;
            FMLLog.warning("[NikoPet] rod swap failed: %s", String.valueOf(c));
            return "internal error: " + c;
        }
    }
}
