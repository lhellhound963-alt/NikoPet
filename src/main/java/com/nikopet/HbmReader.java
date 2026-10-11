package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Read-only bridge to HBM machines: electricity (HE) and fluid / steam tanks.
 * Uses reflection only (no compile dependency on HBM). Never changes anything in the world.
 */
public class HbmReader {
    private static boolean tried, ok;
    private static Class<?> energyC, fluidUserC, recvC, sendC, tankC;
    private static Method getPower, getMaxPower, getAllTanks, getRecvTanks, getSendTanks;
    private static Method tankType, tankFill, tankMax, typeLocal, typeName;
    private static Method armorHazmat, radGet;
    public static String lastError = "none";

    public static class Tank {
        public String name;
        public long fill, max;
    }

    public static class Reading {
        public String name;
        public int x, y, z;
        public boolean hasPower;
        public long power, maxPower;
        public final List<Tank> tanks = new ArrayList<Tank>();
        public double dist;
    }

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            energyC = Class.forName("api.hbm.energymk2.IEnergyHandlerMK2");
            getPower = energyC.getMethod("getPower");
            getMaxPower = energyC.getMethod("getMaxPower");
            tankC = Class.forName("com.hbm.inventory.fluid.tank.FluidTank");
            tankType = tankC.getMethod("getTankType");
            tankFill = tankC.getMethod("getFill");
            tankMax = tankC.getMethod("getMaxFill");
            Class<?> typeC = Class.forName("com.hbm.inventory.fluid.FluidType");
            try { typeLocal = typeC.getMethod("getLocalizedName"); } catch (Throwable t) { typeLocal = null; }
            try { typeName = typeC.getMethod("getName"); } catch (Throwable t) { typeName = null; }
            try {
                fluidUserC = Class.forName("api.hbm.fluidmk2.IFluidUserMK2");
                getAllTanks = fluidUserC.getMethod("getAllTanks");
            } catch (Throwable t) { fluidUserC = null; }
            try {
                recvC = Class.forName("api.hbm.fluidmk2.IFluidStandardReceiverMK2");
                getRecvTanks = recvC.getMethod("getReceivingTanks");
            } catch (Throwable t) { recvC = null; }
            try {
                sendC = Class.forName("api.hbm.fluidmk2.IFluidStandardSenderMK2");
                getSendTanks = sendC.getMethod("getSendingTanks");
            } catch (Throwable t) { sendC = null; }
            ok = true;
            FMLLog.info("[NikoPet] HBM power/fluid reader ready");
        } catch (Throwable t) {
            lastError = "init: " + t;
            FMLLog.warning("[NikoPet] HBM power/fluid reader unavailable: %s", String.valueOf(t));
        }
        try {
            Class<?> au = Class.forName("com.hbm.util.ArmorUtil");
            armorHazmat = au.getMethod("checkForHazmat", EntityLivingBase.class);
            Class<?> lp = Class.forName("com.hbm.extprop.HbmLivingProps");
            radGet = lp.getMethod("getRadiation", EntityLivingBase.class);
        } catch (Throwable t) {
            armorHazmat = null;
            radGet = null;
        }
    }

    public static boolean ready() { init(); return ok; }

    private static void addTanks(Reading r, Object te, Method m) {
        try {
            Object arr = m.invoke(te);
            if (arr == null) return;
            Object[] tanks = (Object[]) arr;
            for (int i = 0; i < tanks.length; i++) {
                Object t = tanks[i];
                if (t == null) continue;
                long max = ((Number) tankMax.invoke(t)).longValue();
                long fill = ((Number) tankFill.invoke(t)).longValue();
                if (max <= 0) continue;
                String n = fluidName(tankType.invoke(t));
                if (n == null || n.equalsIgnoreCase("none")) continue;
                boolean dup = false;
                for (Tank o : r.tanks) if (o.name.equals(n) && o.fill == fill && o.max == max) dup = true;
                if (dup) continue;
                Tank k = new Tank();
                k.name = n; k.fill = fill; k.max = max;
                r.tanks.add(k);
            }
        } catch (Throwable t) {
            lastError = "tanks: " + (t.getCause() != null ? t.getCause() : t);
        }
    }

    private static String fluidName(Object type) {
        if (type == null) return null;
        try {
            if (typeLocal != null) {
                String s = String.valueOf(typeLocal.invoke(type));
                if (s != null && s.length() > 0 && !s.startsWith("hbmfluid.")) return s;
            }
        } catch (Throwable t) { /* fall through */ }
        try {
            if (typeName != null) return String.valueOf(typeName.invoke(type));
        } catch (Throwable t) { /* fall through */ }
        return String.valueOf(type);
    }

    private static String machineName(TileEntity te) {
        try {
            Block b = te.getBlockType();
            if (b != null) {
                String s = b.getLocalizedName();
                if (s != null && s.length() > 0 && !s.startsWith("tile.")) return s;
            }
        } catch (Throwable t) { /* fall through */ }
        return te.getClass().getSimpleName().replace("TileEntity", "");
    }

    /** All HBM machines (that expose power or tanks) inside the box. Returns null if the bridge is unavailable. */
    public static List<Reading> scan(World w, int x, int y, int z, int rx, int ry, int rz) {
        init();
        if (!ok) return null;
        List<Reading> out = new ArrayList<Reading>();
        try {
            for (Object o : new ArrayList<Object>(w.loadedTileEntityList)) {
                TileEntity te = (TileEntity) o;
                if (Math.abs(te.xCoord - x) > rx || Math.abs(te.yCoord - y) > ry || Math.abs(te.zCoord - z) > rz) continue;
                boolean pw = energyC.isInstance(te);
                boolean fl = (fluidUserC != null && fluidUserC.isInstance(te))
                        || (recvC != null && recvC.isInstance(te)) || (sendC != null && sendC.isInstance(te));
                if (!pw && !fl) continue;
                Reading r = new Reading();
                r.name = machineName(te);
                r.x = te.xCoord; r.y = te.yCoord; r.z = te.zCoord;
                r.dist = Math.sqrt((te.xCoord - x) * (double) (te.xCoord - x) + (te.yCoord - y) * (double) (te.yCoord - y)
                        + (te.zCoord - z) * (double) (te.zCoord - z));
                if (pw) {
                    try {
                        r.power = ((Number) getPower.invoke(te)).longValue();
                        r.maxPower = ((Number) getMaxPower.invoke(te)).longValue();
                        r.hasPower = r.maxPower > 0;
                    } catch (Throwable t) {
                        lastError = "power: " + (t.getCause() != null ? t.getCause() : t);
                    }
                }
                if (fl) {
                    if (fluidUserC != null && fluidUserC.isInstance(te)) addTanks(r, te, getAllTanks);
                    if (recvC != null && recvC.isInstance(te)) addTanks(r, te, getRecvTanks);
                    if (sendC != null && sendC.isInstance(te)) addTanks(r, te, getSendTanks);
                }
                if (r.hasPower || !r.tanks.isEmpty()) out.add(r);
            }
        } catch (Throwable t) {
            lastError = "scan: " + t;
            FMLLog.warning("[NikoPet] power/fluid scan failed: %s", String.valueOf(t));
            return null;
        }
        return out;
    }

    public static String fmtHE(long v) {
        double d = v;
        if (v >= 1000000000L) return String.format(Locale.ROOT, "%.2fG HE", d / 1.0E9D);
        if (v >= 1000000L) return String.format(Locale.ROOT, "%.2fM HE", d / 1.0E6D);
        if (v >= 1000L) return String.format(Locale.ROOT, "%.2fk HE", d / 1.0E3D);
        return v + " HE";
    }

    public static String fmtMB(long v) { return String.format(Locale.ROOT, "%,d", Long.valueOf(v)); }

    private static boolean coolant(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.contains("water") || l.contains("coolant");
    }

    /** Short room summary: total power, summed fluids, low-coolant alerts. Empty list if nothing readable. */
    public static List<String> summary(List<Reading> rs) {
        List<String> out = new ArrayList<String>();
        if (rs == null || rs.isEmpty()) return out;
        long pw = 0, pmax = 0;
        int pn = 0;
        Map<String, long[]> fluids = new LinkedHashMap<String, long[]>();
        List<String> alerts = new ArrayList<String>();
        for (Reading r : rs) {
            if (r.hasPower) { pw += r.power; pmax += r.maxPower; pn++; }
            for (Tank t : r.tanks) {
                long[] a = fluids.get(t.name);
                if (a == null) { a = new long[2]; fluids.put(t.name, a); }
                a[0] += t.fill; a[1] += t.max;
                if (coolant(t.name) && t.fill * 10L < t.max && alerts.size() < 3) {
                    alerts.add("LOW " + t.name + " in " + r.name + " at " + r.x + "," + r.y + "," + r.z
                            + " (" + fmtMB(t.fill) + "/" + fmtMB(t.max) + " mB)");
                }
            }
        }
        if (pn > 0) out.add("Power: " + fmtHE(pw) + " / " + fmtHE(pmax) + " in " + pn + " machines");
        if (!fluids.isEmpty()) {
            StringBuilder sb = new StringBuilder("Fluids: ");
            int shown = 0;
            for (Map.Entry<String, long[]> e : fluids.entrySet()) {
                if (shown >= 6) { sb.append("..."); break; }
                if (shown > 0) sb.append(", ");
                sb.append(e.getKey()).append(' ').append(fmtMB(e.getValue()[0])).append('/').append(fmtMB(e.getValue()[1])).append(" mB");
                shown++;
            }
            out.add(sb.toString());
        }
        out.addAll(alerts);
        return out;
    }

    /** Detailed readout of the nearest machines (one line each). */
    public static List<String> detail(List<Reading> rs, int limit) {
        List<String> out = new ArrayList<String>();
        if (rs == null) return out;
        List<Reading> sorted = new ArrayList<Reading>(rs);
        for (int i = 1; i < sorted.size(); i++) {
            Reading k = sorted.get(i);
            int j = i - 1;
            while (j >= 0 && sorted.get(j).dist > k.dist) { sorted.set(j + 1, sorted.get(j)); j--; }
            sorted.set(j + 1, k);
        }
        for (int i = 0; i < sorted.size() && i < limit; i++) {
            Reading r = sorted.get(i);
            StringBuilder sb = new StringBuilder(r.name).append(" @").append(r.x).append(',').append(r.y).append(',').append(r.z).append(": ");
            boolean first = true;
            for (Tank t : r.tanks) {
                if (!first) sb.append(" | ");
                first = false;
                sb.append(t.name).append(' ').append(fmtMB(t.fill)).append('/').append(fmtMB(t.max)).append(" mB");
            }
            if (r.hasPower) {
                if (!first) sb.append(" | ");
                sb.append(fmtHE(r.power)).append(" / ").append(fmtHE(r.maxPower));
            }
            out.add(sb.toString());
        }
        return out;
    }

    /** Radiation + hazmat status for Niko herself. */
    public static String radiation(EntityLivingBase e) {
        init();
        if (radGet == null) return "radiation: n/a";
        try {
            float rad = ((Number) radGet.invoke(null, e)).floatValue();
            String hz = "";
            if (armorHazmat != null) hz = ((Boolean) armorHazmat.invoke(null, e)).booleanValue() ? ", hazmat suit ON" : ", no hazmat suit";
            return "radiation " + Math.round(rad * 10.0F) / 10.0F + " RAD" + hz;
        } catch (Throwable t) {
            return "radiation: n/a";
        }
    }
}
