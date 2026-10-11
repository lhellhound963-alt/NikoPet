package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Read-only RBMK monitoring + owner-commanded AZ-5, via reflection (no compile dependency on HBM). */
public class RbmkHelper {
    private static boolean tried, ok;
    private static Class<?> base, manual;
    private static Method maxHeat, setTarget;
    private static Field heat, level, startingLevel, levelAny;
    private static Class<?> ctrlBase;
    public static String lastError = "none";

    public static class Result {
        public int columns, rods, controls, allControls;
        public double worstRatio, ctrlSum, maxCtrlLevel;
        public int wx, wy, wz;
    }

    private static Field find(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) { /* keep looking up the hierarchy */ }
        }
        throw new NoSuchFieldException(name);
    }

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            base = Class.forName("com.hbm.tileentity.machine.rbmk.TileEntityRBMKBase");
            manual = Class.forName("com.hbm.tileentity.machine.rbmk.TileEntityRBMKControlManual");
            maxHeat = base.getMethod("maxHeat");
            heat = find(base, "heat");
            level = find(manual, "level");
            startingLevel = find(manual, "startingLevel");
            setTarget = manual.getMethod("setTarget", double.class);
            try {
                ctrlBase = Class.forName("com.hbm.tileentity.machine.rbmk.TileEntityRBMKControl");
                levelAny = find(ctrlBase, "level");
            } catch (Throwable t) { ctrlBase = null; levelAny = null; }
            ok = true;
            FMLLog.info("[NikoPet] RBMK bridge ready");
        } catch (Throwable t) {
            lastError = "init: " + t;
            FMLLog.warning("[NikoPet] RBMK bridge unavailable: %s", String.valueOf(t));
        }
    }

    public static boolean ready() { init(); return ok; }

    /** Scan all RBMK columns in a box around (x,y,z). Returns null if the bridge is unavailable. */
    public static Result scan(World w, int x, int y, int z, int rx, int ry, int rz) {
        init();
        if (!ok) return null;
        Result r = new Result();
        try {
            for (Object o : w.loadedTileEntityList) {
                TileEntity te = (TileEntity) o;
                if (!base.isInstance(te)) continue;
                if (Math.abs(te.xCoord - x) > rx || Math.abs(te.yCoord - y) > ry || Math.abs(te.zCoord - z) > rz) continue;
                r.columns++;
                String n = te.getClass().getSimpleName();
                if (n.contains("RBMKRod")) r.rods++;
                if (manual.isInstance(te)) { r.controls++; r.ctrlSum += level.getDouble(te); }
                if (ctrlBase != null && ctrlBase.isInstance(te)) {
                    r.allControls++;
                    double lv = levelAny.getDouble(te);
                    if (lv > r.maxCtrlLevel) r.maxCtrlLevel = lv;
                }
                double mh = ((Double) maxHeat.invoke(te)).doubleValue();
                if (mh > 0) {
                    double ratio = heat.getDouble(te) / mh;
                    if (ratio > r.worstRatio) { r.worstRatio = ratio; r.wx = te.xCoord; r.wy = te.yCoord; r.wz = te.zCoord; }
                }
            }
        } catch (Throwable t) {
            lastError = "scan: " + t;
            FMLLog.warning("[NikoPet] RBMK scan failed: %s", String.valueOf(t));
            return null;
        }
        return r;
    }

    /** AZ-5: drive every manual control rod in the box to fully inserted. Returns number of rods, or -1 on failure. */
    public static int scram(World w, int x, int y, int z, int rx, int ry, int rz) {
        init();
        if (!ok) return -1;
        int n = 0;
        try {
            for (Object o : w.loadedTileEntityList) {
                TileEntity te = (TileEntity) o;
                if (!manual.isInstance(te)) continue;
                if (Math.abs(te.xCoord - x) > rx || Math.abs(te.yCoord - y) > ry || Math.abs(te.zCoord - z) > rz) continue;
                startingLevel.setDouble(te, level.getDouble(te));
                setTarget.invoke(te, 0.0D);
                te.markDirty();
                n++;
            }
        } catch (Throwable t) {
            lastError = "scram: " + t;
            FMLLog.warning("[NikoPet] RBMK scram failed: %s", String.valueOf(t));
            return -1;
        }
        return n;
    }

    public static String describe(Result r) {
        if (r == null) return "RBMK bridge unavailable";
        if (r.columns == 0) return "no RBMK nearby";
        String avg = r.controls > 0 ? Math.round(r.ctrlSum / r.controls * 100.0D) + "%" : "n/a";
        return "RBMK: " + r.columns + " columns (" + r.rods + " fuel), " + r.controls + " manual rods avg " + avg
                + ", hottest " + Math.round(r.worstRatio * 100.0D) + "% of max at " + r.wx + "," + r.wy + "," + r.wz;
    }
}
