package com.realistictrees.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Какие модели есть и какая модель подходит какому виду дерева. */
public final class TreeSpecies {
    public static final String[] MODELS = {
            "fur_tree", "mega_tree", "beech_tree",
            "low_poly_tree_0", "low_poly_tree_1", "low_poly_tree_2"
    };
    public static final int MODEL_COUNT = MODELS.length;

    /** Высота модели, радиус кроны, ширина ствола (для хитбокса) и допустимый масштаб. */
    public static final float[] HEIGHT    = {9.5f, 12.32f, 7.61f, 5.75f, 5.95f, 5.88f};
    public static final float[] CROWN     = {2.1f, 6.3f, 3.2f, 1.6f, 1.5f, 1.6f};
    public static final float[] TRUNK_W   = {0.7f, 1.6f, 0.9f, 0.7f, 0.7f, 0.7f};
    public static final float[] MIN_SCALE = {0.8f, 0.45f, 0.8f, 0.8f, 0.8f, 0.8f};
    public static final float[] MAX_SCALE = {2.0f, 2.6f, 2.0f, 2.2f, 2.2f, 2.2f};

    /** "minecraft:dark_oak_log" -> "dark_oak"; "stripped_birch_wood" -> "birch". */
    public static String woodOf(ResourceLocation logId) {
        String p = logId.getPath();
        if (p.startsWith("stripped_")) p = p.substring(9);
        for (String suffix : new String[]{"_log", "_wood", "_stem", "_hyphae", "_block"}) {
            if (p.endsWith(suffix)) {
                p = p.substring(0, p.length() - suffix.length());
                break;
            }
        }
        return p;
    }

    /**
     * Выбор модели по виду дерева.
     * @param baseLogs число брёвен в нижнем слое (>=3 значит ствол 2x2)
     * @param height   высота дерева в блоках
     */
    public static int pickModel(String wood, int baseLogs, int height, RandomSource r) {
        boolean thick = baseLogs >= 3;
        switch (wood) {
            case "spruce":
                return thick ? 1 : 0;
            case "birch":
                return 2;
            case "dark_oak":
                return 1;
            case "jungle":
                if (thick) return 1;
                return height >= 8 ? 2 : 3 + r.nextInt(3);
            case "oak":
                if (thick) return 1;
                return (height >= 9 || r.nextInt(100) < 25) ? 2 : 3 + r.nextInt(3);
            default: // acacia, cherry, mangrove, azalea и моды
                if (thick) return 1;
                return 3 + r.nextInt(3);
        }
    }

    /** Масштаб, чтобы модель была такой же высоты, как заменяемое дерево. */
    public static float scaleFor(int model, float wantedHeight) {
        int m = Mth.clamp(model, 0, MODEL_COUNT - 1);
        return Mth.clamp(wantedHeight / HEIGHT[m], MIN_SCALE[m], MAX_SCALE[m]);
    }

    private TreeSpecies() {}
}
