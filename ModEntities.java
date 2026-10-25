package com.realistictrees;

import com.realistictrees.entity.CustomTreeEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, RealisticTrees.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CustomTreeEntity>> CUSTOM_TREE =
            ENTITY_TYPES.register("custom_tree", () -> EntityType.Builder
                    .of(CustomTreeEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("custom_tree"));

    private ModEntities() {}
}
