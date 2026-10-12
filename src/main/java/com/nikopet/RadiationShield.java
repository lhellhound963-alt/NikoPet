package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Method;
import net.minecraft.item.ItemStack;

/**
 * HBM's radiation reduction (HazmatRegistry.getResistance) only reads a PLAYER's armor, so a mob like Niko takes full
 * radiation even in a hazmat suit. Here we apply the same formula (10^-resistance) to what she absorbs each tick.
 */
public class RadiationShield {
    private static boolean tried, ok;
    private static Method itemRes, getRad, setRad;
    public static String lastError = "none";

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> hz = Class.forName("com.hbm.handler.HazmatRegistry");
            itemRes = hz.getMethod("getResistance", ItemStack.class);
            Class<?> lp = Class.forName("com.hbm.extprop.HbmLivingProps");
            getRad = lp.getMethod("getRadiation", net.minecraft.entity.EntityLivingBase.class);
            setRad = lp.getMethod("setRadiation", net.minecraft.entity.EntityLivingBase.class, float.class);
            ok = true;
            FMLLog.info("[NikoPet] radiation shield ready");
        } catch (Throwable t) {
            lastError = "init: " + t;
            FMLLog.warning("[NikoPet] radiation shield unavailable: %s", String.valueOf(t));
        }
    }

    /** Sum of resistance of the 4 armor pieces (equipment slots 1-4). */
    public static double resistance(EntityNiko n) {
        init();
        if (!ok) return 0.0D;
        double r = 0.0D;
        try {
            for (int i = 1; i <= 4; i++) {
                ItemStack s = n.getEquipmentInSlot(i);
                if (s != null) r += ((Number) itemRes.invoke(null, s)).doubleValue();
            }
        } catch (Throwable t) {
            lastError = "resistance: " + t;
        }
        return r;
    }

    private float lastRad = -1.0F;

    public void tick(EntityNiko n) {
        init();
        if (!ok) return;
        try {
            float rad = ((Number) getRad.invoke(null, n)).floatValue();
            if (lastRad >= 0.0F && rad > lastRad + 0.0001F) {
                double res = resistance(n);
                if (res > 0.0D) {
                    rad = lastRad + (float) ((rad - lastRad) * Math.pow(10.0D, -res));
                    setRad.invoke(null, n, Float.valueOf(rad));
                }
            }
            lastRad = rad;
        } catch (Throwable t) {
            lastError = "tick: " + t;
        }
    }

    public static String describe(EntityNiko n) {
        init();
        if (!ok) return "rad shield unavailable";
        double res = resistance(n);
        return "rad resistance " + Math.round(res * 100.0D) / 100.0D + " (blocks " + Math.round((1.0D - Math.pow(10.0D, -res)) * 100.0D) + "%)";
    }
}
