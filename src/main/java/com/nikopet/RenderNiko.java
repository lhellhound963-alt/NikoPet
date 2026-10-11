package com.nikopet;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

public class RenderNiko extends RenderBiped {
    private static final ResourceLocation TEX = new ResourceLocation("nikopet", "textures/entity/niko.png");

    public RenderNiko() {
        // 64x64 texture so a modern skin maps correctly
        super(new ModelBiped(0.0F, 0.0F, 64, 64), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }
}
