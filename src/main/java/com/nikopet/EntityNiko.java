package com.nikopet;

import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class EntityNiko extends EntityTameable {
    /** 0 = hand, 1 = boots, 2 = legs, 3 = chest, 4 = helmet, 5..26 = storage (ammo, items) */
    public final InventoryBasic inv = new InventoryBasic("Niko", false, 27);

    public EntityNiko(World w) {
        super(w);
        setSize(0.6F, 1.8F);
        getNavigator().setAvoidsWater(true);
        tasks.addTask(1, new EntityAISwimming(this));
        tasks.addTask(2, aiSit);
        tasks.addTask(3, new EntityAIPickupItems(this));
        tasks.addTask(4, new EntityAIFollowOwner(this, 1.1D, 8.0F, 2.0F));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(7, new EntityAILookIdle(this));
    }

    @Override
    public boolean isAIEnabled() { return true; }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
    }

    @Override
    public EntityAgeable createChild(EntityAgeable other) { return null; }

    @Override
    public boolean isBreedingItem(ItemStack s) { return false; }

    @Override
    public boolean interact(EntityPlayer p) {
        ItemStack s = p.inventory.getCurrentItem();
        boolean food = s != null && s.getItem() instanceof ItemFood;
        if (!isTamed()) {
            if (food) {
                consume(p, s);
                if (!worldObj.isRemote) {
                    if (rand.nextInt(3) == 0) {
                        setTamed(true);
                        func_152115_b(p.getUniqueID().toString()); // set owner
                        setHealth(20.0F);
                        aiSit.setSitting(false);
                        worldObj.setEntityState(this, (byte) 7);
                    } else {
                        worldObj.setEntityState(this, (byte) 6);
                    }
                }
                return true;
            }
        } else if (func_152114_e(p)) { // is owner
            if (food && getHealth() < getMaxHealth()) {
                heal(((ItemFood) s.getItem()).func_150905_g(s));
                consume(p, s);
                return true;
            }
            if (!worldObj.isRemote) {
                if (p.isSneaking()) {
                    p.openGui(NikoMod.instance, 0, worldObj, getEntityId(), 0, 0);
                } else {
                    aiSit.setSitting(!isSitting());
                    isJumping = false;
                    getNavigator().clearPathEntity();
                }
            }
            return true;
        }
        return super.interact(p);
    }

    private void consume(EntityPlayer p, ItemStack s) {
        if (!p.capabilities.isCreativeMode && --s.stackSize <= 0) {
            p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        }
    }

    /** Put a stack into storage slots 5..26. Returns the leftover (or null). */
    public ItemStack addToInventory(ItemStack in) {
        ItemStack s = in.copy();
        for (int i = 5; i < inv.getSizeInventory() && s.stackSize > 0; i++) {
            ItemStack e = inv.getStackInSlot(i);
            if (e == null) {
                inv.setInventorySlotContents(i, s.copy());
                s.stackSize = 0;
            } else if (e.isItemEqual(s) && ItemStack.areItemStackTagsEqual(e, s)) {
                int m = Math.min(e.getMaxStackSize() - e.stackSize, s.stackSize);
                e.stackSize += m;
                s.stackSize -= m;
            }
        }
        return s.stackSize > 0 ? s : null;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            // GUI slots 0..4 are what she wears / holds
            for (int i = 0; i < 5; i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != getEquipmentInSlot(i)) setCurrentItemOrArmor(i, s);
            }
        }
    }

    @Override
    public void onDeath(DamageSource src) {
        if (!worldObj.isRemote) {
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != null) entityDropItem(s, 0.5F);
                inv.setInventorySlotContents(i, null);
            }
            for (int i = 0; i < 5; i++) setCurrentItemOrArmor(i, null);
        }
        super.onDeath(src);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound t) {
        super.writeEntityToNBT(t);
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null) {
                NBTTagCompound c = new NBTTagCompound();
                c.setByte("Slot", (byte) i);
                s.writeToNBT(c);
                l.appendTag(c);
            }
        }
        t.setTag("NikoInv", l);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound t) {
        super.readEntityFromNBT(t);
        NBTTagList l = t.getTagList("NikoInv", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            int slot = c.getByte("Slot") & 255;
            if (slot < inv.getSizeInventory()) inv.setInventorySlotContents(slot, ItemStack.loadItemStackFromNBT(c));
        }
    }
}
