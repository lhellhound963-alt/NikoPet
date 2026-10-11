package com.nikopet;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/** Invisible light source that follows Niko while she holds a torch/lamp. Removes itself if she is gone. */
public class BlockNikoLight extends Block {
    public BlockNikoLight() {
        super(Material.air);
        setBlockName("nikoLight");
        setLightLevel(1.0F);
        setLightOpacity(0);
    }

    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) { return null; }
    @Override public boolean canCollideCheck(int meta, boolean liquid) { return false; }
    @Override public int tickRate(World w) { return 20; }

    @Override
    public void onBlockAdded(World w, int x, int y, int z) {
        if (!w.isRemote) w.scheduleBlockUpdate(x, y, z, this, tickRate(w));
    }

    @Override
    public void updateTick(World w, int x, int y, int z, Random r) {
        if (w.isRemote) return;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - 2, y - 2, z - 2, x + 3, y + 3, z + 3);
        if (w.getEntitiesWithinAABB(EntityNiko.class, box).isEmpty()) w.setBlockToAir(x, y, z);
        else w.scheduleBlockUpdate(x, y, z, this, tickRate(w));
    }
}
