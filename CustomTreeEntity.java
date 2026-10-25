package com.realistictrees.entity;

import com.realistictrees.TreeConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Одно дерево. Рисуется кастомной моделью, ломается топором (или рукой) и падает. */
public class CustomTreeEntity extends Entity {
    public static final int FALL_DURATION_TICKS = 40;

    private static final EntityDataAccessor<Integer> TREE_TYPE =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIND_PHASE =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TREE_SCALE =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FALLING =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FALL_YAW =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FALL_START_TICK =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LOG_ITEM =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LOG_COUNT =
            SynchedEntityData.defineId(CustomTreeEntity.class, EntityDataSerializers.INT);

    public CustomTreeEntity(EntityType<? extends CustomTreeEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TREE_TYPE, 3);
        builder.define(WIND_PHASE, (float) (Math.random() * Math.PI * 2.0));
        builder.define(TREE_SCALE, 1.0f);
        builder.define(FALLING, false);
        builder.define(FALL_YAW, 0.0f);
        builder.define(FALL_START_TICK, 0);
        builder.define(LOG_ITEM, "minecraft:oak_log");
        builder.define(LOG_COUNT, 4);
    }

    // ---- данные ----
    public int getTreeType() { return Mth.clamp(entityData.get(TREE_TYPE), 0, TreeSpecies.MODEL_COUNT - 1); }
    public void setTreeType(int t) {
        entityData.set(TREE_TYPE, Mth.clamp(t, 0, TreeSpecies.MODEL_COUNT - 1));
        refreshDimensions();
    }
    public static String modelName(int type) { return TreeSpecies.MODELS[Mth.clamp(type, 0, TreeSpecies.MODEL_COUNT - 1)]; }

    public float getWindPhase() { return entityData.get(WIND_PHASE); }
    public float getTreeScale() { return entityData.get(TREE_SCALE); }
    public void setTreeScale(float s) {
        entityData.set(TREE_SCALE, Mth.clamp(s, 0.3f, 4.0f));
        refreshDimensions();
    }
    public boolean isFalling() { return entityData.get(FALLING); }
    public float getFallYaw() { return entityData.get(FALL_YAW); }
    public void setLogItem(String id) { entityData.set(LOG_ITEM, id); }
    public void setLogCount(int n) { entityData.set(LOG_COUNT, Mth.clamp(n, 1, 256)); }

    /** 0..1 */
    public float getFallProgress(float partialTick) {
        if (!isFalling()) return 0f;
        float elapsed = (level().getGameTime() + partialTick) - entityData.get(FALL_START_TICK);
        return Mth.clamp(elapsed / FALL_DURATION_TICKS, 0f, 1f);
    }

    // ---- размеры ----
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (TREE_SCALE.equals(key) || TREE_TYPE.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (this.entityData == null) return EntityDimensions.fixed(1f, 1f);
        int t = getTreeType();
        float s = getTreeScale();
        float w = Math.max(0.8f, TreeSpecies.TRUNK_W[t] * s);
        float h = TreeSpecies.HEIGHT[t] * s;
        return EntityDimensions.fixed(w, h);
    }

    /** Большая область для отсечения по камере, чтобы крона не пропадала, когда ствол вне экрана. */
    @Override
    public AABB getBoundingBoxForCulling() {
        int t = getTreeType();
        float s = getTreeScale();
        double r = TreeSpecies.CROWN[t] * s + 1.0;
        double h = TreeSpecies.HEIGHT[t] * s + 1.0;
        return new AABB(getX() - r, getY() - 0.5, getZ() - r, getX() + r, getY() + h, getZ() + r);
    }

    @Override public boolean isPickable() { return !isFalling(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return !isFalling(); }

    // ---- рубка ----
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!level().isClientSide && !isFalling()) {
            if (attacker instanceof Player player) {
                boolean axe = player.getMainHandItem().is(ItemTags.AXES);
                if (TreeConfig.COMMON.requireAxe.get() && !axe) {
                    return true;
                }
                if (axe) {
                    player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                }
            }
            double dx = getX() - attacker.getX();
            double dz = getZ() - attacker.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
            startFall(yaw);
        }
        return true;
    }

    public void startFall(float directionDegrees) {
        entityData.set(FALL_YAW, directionDegrees);
        entityData.set(FALL_START_TICK, (int) level().getGameTime());
        entityData.set(FALLING, true);
        playSound(SoundEvents.WOOD_BREAK, 1.0f, 0.8f);
    }

    @Override
    public void tick() {
        if (level().isClientSide) return;
        if (isFalling() && level().getGameTime() - entityData.get(FALL_START_TICK) >= FALL_DURATION_TICKS) {
            finishFall();
        }
    }

    private void finishFall() {
        playSound(SoundEvents.WOOD_FALL, 1.2f, 0.7f);
        Item log = Items.OAK_LOG;
        ResourceLocation logId = ResourceLocation.tryParse(entityData.get(LOG_ITEM));
        if (logId != null) {
            log = BuiltInRegistries.ITEM.getOptional(logId).orElse(Items.OAK_LOG);
        }
        int count = entityData.get(LOG_COUNT);
        while (count > 0) {
            int n = Math.min(count, 64);
            spawnAtLocation(new ItemStack(log, n));
            count -= n;
        }
        spawnAtLocation(new ItemStack(saplingFor(logId), 1 + random.nextInt(2)));
        spawnAtLocation(new ItemStack(Items.STICK, 2 + random.nextInt(4)));
        discard();
    }

    /** Саженец того же вида, что и бревно; запасной вариант - дубовый. */
    public static Item saplingFor(ResourceLocation logId) {
        if (logId == null) return Items.OAK_SAPLING;
        String wood = TreeSpecies.woodOf(logId);
        for (String suffix : new String[]{"_sapling", "_propagule"}) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(logId.getNamespace(), wood + suffix);
            var opt = BuiltInRegistries.ITEM.getOptional(id);
            if (opt.isPresent()) return opt.get();
        }
        return Items.OAK_SAPLING;
    }

    // ---- сохранение ----
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("TreeType")) entityData.set(TREE_TYPE, Mth.clamp(tag.getInt("TreeType"), 0, TreeSpecies.MODEL_COUNT - 1));
        if (tag.contains("Scale")) entityData.set(TREE_SCALE, tag.getFloat("Scale"));
        if (tag.contains("Phase")) entityData.set(WIND_PHASE, tag.getFloat("Phase"));
        entityData.set(FALLING, tag.getBoolean("Falling"));
        entityData.set(FALL_YAW, tag.getFloat("FallYaw"));
        entityData.set(FALL_START_TICK, tag.getInt("FallStartTick"));
        if (tag.contains("LogItem")) entityData.set(LOG_ITEM, tag.getString("LogItem"));
        if (tag.contains("LogCount")) entityData.set(LOG_COUNT, tag.getInt("LogCount"));
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("TreeType", entityData.get(TREE_TYPE));
        tag.putFloat("Scale", entityData.get(TREE_SCALE));
        tag.putFloat("Phase", entityData.get(WIND_PHASE));
        tag.putBoolean("Falling", entityData.get(FALLING));
        tag.putFloat("FallYaw", entityData.get(FALL_YAW));
        tag.putInt("FallStartTick", entityData.get(FALL_START_TICK));
        tag.putString("LogItem", entityData.get(LOG_ITEM));
        tag.putInt("LogCount", entityData.get(LOG_COUNT));
    }
}
