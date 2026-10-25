package com.realistictrees.event;

import com.realistictrees.ModEntities;
import com.realistictrees.TreeConfig;
import com.realistictrees.entity.CustomTreeEntity;
import com.realistictrees.entity.TreeSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/** Дерево, выросшее из саженца (или от костной муки), тоже становится реалистичным. */
public class SaplingReplaceHandler {

    @SubscribeEvent
    public void onSaplingGrow(BlockGrowFeatureEvent event) {
        if (!TreeConfig.COMMON.replaceSaplings.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block sapling = state.getBlock();
        ResourceLocation saplingId = BuiltInRegistries.BLOCK.getKey(sapling);
        String path = saplingId.getPath();
        if (!path.endsWith("_sapling") && !path.equals("mangrove_propagule")) return;

        String wood = path.endsWith("_sapling") ? path.substring(0, path.length() - 8) : "mangrove";
        ResourceLocation logId = ResourceLocation.fromNamespaceAndPath(saplingId.getNamespace(), wood + "_log");
        if (BuiltInRegistries.BLOCK.getOptional(logId).isEmpty()) {
            logId = ResourceLocation.withDefaultNamespace("oak_log");   // азалия и т.п.
            wood = "oak";
        }

        // 2x2 саженцы -> большое дерево
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        boolean big = false;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        BlockState air = Blocks.AIR.defaultBlockState();
        outer:
        for (int ox = -1; ox <= 0; ox++) {
            for (int oz = -1; oz <= 0; oz++) {
                BlockPos a = pos.offset(ox, 0, oz);
                if (level.getBlockState(a).getBlock() == sapling
                        && level.getBlockState(a.east()).getBlock() == sapling
                        && level.getBlockState(a.south()).getBlock() == sapling
                        && level.getBlockState(a.east().south()).getBlock() == sapling) {
                    level.setBlock(a, air, flags);
                    level.setBlock(a.east(), air, flags);
                    level.setBlock(a.south(), air, flags);
                    level.setBlock(a.east().south(), air, flags);
                    cx = a.getX() + 1.0;
                    cz = a.getZ() + 1.0;
                    big = true;
                    break outer;
                }
            }
        }
        if (!big) level.setBlock(pos, air, flags);

        float height = big ? 14f + level.random.nextFloat() * 6f : 6f + level.random.nextFloat() * 3f;
        int model = TreeSpecies.pickModel(wood, big ? 4 : 1, Math.round(height), level.random);
        float scale = TreeSpecies.scaleFor(model, height) * TreeConfig.COMMON.treeScale.get().floatValue();

        CustomTreeEntity tree = new CustomTreeEntity(ModEntities.CUSTOM_TREE.get(), level);
        tree.setPos(cx, pos.getY() - 0.05, cz);
        tree.setYRot(level.random.nextFloat() * 360f);
        tree.setTreeType(model);
        tree.setTreeScale(scale);
        tree.setLogItem(logId.toString());
        tree.setLogCount(big ? Math.round(height * 3f) : Math.round(height));
        level.addFreshEntity(tree);

        event.setCanceled(true);
    }
}
