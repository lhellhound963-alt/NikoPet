package com.nikopet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

public class ContainerNiko extends Container {
    private final EntityNiko niko;

    public ContainerNiko(InventoryPlayer pi, EntityNiko n) {
        niko = n;
        addSlotToContainer(new Slot(n.inv, 0, 8, 18)); // hand: gun / lantern / tool
        for (int i = 1; i <= 4; i++) addSlotToContainer(new ArmorSlot(n.inv, i, 8 + i * 18, 18));
        for (int i = 5; i < 27; i++) addSlotToContainer(new Slot(n.inv, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(pi, c + r * 9 + 9, 8 + c * 18, 85 + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(pi, c, 8 + c * 18, 143));
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return niko.isEntityAlive() && p.getDistanceSqToEntity(niko) < 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int idx) {
        ItemStack r = null;
        Slot s = (Slot) inventorySlots.get(idx);
        if (s != null && s.getHasStack()) {
            ItemStack st = s.getStack();
            r = st.copy();
            boolean ok = idx < 27 ? mergeItemStack(st, 27, 63, true) : mergeItemStack(st, 0, 27, false);
            if (!ok) return null;
            if (st.stackSize == 0) s.putStack(null); else s.onSlotChanged();
        }
        return r;
    }

    static class ArmorSlot extends Slot {
        private final int equipIndex;
        ArmorSlot(IInventory inv, int i, int x, int y) { super(inv, i, x, y); equipIndex = i; }
        @Override public int getSlotStackLimit() { return 1; }
        @Override public boolean isItemValid(ItemStack s) {
            if (s == null) return false;
            int type = 4 - equipIndex; // 0 helmet, 1 chest, 2 legs, 3 boots
            if (s.getItem() instanceof ItemArmor && ((ItemArmor) s.getItem()).armorType == type) return true;
            try {
                // covers modded armor / masks that declare their slot via Forge's hook
                return s.getItem().isValidArmor(s, type, null);
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
