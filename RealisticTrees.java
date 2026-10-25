package com.realistictrees;

import com.realistictrees.command.TreeCommands;
import com.realistictrees.event.SaplingReplaceHandler;
import com.realistictrees.event.TreeReplacer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(RealisticTrees.MODID)
public class RealisticTrees {
    public static final String MODID = "realistictrees";

    public RealisticTrees(IEventBus modEventBus, ModContainer container) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        container.registerConfig(ModConfig.Type.COMMON, TreeConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, TreeConfig.CLIENT_SPEC);
        NeoForge.EVENT_BUS.register(new TreeReplacer());
        NeoForge.EVENT_BUS.register(new SaplingReplaceHandler());
        NeoForge.EVENT_BUS.register(new TreeCommands());
    }
}
