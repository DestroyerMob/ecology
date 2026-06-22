package com.destroyermob.ecology;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EcologyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_BEE_SYSTEM = BUILDER
            .comment("Master switch for Ecology bee AI and colony simulation.")
            .define("enableBeeSystem", true);
    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_BEE_GOALS = BUILDER
            .comment("When false, Ecology only initializes bee memory and leaves vanilla bee AI intact.")
            .define("replaceVanillaBeeGoals", true);
    public static final ModConfigSpec.BooleanValue ENABLE_HIVE_COLONY_TICKING = BUILDER
            .comment("Enables Ecology colony simulation from vanilla hive ticks.")
            .define("enableHiveColonyTicking", true);
    public static final ModConfigSpec.BooleanValue ENABLE_BEE_LIFESPAN_DEATH = BUILDER
            .comment("When true, visible bees die from Ecology lifespan rules in entity tick.")
            .define("enableBeeLifespanDeath", true);
    public static final ModConfigSpec.BooleanValue ENABLE_DRONE_MATING_GOAL = BUILDER
            .comment("Enables live drone mating AI.")
            .define("enableDroneMatingGoal", true);
    public static final ModConfigSpec.BooleanValue ENABLE_QUEEN_MIGRATION_GOAL = BUILDER
            .comment("Enables live queen migration AI.")
            .define("enableQueenMigrationGoal", true);
    public static final ModConfigSpec.BooleanValue DEBUG_BEE_SYSTEM_LOGGING = BUILDER
            .comment("Logs diagnostic Ecology bee initialization and hive tick events.")
            .define("debugBeeSystemLogging", false);

    public static final ModConfigSpec.IntValue WORKER_LIFESPAN_DAYS = BUILDER
            .comment("Worker bee lifespan in Minecraft days.")
            .defineInRange("workerLifespanDays", 7, 1, 365);
    public static final ModConfigSpec.IntValue DRONE_LIFESPAN_DAYS = BUILDER
            .comment("Drone bee lifespan in Minecraft days.")
            .defineInRange("droneLifespanDays", 14, 1, 365);
    public static final ModConfigSpec.IntValue QUEEN_LIFESPAN_DAYS = BUILDER
            .comment("Queen bee lifespan in Minecraft days.")
            .defineInRange("queenLifespanDays", 21, 1, 365);

    public static final ModConfigSpec.IntValue MAX_WORKERS_PER_HIVE = BUILDER
            .defineInRange("maxWorkersPerHive", 5, 1, 32);
    public static final ModConfigSpec.IntValue MAX_DRONES_PER_HIVE = BUILDER
            .defineInRange("maxDronesPerHive", 3, 0, 32);
    public static final ModConfigSpec.IntValue MAX_QUEENS_PER_HIVE = BUILDER
            .comment("Ecology currently tracks one queen per hive.")
            .defineInRange("maxQueensPerHive", 1, 1, 1);
    public static final ModConfigSpec.IntValue BEEHIVE_CAPACITY = BUILDER
            .comment("Maximum bees an Ecology hive or nest can physically store.")
            .defineInRange("beehiveCapacity", 5, 1, 32);
    public static final ModConfigSpec.IntValue FRESH_HIVE_RELEASE_TICKS = BUILDER
            .comment("Minimum ticks before newly stored nectarless bees can leave a hive. Vanilla uses 600 ticks.")
            .defineInRange("freshHiveReleaseTicks", 100, 1, 24000);
    public static final ModConfigSpec.IntValue DAILY_COMPLETE_RELEASE_PADDING_TICKS = BUILDER
            .comment("Extra ticks after the next Minecraft day starts before a worker that completed its route may leave the hive.")
            .defineInRange("dailyCompleteReleasePaddingTicks", 100, 0, 24000);
    public static final ModConfigSpec.BooleanValue AUTO_SEED_EMPTY_HIVES = BUILDER
            .comment("Development helper only. When false, empty hives never generate free Ecology colonies.")
            .define("autoSeedEmptyHives", false);
    public static final ModConfigSpec.IntValue MAX_ROUTE_PAIRS = BUILDER
            .comment("Maximum flower/crop pairs a worker bee attempts each day.")
            .defineInRange("maxRoutePairs", 8, 1, 32);
    public static final ModConfigSpec.IntValue QUEEN_REPLACEMENT_DAYS = BUILDER
            .comment("Workers raise a replacement queen when the current queen has this many Minecraft days left.")
            .defineInRange("queenReplacementDays", 3, 1, 21);
    public static final ModConfigSpec.IntValue COLONY_CATCHUP_DAYS = BUILDER
            .comment("Maximum colony days to simulate when a hive chunk loads after being unloaded.")
            .defineInRange("colonyCatchupDays", 3, 1, 30);

    public static final ModConfigSpec.IntValue BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS = BUILDER
            .comment("Horizontal radius for live bee searches. A value of 2 means a 5x5 area.")
            .defineInRange("beeLocalSearchHorizontalRadius", 2, 1, 8);
    public static final ModConfigSpec.IntValue BEE_LOCAL_SEARCH_VERTICAL_RADIUS = BUILDER
            .comment("Vertical radius for live bee searches. This is taller than the horizontal range so tree-nest bees can see ground flowers.")
            .defineInRange("beeLocalSearchVerticalRadius", 5, 0, 8);
    public static final ModConfigSpec.IntValue MAX_ROUTE_SEARCH_MISSES = BUILDER
            .comment("Local search areas a worker can fail to find the next route target in before returning home.")
            .defineInRange("maxRouteSearchMisses", 8, 1, 64);
    public static final ModConfigSpec.IntValue WORKER_FLOWER_SEARCH_TICKS = BUILDER
            .comment("Ticks a worker searches for its next flower before returning home.")
            .defineInRange("workerFlowerSearchTicks", 20 * 60, 20, 20 * 30 * 10);
    public static final ModConfigSpec.IntValue WORKER_CROP_SEARCH_TICKS = BUILDER
            .comment("Ticks a worker searches for a crop after gathering pollen before returning home.")
            .defineInRange("workerCropSearchTicks", 20 * 30, 20, 20 * 30 * 10);
    public static final ModConfigSpec.IntValue WORKER_TRAVEL_TARGET_TICKS = BUILDER
            .comment("Ticks a worker may spend travelling to a flower or crop target before returning home.")
            .defineInRange("workerTravelTargetTicks", 20 * 30, 20, 20 * 30 * 10);
    public static final ModConfigSpec.IntValue WORKER_MAX_DISTANCE_FROM_HIVE = BUILDER
            .comment("Maximum block distance a worker may roam from its home hive before returning.")
            .defineInRange("workerMaxDistanceFromHive", 50, 4, 256);

    public static final ModConfigSpec.IntValue FLOWER_SEARCH_RANGE = BUILDER
            .comment("Worker flower detection range. Vanilla bee pollination searches within five blocks.")
            .defineInRange("flowerSearchRange", 5, 1, 32);
    public static final ModConfigSpec.IntValue CROP_SEARCH_RANGE = BUILDER
            .comment("Crop search range used after each flower.")
            .defineInRange("cropSearchRange", 5, 1, 32);
    public static final ModConfigSpec.IntValue HIVE_SEARCH_RANGE = BUILDER
            .defineInRange("hiveSearchRange", 20, 1, 128);
    public static final ModConfigSpec.IntValue MIGRATION_SEARCH_RANGE = BUILDER
            .defineInRange("migrationSearchRange", 48, 8, 256);

    public static final ModConfigSpec.DoubleValue PATH_HORIZONTAL_TRIGGER_RANGE = BUILDER
            .comment("Horizontal player distance from a worker route that makes that bee defensive.")
            .defineInRange("pathHorizontalTriggerRange", 3.0, 0.5, 16.0);
    public static final ModConfigSpec.DoubleValue PATH_VERTICAL_TRIGGER_RANGE = BUILDER
            .comment("Vertical player distance from a worker route that makes that bee defensive.")
            .defineInRange("pathVerticalTriggerRange", 2.0, 0.5, 16.0);
    public static final ModConfigSpec.IntValue ROUTE_AGITATION_ATTACK_TICKS = BUILDER
            .comment("Ticks a player must remain in a worker route before that bee becomes aggressive.")
            .defineInRange("routeAgitationAttackTicks", 60, 1, 20 * 30);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EcologyConfig() {
    }

    public static int hiveCapacity() {
        return BEEHIVE_CAPACITY.get();
    }
}
