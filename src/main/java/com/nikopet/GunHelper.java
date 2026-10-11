package com.nikopet;

import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Method;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Reflection bridge to HBM's Sedna guns (no compile-time dependency on HBM). */
public class GunHelper {
    private static boolean tried, ok, failLogged;
    private static Class<?> gunClass;
    private static Method handle;
    private static Object PRIMARY, RELOAD;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void init() {
        if (tried) return;
        tried = true;
        try {
            gunClass = Class.forName("com.hbm.items.weapon.sedna.ItemGunBaseNT");
            Class keyEnum = Class.forName("com.hbm.handler.HbmKeybinds$EnumKeybind");
            PRIMARY = Enum.valueOf(keyEnum, "GUN_PRIMARY");
            RELOAD = Enum.valueOf(keyEnum, "RELOAD");
            handle = gunClass.getMethod("handleKeybind", EntityLivingBase.class, IInventory.class, ItemStack.class, keyEnum, boolean.class);
            ok = true;
            FMLLog.info("[NikoPet] HBM gun bridge ready");
        } catch (Throwable t) {
            FMLLog.warning("[NikoPet] HBM gun bridge unavailable: %s", String.valueOf(t));
        }
    }

    public static boolean isGun(ItemStack s) {
        init();
        return ok && s != null && gunClass.isInstance(s.getItem());
    }

    /** Drives the gun's internal state machine (the game normally does this for the holder). */
    public static void tick(EntityNiko n, ItemStack g) {
        try {
            g.getItem().onUpdate(g, n.worldObj, n, 0, true);
        } catch (Throwable t) {
            logOnce("onUpdate", t);
        }
    }

    public static void key(EntityNiko n, ItemStack g, boolean reload, boolean down) {
        init();
        if (!ok) return;
        try {
            handle.invoke(g.getItem(), n, n.inv, g, reload ? RELOAD : PRIMARY, down);
        } catch (Throwable t) {
            logOnce("handleKeybind", t);
        }
    }

    private static void logOnce(String where, Throwable t) {
        if (failLogged) return;
        failLogged = true;
        Throwable c = t.getCause() != null ? t.getCause() : t;
        FMLLog.warning("[NikoPet] gun call failed in %s: %s", where, c.toString());
        c.printStackTrace();
    }
}
