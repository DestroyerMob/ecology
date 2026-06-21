package com.destroyermob.ecology;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EcologyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

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
            .defineInRange("maxQueensPerHive", 1, 1, 4);
    public static final ModConfigSpec.IntValue MAX_ROUTE_PAIRS = BUILDER
            .comment("Maximum flower/crop pairs a worker bee attempts each day.")
            .defineInRange("maxRoutePairs", 8, 1, 32);

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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EcologyConfig() {
    }

    public static int hiveCapacity() {
        return MAX_WORKERS_PER_HIVE.get() + MAX_DRONES_PER_HIVE.get() + MAX_QUEENS_PER_HIVE.get();
    }
}
