package com.realistictrees.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.realistictrees.ModEntities;
import com.realistictrees.entity.CustomTreeEntity;
import com.realistictrees.entity.TreeSpecies;
import com.realistictrees.event.TreeReplacer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /tree place <0-5>        - поставить дерево-модель перед собой
 * /tree replace <радиус>   - заменить ванильные деревья в чанках вокруг вас (для старых миров)
 */
public class TreeCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tree")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("place")
                        .then(Commands.argument("type", IntegerArgumentType.integer(0, TreeSpecies.MODEL_COUNT - 1))
                                .executes(ctx -> place(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "type")))))
                .then(Commands.literal("replace")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                .executes(ctx -> replace(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private int place(CommandSourceStack src, int type) {
        ServerLevel level = src.getLevel();
        Vec3 p = src.getPosition();
        CustomTreeEntity tree = new CustomTreeEntity(ModEntities.CUSTOM_TREE.get(), level);
        tree.setPos(p.x, p.y, p.z);
        tree.setYRot(level.random.nextFloat() * 360f);
        tree.setTreeType(type);
        level.addFreshEntity(tree);
        src.sendSuccess(() -> Component.literal("Tree " + type + ": " + TreeSpecies.MODELS[type]), true);
        return 1;
    }

    private int replace(CommandSourceStack src, int radius) {
        ServerLevel level = src.getLevel();
        ChunkPos center = new ChunkPos(BlockPos.containing(src.getPosition()));
        int n = TreeReplacer.queueAround(level, center, radius);
        src.sendSuccess(() -> Component.literal("Scanning " + n + " chunks for trees..."), true);
        return n;
    }
}
