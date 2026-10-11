package com.nikopet;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class GuiNiko extends GuiContainer {
    private static final ResourceLocation BG = new ResourceLocation("textures/gui/container/generic_54.png");

    public GuiNiko(InventoryPlayer pi, EntityNiko n) {
        super(new ContainerNiko(pi, n));
        xSize = 176;
        ySize = 168;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString("Niko", 8, 6, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float pt, int mx, int my) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(BG);
        int x = (width - xSize) / 2, y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, xSize, 3 * 18 + 17);
        drawTexturedModalRect(x, y + 3 * 18 + 17, 0, 126, xSize, 96);
    }
}
