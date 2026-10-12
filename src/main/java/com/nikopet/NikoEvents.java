package com.nikopet;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public class NikoEvents {
    private EntityNiko nearestOwned(PlayerInteractEvent e, EntityPlayer p) {
        List l = e.world.getEntitiesWithinAABB(EntityNiko.class, p.boundingBox.expand(64.0D, 32.0D, 64.0D));
        EntityNiko best = null;
        for (Object o : l) {
            EntityNiko n = (EntityNiko) o;
            if (n.isTamed() && n.func_152114_e(p) && (best == null || n.getDistanceToEntity(p) < best.getDistanceToEntity(p))) best = n;
        }
        return best;
    }

    /** Feather + right-click = storage pool, flint + right-click = RBMK column to change (sneak = clear). */
    private void markRodSpot(PlayerInteractEvent e, EntityPlayer p, ItemStack s) {
        EntityNiko n = nearestOwned(e, p);
        if (n == null) return;
        boolean pool = s.getItem() == Items.feather;
        if (p.isSneaking()) {
            if (pool) n.pool = null; else n.cols.clear();
            p.addChatMessage(new ChatComponentText("<Niko> " + (pool ? "Pool mark" : "Column marks") + " cleared."));
            e.setCanceled(true);
            return;
        }
        if (!RodHelper.ready()) {
            p.addChatMessage(new ChatComponentText("<Niko> Rod bridge unavailable: " + RodHelper.lastError));
            e.setCanceled(true);
            return;
        }
        net.minecraft.tileentity.TileEntity te = pool ? RodHelper.findPool(e.world, e.x, e.y, e.z) : RodHelper.findLoadable(e.world, e.x, e.y, e.z);
        if (te == null) {
            p.addChatMessage(new ChatComponentText(pool ? "<Niko> That is not a Spent Fuel Pool Drum or RBMK storage column."
                    : "<Niko> That is not an RBMK fuel rod column."));
        } else if (pool) {
            n.pool = new int[] { te.xCoord, te.yCoord, te.zCoord };
            p.addChatMessage(new ChatComponentText("<Niko> Storage pool saved" + (RodHelper.isDrum(te) ? " (Spent Fuel Pool Drum)." : ".")));
        } else if (RodHelper.isStorage(te) || !RodHelper.isRodColumn(te)) {
            p.addChatMessage(new ChatComponentText("<Niko> That is not a fuel rod column."));
        } else if (n.cols.size() >= 8) {
            p.addChatMessage(new ChatComponentText("<Niko> Max 8 columns."));
        } else {
            boolean dup = false;
            for (int[] c : n.cols) if (c[0] == te.xCoord && c[1] == te.yCoord && c[2] == te.zCoord) dup = true;
            if (!dup) {
                n.cols.add(new int[] { te.xCoord, te.yCoord, te.zCoord });
                p.addChatMessage(new ChatComponentText("<Niko> Column " + n.cols.size() + " saved."));
            }
        }
        e.setCanceled(true);
    }

    /** Paper + right-click block = add inspection checkpoint. Sneak + paper = clear checkpoints. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack s = p.getCurrentEquippedItem();
        if (s == null) return;
        if (s.getItem() == Items.feather || s.getItem() == Items.flint) { markRodSpot(e, p, s); return; }
        if (s.getItem() != Items.paper) return;
        List l = e.world.getEntitiesWithinAABB(EntityNiko.class, p.boundingBox.expand(64.0D, 32.0D, 64.0D));
        EntityNiko best = null;
        for (Object o : l) {
            EntityNiko n = (EntityNiko) o;
            if (n.isTamed() && n.func_152114_e(p) && (best == null || n.getDistanceToEntity(p) < best.getDistanceToEntity(p))) best = n;
        }
        if (best == null) return;
        if (p.isSneaking()) {
            best.points.clear();
            p.addChatMessage(new ChatComponentText("<Niko> Checkpoints cleared."));
        } else if (!best.points.isEmpty() && Math.abs(best.points.get(best.points.size() - 1)[0] - e.x) <= 1
                && Math.abs(best.points.get(best.points.size() - 1)[1] - (e.y + 1)) <= 1
                && Math.abs(best.points.get(best.points.size() - 1)[2] - e.z) <= 1) {
            // duplicate click event on the same block: ignore
        } else if (best.points.size() >= 8) {
            p.addChatMessage(new ChatComponentText("<Niko> Max 8 checkpoints."));
        } else {
            best.points.add(new int[] { e.x, e.y + 1, e.z });
            p.addChatMessage(new ChatComponentText("<Niko> Checkpoint " + best.points.size() + " saved."));
        }
        e.setCanceled(true);
    }
}
