package com.nikopet;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class GuiHandler implements IGuiHandler {
    @Override
    public Object getServerGuiElement(int id, EntityPlayer p, World w, int x, int y, int z) {
        Entity e = w.getEntityByID(x);
        return e instanceof EntityNiko ? new ContainerNiko(p.inventory, (EntityNiko) e) : null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer p, World w, int x, int y, int z) {
        Entity e = w.getEntityByID(x);
        return e instanceof EntityNiko ? new GuiNiko(p.inventory, (EntityNiko) e) : null;
    }
}
