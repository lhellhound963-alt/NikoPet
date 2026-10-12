package com.nikopet;

import com.google.common.collect.Multimap;
import java.util.Collection;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

/** Works out whether an item is a melee weapon (swords/tools, plus any HBM or modded item that adds attack damage) and how hard it hits. */
public class MeleeHelper {
    public static String lastError = "none";

    private static String key() { return SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(); }

    public static boolean isMelee(ItemStack s) {
        if (s == null) return false;
        if (GunHelper.isGun(s)) return false;
        if (s.getItem() instanceof ItemSword || s.getItem() instanceof ItemTool) return true;
        try {
            Multimap m = s.getAttributeModifiers();
            return m != null && m.containsKey(key());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Base damage plus the item's own attack damage modifiers. */
    public static double damage(ItemStack s, double base) {
        double d = base;
        if (s == null) return d;
        try {
            Multimap m = s.getAttributeModifiers();
            if (m == null) return d;
            Collection c = m.get(key());
            for (Object o : c) {
                AttributeModifier am = (AttributeModifier) o;
                if (am.getOperation() == 0) d += am.getAmount();
            }
        } catch (Throwable t) {
            lastError = "damage: " + t;
        }
        return d;
    }
}
