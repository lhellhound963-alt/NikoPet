package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Method;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Reflection bridge to HBM's Sedna guns (no compile-time dependency on HBM). */
public class GunHelper {
    private static boolean tried, ok;
    private static Class<?> gunClass, magClass;
    private static Method handle, getConfig;
    private static Object[] KEYS; // PRIMARY, SECONDARY, TERTIARY, RELOAD
    public static String lastError = "none";

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void init() {
        if (tried) return;
        tried = true;
        try {
            gunClass = Class.forName("com.hbm.items.weapon.sedna.ItemGunBaseNT");
            magClass = Class.forName("com.hbm.items.weapon.sedna.mags.IMagazine");
            Class keyEnum = Class.forName("com.hbm.handler.HbmKeybinds$EnumKeybind");
            KEYS = new Object[] { Enum.valueOf(keyEnum, "GUN_PRIMARY"), Enum.valueOf(keyEnum, "GUN_SECONDARY"),
                    Enum.valueOf(keyEnum, "GUN_TERTIARY"), Enum.valueOf(keyEnum, "RELOAD") };
            handle = gunClass.getMethod("handleKeybind", EntityLivingBase.class, IInventory.class, ItemStack.class, keyEnum, boolean.class);
            getConfig = gunClass.getMethod("getConfig", ItemStack.class, int.class);
            ok = true;
            FMLLog.info("[NikoPet] HBM gun bridge ready");
        } catch (Throwable t) {
            lastError = "bridge init: " + t;
            FMLLog.warning("[NikoPet] HBM gun bridge unavailable: %s", String.valueOf(t));
        }
    }

    public static boolean bridgeOk() { init(); return ok; }

    public static boolean isGun(ItemStack s) {
        init();
        return ok && s != null && gunClass.isInstance(s.getItem());
    }

    /** Drives the gun's internal state machine (the game normally does this for the holder). */
    public static void tick(EntityNiko n, ItemStack g) {
        try {
            g.getItem().onUpdate(g, n.worldObj, n, 0, true);
        } catch (Throwable t) {
            err("onUpdate", t);
        }
    }

    /** Same as HBM's own mob AI: every tick, tell the gun which keys are held. */
    public static void setKeys(EntityNiko n, ItemStack g, boolean primary, boolean reload) {
        init();
        if (!ok) return;
        try {
            boolean[] st = { primary, false, false, reload };
            for (int i = 0; i < 4; i++) handle.invoke(g.getItem(), n, n.inv, g, KEYS[i], st[i]);
        } catch (Throwable t) {
            err("handleKeybind", t);
        }
    }

    /** Rounds currently loaded, or -1 if unknown. */
    public static int ammo(EntityNiko n, ItemStack g) {
        init();
        if (!ok) return -1;
        try {
            Object cfg = getConfig.invoke(g.getItem(), g, 0);
            if (cfg == null) return -1;
            Method gr = cfg.getClass().getMethod("getReceivers", ItemStack.class);
            gr.setAccessible(true);
            Object[] rec = (Object[]) gr.invoke(cfg, g);
            if (rec == null || rec.length == 0) return -1;
            Method gm = rec[0].getClass().getMethod("getMagazine", ItemStack.class);
            gm.setAccessible(true);
            Object mag = gm.invoke(rec[0], g);
            if (mag == null) return -1;
            Method ga = magClass.getMethod("getAmount", ItemStack.class, IInventory.class);
            ga.setAccessible(true);
            return ((Integer) ga.invoke(mag, g, n.inv)).intValue();
        } catch (Throwable t) {
            err("ammo", t);
            return -1;
        }
    }

    private static void err(String where, Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        String msg = where + ": " + c;
        if (!msg.equals(lastError)) {
            lastError = msg;
            FMLLog.warning("[NikoPet] gun call failed: %s", msg);
            c.printStackTrace();
        }
    }
}
