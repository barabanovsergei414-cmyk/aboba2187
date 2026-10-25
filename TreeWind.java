package com.realistictrees.wind;

import com.realistictrees.TreeConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Ветер для деревьев. Читается только на клиенте (render thread).
 *
 * Погодный мод может задавать ветер каждый тик:
 *   TreeWind.setExternalWind(directionDegrees, strength);
 * directionDegrees: куда дует (0 = восток, 90 = юг), strength: 0.3 лёгкий ветер, 1 шторм, 2 торнадо.
 * Если внешний ветер не обновлялся 3 секунды - используется встроенный (по дождю и грозе).
 */
public final class TreeWind {
    private static volatile float extDir;
    private static volatile float extStrength;
    private static volatile long extStamp = Long.MIN_VALUE;

    /** Результат refresh(): единичный вектор ветра, уровень, время (сек). */
    public static float dirX = 1f, dirZ = 0f, level = 0.3f, time = 0f;

    private static long lastGameTime = Long.MIN_VALUE;
    private static float lastPartial = -1f;

    public static void setExternalWind(float directionDegrees, float strength) {
        extDir = directionDegrees;
        extStrength = Math.max(0f, strength);
        extStamp = System.nanoTime();
    }

    /** Пересчитывает ветер один раз за кадр. */
    public static void refresh(Level lvl, float partial) {
        long gt = lvl.getGameTime();
        if (gt == lastGameTime && partial == lastPartial) return;
        lastGameTime = gt;
        lastPartial = partial;

        float t = (gt + partial) / 20f;
        time = t;
        float dirDeg;
        float lv;
        boolean ext = TreeConfig.CLIENT.useExternalWind.get()
                && extStamp != Long.MIN_VALUE && (System.nanoTime() - extStamp) < 3_000_000_000L;
        if (ext) {
            dirDeg = extDir;
            lv = extStrength;
        } else {
            float rain = lvl.getRainLevel(partial);
            float thunder = lvl.getThunderLevel(partial);
            dirDeg = TreeConfig.CLIENT.windDirection.get() + 25f * Mth.sin(t * 0.03f);
            lv = 0.30f + 0.12f * Mth.sin(t * 0.11f) + 0.5f * rain + 0.7f * thunder;
        }
        float r = dirDeg * Mth.DEG_TO_RAD;
        dirX = Mth.cos(r);
        dirZ = Mth.sin(r);
        level = Math.max(0f, lv);
    }

    private TreeWind() {}
}
