package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Drives HBM's RBMK Crane Console (TileEntityCraneConsole) by reflection. HBM only exposes its controls to
 * OpenComputers / the GUI, so we do exactly what those controls do: slide posFront/posLeft toward the column
 * (world position = center - front*posFront - left*posLeft, the same formula the crane uses), set goesDown to
 * start a lower-and-lift cycle, and hand the crane / take from the crane its loadedItem.
 */
public class CraneHelper {
    private static boolean tried, ok;
    private static Class<?> cc;
    private static Field centerX, centerZ, posFront, posLeft, goesDown, setUp, progress, loadedItem, hasLoaded, loadedHeat, loadedEnr, speed;
    public static String lastError = "none";

    private static Field find(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) { /* look in the parent */ }
        }
        throw new NoSuchFieldException(name);
    }

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            cc = Class.forName("com.hbm.tileentity.machine.rbmk.TileEntityCraneConsole");
            centerX = find(cc, "centerX");
            centerZ = find(cc, "centerZ");
            posFront = find(cc, "posFront");
            posLeft = find(cc, "posLeft");
            goesDown = find(cc, "goesDown");
            setUp = find(cc, "setUpCrane");
            progress = find(cc, "progress");
            loadedItem = find(cc, "loadedItem");
            hasLoaded = find(cc, "hasLoaded");
            loadedHeat = find(cc, "loadedHeat");
            loadedEnr = find(cc, "loadedEnrichment");
            speed = find(cc, "speed");
            ok = true;
            FMLLog.info("[NikoPet] crane bridge ready");
        } catch (Throwable t) {
            lastError = "init: " + t;
            FMLLog.warning("[NikoPet] crane bridge unavailable: %s", String.valueOf(t));
        }
    }

    public static boolean ready() { init(); return ok; }

    /** Nearest crane console that has been set up, within r blocks (flat) of x,z. */
    public static TileEntity find(World w, int x, int z, int r) {
        init();
        if (!ok) return null;
        TileEntity best = null;
        double bd = (double) r * r;
        try {
            for (Object o : w.loadedTileEntityList) {
                TileEntity te = (TileEntity) o;
                if (!cc.isInstance(te) || !setUp.getBoolean(te)) continue;
                double dx = te.xCoord - x, dz = te.zCoord - z, d = dx * dx + dz * dz;
                if (d <= bd) { bd = d; best = te; }
            }
        } catch (Throwable t) {
            lastError = "find: " + t;
        }
        return best;
    }

    public static String describe(World w, int x, int z) {
        if (!ready()) return "crane bridge unavailable";
        TileEntity c = find(w, x, z, 128);
        return c == null ? "no set-up crane console within 128 blocks" : "crane console at " + c.xCoord + "," + c.yCoord + "," + c.zCoord;
    }

    /** One tick of movement toward the block column at world x,z. Returns true once the crane is over it. */
    public static boolean moveToward(TileEntity te, int tx, int tz) {
        try {
            ForgeDirection f = ForgeDirection.getOrientation(te.getBlockMetadata() - 10);
            ForgeDirection l = f.getRotation(ForgeDirection.DOWN);
            int dx = tx - centerX.getInt(te), dz = tz - centerZ.getInt(te);
            double wantF = -(dx * f.offsetX + dz * f.offsetZ);
            double wantL = -(dx * l.offsetX + dz * l.offsetZ);
            double sp = speed.getDouble(null);
            if (sp <= 0.0D || sp > 1.0D) sp = 0.05D;
            double curF = posFront.getDouble(te), curL = posLeft.getDouble(te);
            double nf = curF + Math.max(-sp, Math.min(sp, wantF - curF));
            double nl = curL + Math.max(-sp, Math.min(sp, wantL - curL));
            posFront.setDouble(te, nf);
            posLeft.setDouble(te, nl);
            return Math.abs(wantF - nf) < 0.001D && Math.abs(wantL - nl) < 0.001D;
        } catch (Throwable t) {
            lastError = "move: " + t;
            return false;
        }
    }

    /** Lower the hook, load/unload at the column below, lift back up. */
    public static void startCycle(TileEntity te) {
        try { goesDown.setBoolean(te, true); } catch (Throwable t) { lastError = "cycle: " + t; }
    }

    public static boolean busy(TileEntity te) {
        try { return goesDown.getBoolean(te) || progress.getDouble(te) > 0.0D; } catch (Throwable t) { return false; }
    }

    public static String state(TileEntity te) {
        try {
            ItemStack s = (ItemStack) loadedItem.get(te);
            return "goesDown=" + goesDown.getBoolean(te) + ", progress=" + Math.round(progress.getDouble(te) * 100.0D) / 100.0D
                    + ", holding=" + (s == null ? "nothing" : s.getDisplayName());
        } catch (Throwable t) {
            return "state unreadable";
        }
    }

    public static boolean hasItem(TileEntity te) {
        try { return loadedItem.get(te) != null; } catch (Throwable t) { return false; }
    }

    public static ItemStack takeLoaded(TileEntity te) {
        try {
            ItemStack s = (ItemStack) loadedItem.get(te);
            loadedItem.set(te, null);
            hasLoaded.setBoolean(te, false);
            te.markDirty();
            return s;
        } catch (Throwable t) {
            lastError = "take: " + t;
            return null;
        }
    }

    public static void give(TileEntity te, ItemStack s) {
        try {
            loadedItem.set(te, s);
            hasLoaded.setBoolean(te, true);
            loadedHeat.setDouble(te, 0.0D);
            loadedEnr.setDouble(te, 0.0D);
            te.markDirty();
        } catch (Throwable t) {
            lastError = "give: " + t;
        }
    }
}
