package com.nikopet;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public class NikoEvents {
    /** Paper + right-click block = add inspection checkpoint. Sneak + paper = clear checkpoints. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.world.isRemote) return;
        EntityPlayer p = e.entityPlayer;
        ItemStack s = p.getCurrentEquippedItem();
        if (s == null || s.getItem() != Items.paper) return;
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
        } else if (best.points.size() >= 8) {
            p.addChatMessage(new ChatComponentText("<Niko> Max 8 checkpoints."));
        } else {
            best.points.add(new int[] { e.x, e.y + 1, e.z });
            p.addChatMessage(new ChatComponentText("<Niko> Checkpoint " + best.points.size() + " saved."));
        }
        e.setCanceled(true);
    }
}
