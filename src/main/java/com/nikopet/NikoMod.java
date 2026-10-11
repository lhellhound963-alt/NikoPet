package com.nikopet;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = "nikopet", name = "Niko Pet", version = "0.1")
public class NikoMod {
    @Mod.Instance("nikopet")
    public static NikoMod instance;

    public static Block lightBlock;

    @SidedProxy(clientSide = "com.nikopet.ClientProxy", serverSide = "com.nikopet.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        lightBlock = new BlockNikoLight();
        GameRegistry.registerBlock(lightBlock, "niko_light");
        int id = EntityRegistry.findGlobalUniqueEntityId();
        // registers entity + spawn egg (purple / red)
        EntityRegistry.registerGlobalEntityID(EntityNiko.class, "Niko", id, 0x6B4FA0, 0xA02828);
        EntityRegistry.registerModEntity(EntityNiko.class, "Niko", 0, this, 64, 1, true);
        // natural spawn in every biome that spawns normal animals
        for (BiomeGenBase b : BiomeGenBase.getBiomeGenArray()) {
            if (b != null && !b.getSpawnableList(EnumCreatureType.creature).isEmpty()) {
                EntityRegistry.addSpawn(EntityNiko.class, 4, 1, 2, EnumCreatureType.creature, b);
            }
        }
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
        MinecraftForge.EVENT_BUS.register(new NikoEvents());
        proxy.registerRenderers();
    }
}
