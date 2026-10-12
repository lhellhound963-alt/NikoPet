package com.nikopet;

import net.minecraft.block.Block;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class EntityNiko extends EntityTameable {
    /** 0 = hand, 1 = boots, 2 = legs, 3 = chest, 4 = helmet, 5..26 = storage (ammo, items) */
    public final InventoryBasic inv = new InventoryBasic("Niko", false, 27);

    public String task = "idle";
    private final RadiationShield shield = new RadiationShield();
    public final List<int[]> points = new ArrayList<int[]>();
    public final List<String> findings = new ArrayList<String>();
    public int patrolIndex;
    private int lastRbmkWarn = -10000;
    public boolean patrolling;
    // owner-commanded fuel rod change
    public int[] pool;
    public final List<int[]> cols = new ArrayList<int[]>();
    public boolean swapping;
    public int swapIdx;
    public ItemStack carrying;
    private int lx, ly, lz;
    private boolean lit;
    private int lastAct = -100;
    public int lastTargetId = -1;
    public int lastTargetTell = -10000;

    public EntityNiko(World w) {
        super(w);
        setSize(0.6F, 1.8F);
        getNavigator().setAvoidsWater(true);
        getNavigator().setBreakDoors(true);
        tasks.addTask(2, new EntityAIOpenDoor(this, true));
        tasks.addTask(1, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIPatrol(this));
        tasks.addTask(1, new EntityAIRodSwap(this));
        tasks.addTask(1, new EntityAIShootGun(this));
        targetTasks.addTask(1, new EntityAIOwnerHurtByTarget(this));
        targetTasks.addTask(2, new EntityAIOwnerHurtTarget(this));
        targetTasks.addTask(3, new EntityAINearestAttackableTarget(this, EntityMob.class, 0, true) {
            @Override
            public boolean shouldExecute() {
                return EntityNiko.this.isTamed() && GunHelper.isGun(EntityNiko.this.getEquipmentInSlot(0)) && super.shouldExecute();
            }
        });
        tasks.addTask(2, aiSit);
        tasks.addTask(3, new EntityAISleepInBed(this));
        tasks.addTask(4, new EntityAIPickupItems(this));
        tasks.addTask(5, new EntityAIFollowOwner(this, 1.1D, 8.0F, 2.0F));
        tasks.addTask(6, new EntityAIWander(this, 0.8D));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20, Byte.valueOf((byte) 0));
    }

    public boolean isNikoSleeping() { return dataWatcher.getWatchableObjectByte(20) != 0; }

    public void setNikoSleeping(boolean b) { dataWatcher.updateObject(20, Byte.valueOf((byte) (b ? 1 : 0))); }

    public void startPatrol(EntityPlayer p) {
        if (points.isEmpty()) {
            p.addChatMessage(new ChatComponentText("<Niko> No checkpoints. Right-click blocks with paper first."));
            return;
        }
        findings.clear();
        patrolIndex = 0;
        patrolling = true;
        aiSit.setSitting(false);
        p.addChatMessage(new ChatComponentText("<Niko> Okay, checking " + points.size() + " rooms."));
    }

    public void finishPatrol() {
        patrolling = false;
        task = "idle";
        if (getOwner() instanceof EntityPlayer) {
            EntityPlayer o = (EntityPlayer) getOwner();
            o.addChatMessage(new ChatComponentText("<Niko> Inspection done:"));
            for (String f : findings) o.addChatMessage(new ChatComponentText("<Niko> " + f));
        }
    }

    /** Background watch: warn the owner if an RBMK column nearby gets too hot. */
    private void monitorReactor() {
        if (!RbmkHelper.ready() || ticksExisted - lastRbmkWarn < 600) return;
        RbmkHelper.Result r = RbmkHelper.scan(worldObj, MathHelper.floor_double(posX), MathHelper.floor_double(posY),
                MathHelper.floor_double(posZ), 32, 24, 32);
        if (r != null && r.columns > 0 && r.worstRatio >= 0.85D) {
            lastRbmkWarn = ticksExisted;
            tell("WARNING! RBMK column at " + r.wx + "," + r.wy + "," + r.wz + " is at "
                    + Math.round(r.worstRatio * 100.0D) + "% of max heat. Right-click me with redstone dust for AZ-5.");
        }
    }

    public int findRodSlot() {
        for (int i = 5; i < inv.getSizeInventory(); i++) if (RodHelper.isRodStack(inv.getStackInSlot(i))) return i;
        return -1;
    }

    /** Owner command (blaze rod): change the fuel rods in the marked columns. */
    public void startSwap(EntityPlayer p) {
        if (!RodHelper.ready()) { tell("Rod bridge unavailable: " + RodHelper.lastError); return; }
        if (pool == null) { tell("Mark the storage pool first: right-click it with a feather."); return; }
        if (cols.isEmpty() && carrying == null) { tell("Mark the columns first: right-click each with flint."); return; }
        int rods = 0;
        for (int i = 5; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (RodHelper.isRodStack(s)) rods += s.stackSize;
        }
        if (rods < cols.size()) { tell("I only carry " + rods + " fresh rods for " + cols.size() + " columns."); return; }
        swapIdx = 0;
        swapping = true;
        aiSit.setSitting(false);
        tell("Okay, changing " + cols.size() + " columns. Hot rods only if the control rods are fully in (AZ-5).");
    }

    public void finishSwap() {
        swapping = false;
        task = "idle";
        tell("Fuel rod change finished.");
    }

    public void abortSwap(String why) {
        swapping = false;
        task = "idle";
        tell("Stopped: " + why);
        if (carrying != null) {
            aiSit.setSitting(true); // stay put instead of wandering with the old rod
            tell("WARNING: I am still holding the OLD (possibly hot) rod at " + (int) posX + "," + (int) posY + "," + (int) posZ
                    + ". I am sitting. Fix the pool, then right-click me with a blaze rod to finish delivering it.");
        }
    }

    /** Adds the room line plus power/fluid lines to the patrol findings. */
    public void inspectInto(int[] p, boolean reached, String why) {
        String line = inspect(p, reached);
        if (!reached && why != null) line = line.replace("(could not reach)", "(could not reach: " + why + ")");
        findings.add(line);
        List<HbmReader.Reading> rs = HbmReader.scan(worldObj, p[0], p[1], p[2], 8, 6, 8);
        for (String l : HbmReader.summary(rs)) findings.add("   " + l);
    }

    public String inspect(int[] p, boolean reached) {
        int hbm = 0, furn = 0, burn = 0, other = 0;
        for (Object o : worldObj.loadedTileEntityList) {
            TileEntity te = (TileEntity) o;
            if (Math.abs(te.xCoord - p[0]) > 8 || Math.abs(te.yCoord - p[1]) > 4 || Math.abs(te.zCoord - p[2]) > 8) continue;
            if (te.getClass().getName().startsWith("com.hbm")) hbm++;
            else if (te instanceof TileEntityFurnace) {
                furn++;
                if (((TileEntityFurnace) te).isBurning()) burn++;
            } else other++;
        }
        int light = worldObj.getBlockLightValue(p[0], p[1], p[2]);
        RbmkHelper.Result rb = RbmkHelper.scan(worldObj, p[0], p[1], p[2], 8, 16, 8);
        String reactor = (rb != null && rb.columns > 0) ? " | " + RbmkHelper.describe(rb) : "";
        return "Room " + (patrolIndex + 1) + (reached ? "" : " (could not reach)") + ": HBM machines " + hbm
                + ", furnaces " + furn + " (" + burn + " burning), other tile blocks " + other
                + ", light " + light + (light < 7 ? " - DARK" : "") + reactor;
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
            if (ticksExisted - lastAct < 5) return true; // ignore duplicate click events
            lastAct = ticksExisted;
            if (food && getHealth() < getMaxHealth()) {
                heal(((ItemFood) s.getItem()).func_150905_g(s));
                consume(p, s);
                return true;
            }
            if (s != null && s.getItem() == Items.clock && !p.isSneaking()) {
                if (!worldObj.isRemote) startPatrol(p);
                return true;
            }
            if (s != null && s.getItem() == Items.redstone && !p.isSneaking()) {
                if (!worldObj.isRemote) {
                    int n = RbmkHelper.scram(worldObj, MathHelper.floor_double(posX), MathHelper.floor_double(posY),
                            MathHelper.floor_double(posZ), 32, 24, 32);
                    tell(n < 0 ? "AZ-5 failed: " + RbmkHelper.lastError
                            : n == 0 ? "No RBMK control rods within 32 blocks."
                            : "AZ-5! Inserting " + n + " control rods.");
                }
                return true;
            }
            if (s != null && s.getItem() == Items.blaze_rod && !p.isSneaking()) {
                if (!worldObj.isRemote) {
                    if (swapping) abortSwap("cancelled by you."); else startSwap(p);
                }
                return true;
            }
            if (s != null && s.getItem() == Items.compass && !p.isSneaking()) {
                if (!worldObj.isRemote) readMachines(p);
                return true;
            }
            if (s != null && s.getItem() == Items.stick && !p.isSneaking()) {
                if (!worldObj.isRemote) report(p);
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

    /** Compass + right-click Niko: read power (HE) and fluids/steam of the nearest HBM machines. */
    private void readMachines(EntityPlayer p) {
        if (!HbmReader.ready()) {
            tell("Power/fluid reader unavailable: " + HbmReader.lastError);
            return;
        }
        List<HbmReader.Reading> rs = HbmReader.scan(worldObj, MathHelper.floor_double(posX), MathHelper.floor_double(posY),
                MathHelper.floor_double(posZ), 16, 8, 16);
        List<String> lines = HbmReader.detail(rs, 6);
        if (lines.isEmpty()) {
            tell("No HBM machine with power or tanks within 16 blocks.");
            return;
        }
        tell("Machines nearby (" + rs.size() + " found, nearest " + lines.size() + "):");
        for (String l : lines) tell(l);
        for (String l : HbmReader.summary(rs)) tell(l);
    }

    public void tell(String s) {
        if (getOwner() instanceof EntityPlayer) ((EntityPlayer) getOwner()).addChatMessage(new ChatComponentText("<Niko> " + s));
    }

    /** Status report: right-click Niko with a stick. */
    private void report(EntityPlayer p) {
        ItemStack hand = getEquipmentInSlot(0);
        int stored = 0;
        for (int i = 5; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null) stored += s.stackSize;
        }
        String state = isSitting() ? "resting (sitting)" : task;
        p.addChatMessage(new ChatComponentText("<Niko> Status: " + state
                + " | HP " + (int) getHealth() + "/" + (int) getMaxHealth()
                + " | holding: " + (hand == null ? "nothing" : hand.getDisplayName())
                + " | stored items: " + stored));
        String gun;
        if (!GunHelper.bridgeOk()) gun = "HBM bridge FAILED";
        else if (!GunHelper.isGun(hand)) gun = "no HBM gun in hand";
        else gun = "gun ready, loaded rounds " + GunHelper.ammo(this, hand);
        EntityLivingBase tg = getAttackTarget();
        p.addChatMessage(new ChatComponentText("<Niko> Gun: " + gun + " | target: "
                + (tg == null ? "none" : tg.getCommandSenderName()) + " | sitting: " + isSitting()
                + " | last gun error: " + GunHelper.lastError));
        p.addChatMessage(new ChatComponentText("<Niko> Body: " + HbmReader.radiation(this) + " | " + RadiationShield.describe(this)));
        p.addChatMessage(new ChatComponentText("<Niko> Rod change: pool " + (pool == null ? "not set" : pool[0] + "," + pool[1] + "," + pool[2])
                + " | columns marked " + cols.size() + " | " + (swapping ? "working" : "idle")
                + " | holding old rod: " + (carrying != null) + " | last rod error: " + RodHelper.lastError));
    }

    private boolean holdsLight() {
        ItemStack s = getEquipmentInSlot(0);
        if (s == null) return false;
        Item it = s.getItem();
        Block b = Block.getBlockFromItem(it);
        if (b == net.minecraft.init.Blocks.torch || b == net.minecraft.init.Blocks.glowstone
                || b == net.minecraft.init.Blocks.lit_pumpkin) return true;
        String name = Item.itemRegistry.getNameForObject(it);
        return name != null && (name.contains("lamp") || name.contains("lantern") || name.contains("torch"));
    }

    private void clearLight() {
        if (lit) {
            if (worldObj.getBlock(lx, ly, lz) == NikoMod.lightBlock) worldObj.setBlockToAir(lx, ly, lz);
            lit = false;
        }
    }

    private void updateLight() {
        if (holdsLight()) {
            int x = MathHelper.floor_double(posX), y = MathHelper.floor_double(posY + 1.0D), z = MathHelper.floor_double(posZ);
            if (!(lit && x == lx && y == ly && z == lz)) {
                clearLight();
                if (worldObj.getBlock(x, y, z).isAir(worldObj, x, y, z)) {
                    worldObj.setBlock(x, y, z, NikoMod.lightBlock);
                    lx = x; ly = y; lz = z;
                    lit = true;
                }
            }
        } else {
            clearLight();
        }
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
            updateLight();
            shield.tick(this);
            if (ticksExisted % 100 == 0 && isTamed() && !patrolling) monitorReactor();
            ItemStack gun = getEquipmentInSlot(0);
            if (gun != null && GunHelper.isGun(gun)) GunHelper.tick(this, gun);
        }
    }

    @Override
    public void onDeath(DamageSource src) {
        if (!worldObj.isRemote) {
            clearLight();
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != null) entityDropItem(s, 0.5F);
                inv.setInventorySlotContents(i, null);
            }
            for (int i = 0; i < 5; i++) setCurrentItemOrArmor(i, null);
            if (carrying != null) { entityDropItem(carrying, 0.5F); carrying = null; }
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
        NBTTagList pl = new NBTTagList();
        for (int[] p : points) pl.appendTag(new NBTTagIntArray(p));
        t.setTag("NikoPoints", pl);
        if (pool != null) t.setIntArray("NikoPool", pool);
        NBTTagList cl = new NBTTagList();
        for (int[] c : cols) cl.appendTag(new NBTTagIntArray(c));
        t.setTag("NikoCols", cl);
        if (carrying != null) {
            NBTTagCompound cc = new NBTTagCompound();
            carrying.writeToNBT(cc);
            t.setTag("NikoCarry", cc);
        }
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
        NBTTagList pl = t.getTagList("NikoPoints", 11);
        points.clear();
        for (int i = 0; i < pl.tagCount(); i++) points.add(pl.func_150306_c(i));
        pool = t.hasKey("NikoPool") ? t.getIntArray("NikoPool") : null;
        if (pool != null && pool.length != 3) pool = null;
        NBTTagList cl = t.getTagList("NikoCols", 11);
        cols.clear();
        for (int i = 0; i < cl.tagCount(); i++) cols.add(cl.func_150306_c(i));
        carrying = t.hasKey("NikoCarry") ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("NikoCarry")) : null;
        swapping = false;
    }
}
