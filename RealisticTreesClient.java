package com.realistictrees.client;

import com.realistictrees.ModEntities;
import com.realistictrees.RealisticTrees;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Клиентская часть: рендерер деревьев и экран настроек (Mods -> Realistic Trees -> Config). */
@Mod(value = RealisticTrees.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = RealisticTrees.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RealisticTreesClient {

    public RealisticTreesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CUSTOM_TREE.get(), TreeEntityRenderer::new);
    }
}
