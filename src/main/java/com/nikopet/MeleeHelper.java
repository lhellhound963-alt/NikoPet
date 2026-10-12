package com.nikopet;

import com.google.common.collect.Multimap;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

/** Works out whether an item is a melee weapon: swords/tools, plus any HBM or other modded item that adds attack damage. */
public class MeleeHelper {
    public static boolean isMelee(ItemStack s) {
        if (s == null) return false;
        if (GunHelper.isGun(s)) return false;
        if (s.getItem() instanceof ItemSword || s.getItem() instanceof ItemTool) return true;
        try {
            Multimap m = s.getAttributeModifiers();
            return m != null && m.containsKey(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName());
        } catch (Throwable t) {
            return false;
        }
    }
}
