package com.realistictrees;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Все настройки мода. Меню: Mods -> Realistic Trees -> Config. */
public final class TreeConfig {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        var common = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        var client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private static final String K = "realistictrees.configuration.";

    /** Серверные настройки (что и как заменяется). */
    public static final class Common {
        public final ModConfigSpec.BooleanValue replaceWorldgenTrees;
        public final ModConfigSpec.BooleanValue replaceSaplings;
        public final ModConfigSpec.BooleanValue requireAxe;
        public final ModConfigSpec.DoubleValue treeScale;
        public final ModConfigSpec.IntValue minLeaves;
        public final ModConfigSpec.IntValue chunksPerTick;

        Common(ModConfigSpec.Builder b) {
            replaceWorldgenTrees = b.comment("Replace natural vanilla trees (all wood types) with realistic models when chunks load")
                    .translation(K + "replaceWorldgenTrees").define("replaceWorldgenTrees", true);
            replaceSaplings = b.comment("Replace trees grown from saplings / bone meal")
                    .translation(K + "replaceSaplings").define("replaceSaplings", true);
            requireAxe = b.comment("Only an axe can chop down a tree")
                    .translation(K + "requireAxe").define("requireAxe", false);
            treeScale = b.comment("Size multiplier for all replaced trees")
                    .translation(K + "treeScale").defineInRange("treeScale", 1.0, 0.5, 2.0);
            minLeaves = b.comment("Minimum number of natural leaves for a log cluster to count as a tree (protects player builds)")
                    .translation(K + "minLeaves").defineInRange("minLeaves", 8, 1, 200);
            chunksPerTick = b.comment("How many chunks are scanned for trees per server tick (lower = less lag)")
                    .translation(K + "chunksPerTick").defineInRange("chunksPerTick", 3, 1, 32);
        }
    }

    /** Клиентские настройки (ветер и графика). */
    public static final class Client {
        public final ModConfigSpec.BooleanValue enableWind;
        public final ModConfigSpec.DoubleValue windStrength;
        public final ModConfigSpec.DoubleValue windSpeed;
        public final ModConfigSpec.DoubleValue gustiness;
        public final ModConfigSpec.DoubleValue leafFlutter;
        public final ModConfigSpec.IntValue windDirection;
        public final ModConfigSpec.BooleanValue useExternalWind;
        public final ModConfigSpec.IntValue animationDistance;
        public final ModConfigSpec.IntValue renderDistance;

        Client(ModConfigSpec.Builder b) {
            enableWind = b.comment("Enable tree wind animation")
                    .translation(K + "enableWind").define("enableWind", true);
            windStrength = b.comment("Strength of tree tilt from wind. 0 = trees stand still, 1 = default, 5 = extreme")
                    .translation(K + "windStrength").defineInRange("windStrength", 1.0, 0.0, 5.0);
            windSpeed = b.comment("Speed of swaying")
                    .translation(K + "windSpeed").defineInRange("windSpeed", 1.0, 0.1, 4.0);
            gustiness = b.comment("How strong gusts are (variation of the tilt over time)")
                    .translation(K + "gustiness").defineInRange("gustiness", 1.0, 0.0, 2.0);
            leafFlutter = b.comment("Leaf flutter amount")
                    .translation(K + "leafFlutter").defineInRange("leafFlutter", 1.0, 0.0, 3.0);
            windDirection = b.comment("Base wind direction in degrees (0 = east, 90 = south, 180 = west, 270 = north)")
                    .translation(K + "windDirection").defineInRange("windDirection", 45, 0, 360);
            useExternalWind = b.comment("Use wind from the weather mod (RealWeather) when it provides one")
                    .translation(K + "useExternalWind").define("useExternalWind", true);
            animationDistance = b.comment("Trees farther than this (blocks) do not animate - saves FPS")
                    .translation(K + "animationDistance").defineInRange("animationDistance", 56, 8, 160);
            renderDistance = b.comment("Trees farther than this (blocks) are not rendered")
                    .translation(K + "renderDistance").defineInRange("renderDistance", 128, 32, 256);
        }
    }

    private TreeConfig() {}
}
