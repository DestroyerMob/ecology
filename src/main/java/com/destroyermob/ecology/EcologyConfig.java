package com.destroyermob.ecology;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EcologyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<EcologyPreset> GAMEPLAY_PRESET = BUILDER
            .comment(
                    "High-level Ecology behavior preset. CUSTOM uses the individual switches below.",
                    "VANILLA_SAFE keeps Ecology passive. LIGHT_ECOLOGY enables colony data and beekeeper relocation without replacing vanilla bee AI.",
                    "FULL_SIMULATION enables the complete bee simulation and village construction. DEBUG_TESTING is full simulation plus noisy diagnostics and auto-seeded hives.")
            .defineEnum("gameplayPreset", EcologyPreset.CUSTOM);
    public static final ModConfigSpec.BooleanValue ENABLE_BEE_SYSTEM = BUILDER
            .comment("Master switch for Ecology's advanced bee AI, colony simulation, hive data, and bee relocation items. Disabled by default so vanilla bee behavior is unchanged unless a pack opts in.")
            .define("enableBeeSystem", false);
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
    public static final ModConfigSpec.BooleanValue ENABLE_BEE_RELOCATION_ITEMS = BUILDER
            .comment("Enables the beekeeper knife, brood comb, captured worker bee, nest sealing, and hive day simulator interactions. Requires enableBeeSystem and enableHiveColonyTicking.")
            .define("enableBeeRelocationItems", true);
    public static final ModConfigSpec.BooleanValue ENABLE_HIVE_HEALTH = BUILDER
            .comment("Enables colony health scoring for Jade, beekeeper journals, and systems that react to hive condition.")
            .define("enableHiveHealth", true);
    public static final ModConfigSpec.BooleanValue ENABLE_HEALTHY_POLLINATION_BONUS = BUILDER
            .comment("Lets healthy colonies occasionally grow a crop by an extra stage when a worker delivers pollen.")
            .define("enableHealthyPollinationBonus", true);
    public static final ModConfigSpec.DoubleValue HEALTHY_POLLINATION_BONUS_CHANCE = BUILDER
            .comment("Maximum extra crop-growth chance from a healthy or thriving colony.")
            .defineInRange("healthyPollinationBonusChance", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue ENABLE_COLONY_TRAITS = BUILDER
            .comment("Enables colony traits that influence health, route productivity, brood success, temperament, and swarming.")
            .define("enableColonyTraits", true);
    public static final ModConfigSpec.BooleanValue ENABLE_SWARMING = BUILDER
            .comment("Lets healthy crowded colonies create daughter colonies in nearby empty hives.")
            .define("enableSwarming", true);
    public static final ModConfigSpec.DoubleValue SWARMING_CHANCE = BUILDER
            .comment("Daily chance for a swarm-ready colony to create a daughter colony when an empty hive is nearby.")
            .defineInRange("swarmingChance", 0.18, 0.0, 1.0);
    public static final ModConfigSpec.IntValue SWARM_COOLDOWN_DAYS = BUILDER
            .comment("Minimum Minecraft days between successful swarms from the same colony.")
            .defineInRange("swarmCooldownDays", 3, 1, 30);
    public static final ModConfigSpec.BooleanValue DEBUG_BEE_SYSTEM_LOGGING = BUILDER
            .comment("Logs diagnostic Ecology bee initialization and hive tick events.")
            .define("debugBeeSystemLogging", false);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_GOLEM_CONSTRUCTION = BUILDER
            .comment("When true, villager iron golem spawning builds the iron golem structure before the golem appears.")
            .define("enableVillagerGolemConstruction", true);
    public static final ModConfigSpec.BooleanValue DEBUG_VILLAGER_GOLEM_CONSTRUCTION = BUILDER
            .comment("Logs diagnostic village iron golem construction events.")
            .define("debugVillagerGolemConstruction", false);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_ECOLOGY = BUILDER
            .comment("Enables village ecology scoring, Village Ledger reports, and village-health effects on golem construction.")
            .define("enableVillageEcology", true);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_MAINTENANCE = BUILDER
            .comment("Lets villagers occasionally perform small upkeep tasks such as replanting empty farmland, repairing path gaps, and adding garden flowers.")
            .define("enableVillageMaintenance", true);
    public static final ModConfigSpec.IntValue VILLAGE_ECOLOGY_RADIUS = BUILDER
            .comment("Horizontal radius used by village ecology surveys.")
            .defineInRange("villageEcologyRadius", 32, 8, 96);
    public static final ModConfigSpec.IntValue VILLAGE_MAINTENANCE_INTERVAL_TICKS = BUILDER
            .comment("Per-villager tick interval for attempting small village maintenance.")
            .defineInRange("villageMaintenanceIntervalTicks", 20 * 60, 20 * 10, 20 * 60 * 10);
    public static final ModConfigSpec.DoubleValue VILLAGE_MAINTENANCE_CHANCE = BUILDER
            .comment("Chance that a villager performs a maintenance action when its interval triggers.")
            .defineInRange("villageMaintenanceChance", 0.20, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_VOCATIONS = BUILDER
            .comment("Lets Ecology assign professions to jobless adult villagers based on parent professions, village needs, and a small random chance.")
            .define("enableVillageVocations", true);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_SUPPLIES = BUILDER
            .comment("Enables a lightweight village supply ledger that affects villager trade capacity and can catch up while villages are unloaded.")
            .define("enableVillageSupplies", true);
    public static final ModConfigSpec.IntValue VILLAGE_SUPPLY_UPDATE_INTERVAL_TICKS = BUILDER
            .comment("Minimum game ticks between per-village supply catch-up attempts from active villagers.")
            .defineInRange("villageSupplyUpdateIntervalTicks", 20 * 30, 20 * 5, 20 * 60 * 10);
    public static final ModConfigSpec.IntValue VILLAGE_SUPPLY_SURVEY_INTERVAL_TICKS = BUILDER
            .comment("Minimum game ticks between full village supply surveys. Higher values reduce scans; lower values react faster to building changes.")
            .defineInRange("villageSupplySurveyIntervalTicks", 20 * 120, 20 * 15, 20 * 60 * 20);
    public static final ModConfigSpec.IntValue VILLAGE_SUPPLY_CATCHUP_DAYS = BUILDER
            .comment("Maximum Minecraft days of village supply progress to simulate after a village has been unloaded.")
            .defineInRange("villageSupplyCatchupDays", 3, 1, 14);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_WELFARE = BUILDER
            .comment("Tracks whether trading villagers can reach homes and meeting space. Confined villagers lose Ecology market benefits and gain price penalties.")
            .define("enableVillageWelfare", true);
    public static final ModConfigSpec.IntValue VILLAGE_WELFARE_CHECK_INTERVAL_TICKS = BUILDER
            .comment("Per-villager interval for checking whether a villager appears confined.")
            .defineInRange("villageWelfareCheckIntervalTicks", 20 * 90, 20 * 30, 20 * 60 * 10);
    public static final ModConfigSpec.IntValue VILLAGE_WELFARE_GRACE_CHECKS = BUILDER
            .comment("Consecutive failed welfare checks before Ecology treats a villager as confined.")
            .defineInRange("villageWelfareGraceChecks", 3, 1, 10);
    public static final ModConfigSpec.IntValue VILLAGE_WELFARE_MAX_PRICE_PENALTY = BUILDER
            .comment("Maximum special price increase applied to confined villagers' offers.")
            .defineInRange("villageWelfareMaxPricePenalty", 16, 0, 64);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_HOUSEHOLDS = BUILDER
            .comment("Tracks villager households, parentage, home ownership, household savings, and adult children moving into empty homes.")
            .define("enableVillageHouseholds", true);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSEHOLD_UPDATE_INTERVAL_TICKS = BUILDER
            .comment("Per-villager interval for light household syncing and adult-child move-out checks.")
            .defineInRange("villageHouseholdUpdateIntervalTicks", 20 * 60, 20 * 15, 20 * 60 * 10);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSEHOLD_MOVE_OUT_SAVINGS = BUILDER
            .comment("Household savings spent when an adult child moves into an empty home.")
            .defineInRange("villageHouseholdMoveOutSavings", 32, 0, 500);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSEHOLD_EXPANSION_READY_SAVINGS = BUILDER
            .comment("Savings level at which a crowded household is reported as ready to fund a home expansion.")
            .defineInRange("villageHouseholdExpansionReadySavings", 72, 0, 1000);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_HOUSE_CONSTRUCTION = BUILDER
            .comment("Lets households with enough savings fund houses on player-approved plots marked with the Village Ledger and a banner.")
            .define("enableVillageHouseConstruction", true);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSE_CONSTRUCTION_SAVINGS_COST = BUILDER
            .comment("Household savings spent when starting a player-approved house construction.")
            .defineInRange("villageHouseConstructionSavingsCost", 72, 0, 1000);
    public static final ModConfigSpec.IntValue VILLAGE_HOME_UPGRADE_SAVINGS_COST = BUILDER
            .comment("Household savings spent when adding beds to an existing generated village home.")
            .defineInRange("villageHomeUpgradeSavingsCost", 24, 0, 500);
    public static final ModConfigSpec.IntValue VILLAGE_HOME_UPGRADE_MAX_BEDS = BUILDER
            .comment("Maximum beds Ecology may add during one existing-home upgrade.")
            .defineInRange("villageHomeUpgradeMaxBeds", 2, 1, 8);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSE_CONSTRUCTION_BLOCKS_PER_STEP = BUILDER
            .comment("How much construction progress a household makes per work step. Higher values complete approved vanilla homes faster.")
            .defineInRange("villageHouseConstructionBlocksPerStep", 8, 1, 64);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSE_PLOT_MAX_SIZE = BUILDER
            .comment("Maximum width or depth of a player-approved house construction plot.")
            .defineInRange("villageHousePlotMaxSize", 15, 7, 32);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSE_SMALL_WEIGHT = BUILDER
            .comment("Base weight for small vanilla village house templates.")
            .defineInRange("villageHouseSmallWeight", 42, 0, 1000);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSE_MEDIUM_WEIGHT = BUILDER
            .comment("Base weight for medium vanilla village house templates.")
            .defineInRange("villageHouseMediumWeight", 36, 0, 1000);
    public static final ModConfigSpec.IntValue VILLAGE_HOUSE_LARGE_WEIGHT = BUILDER
            .comment("Base weight for larger vanilla village house templates.")
            .defineInRange("villageHouseLargeWeight", 22, 0, 1000);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_MARKET_STALLS = BUILDER
            .comment("Lets players assign villager market stall positions with the Village Ledger. Assigned villagers walk to their stall during work hours when pathing allows.")
            .define("enableVillageMarketStalls", true);
    public static final ModConfigSpec.IntValue VILLAGE_MARKET_STALL_WALK_INTERVAL_TICKS = BUILDER
            .comment("Per-villager interval for nudging assigned villagers toward their market stall during work hours.")
            .defineInRange("villageMarketStallWalkIntervalTicks", 20 * 8, 20 * 2, 20 * 60);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_PLAYER_TRADES = BUILDER
            .comment("Lets players assign tradeboards and stock inventories to non-confined villagers for player-supplied custom trades.")
            .define("enableVillagePlayerTrades", true);
    public static final ModConfigSpec.IntValue VILLAGE_PLAYER_TRADE_MAX_OFFERS = BUILDER
            .comment("Maximum active tradeboard offers generated for one villager. A 15x15 tradeboard can hold more definitions, but only this many stocked offers are exposed at once.")
            .defineInRange("villagePlayerTradeMaxOffers", 64, 1, 225);
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGE_CURRENCIES = BUILDER
            .comment("Assigns each village one trade currency. Ruby and sapphire villages only spawn when another mod/datapack supplies items through Ecology's village currency tags.")
            .define("enableVillageCurrencies", true);

    public static final ModConfigSpec.IntValue WORKER_LIFESPAN_DAYS = BUILDER
            .comment("Worker bee lifespan in Minecraft days.")
            .defineInRange("workerLifespanDays", 7, 1, 365);
    public static final ModConfigSpec.IntValue DRONE_LIFESPAN_DAYS = BUILDER
            .comment("Drone bee lifespan in Minecraft days.")
            .defineInRange("droneLifespanDays", 14, 1, 365);
    public static final ModConfigSpec.IntValue QUEEN_LIFESPAN_DAYS = BUILDER
            .comment("Queen bee lifespan in Minecraft days.")
            .defineInRange("queenLifespanDays", 21, 1, 365);
    public static final ModConfigSpec.IntValue QUEEN_MATING_MATURITY_DAYS = BUILDER
            .comment("Minecraft days a queen must age before her hive can trigger a mating flight.")
            .defineInRange("queenMatingMaturityDays", 2, 0, 365);
    public static final ModConfigSpec.DoubleValue MAX_MATING_LINEAGE_OVERLAP = BUILDER
            .comment("Maximum founder-lineage overlap allowed for mating. 1.0 permits exact-family matings; lower values reject closer related colonies.")
            .defineInRange("maxMatingLineageOverlap", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue INBREEDING_BROOD_PENALTY = BUILDER
            .comment("Brood failure multiplier from inbreeding. A value of 1.0 means F=0.5 gives a 50% brood success penalty.")
            .defineInRange("inbreedingBroodPenalty", 1.0, 0.0, 1.0);

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

    public static boolean advancedBeeSimulationEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_BEE_SYSTEM.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean replaceVanillaBeeGoalsEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> REPLACE_VANILLA_BEE_GOALS.get();
            case VANILLA_SAFE, LIGHT_ECOLOGY -> false;
            case FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean hiveColonyTickingEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_HIVE_COLONY_TICKING.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean beeLifespanDeathEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_BEE_LIFESPAN_DEATH.get();
            case VANILLA_SAFE, LIGHT_ECOLOGY -> false;
            case FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean droneMatingGoalEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_DRONE_MATING_GOAL.get();
            case VANILLA_SAFE, LIGHT_ECOLOGY -> false;
            case FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean queenMigrationGoalEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_QUEEN_MIGRATION_GOAL.get();
            case VANILLA_SAFE, LIGHT_ECOLOGY -> false;
            case FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean beeSystemDebugLoggingEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> DEBUG_BEE_SYSTEM_LOGGING.get();
            case DEBUG_TESTING -> true;
            case VANILLA_SAFE, LIGHT_ECOLOGY, FULL_SIMULATION -> false;
        };
    }

    public static boolean villagerGolemConstructionEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGER_GOLEM_CONSTRUCTION.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean villagerGolemDebugLoggingEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> DEBUG_VILLAGER_GOLEM_CONSTRUCTION.get();
            case DEBUG_TESTING -> true;
            case VANILLA_SAFE, LIGHT_ECOLOGY, FULL_SIMULATION -> false;
        };
    }

    public static boolean villageEcologyEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_ECOLOGY.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
    }

    public static boolean villageMaintenanceEnabled() {
        boolean maintenance = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_MAINTENANCE.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && maintenance;
    }

    public static boolean villageVocationsEnabled() {
        boolean vocations = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_VOCATIONS.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && vocations;
    }

    public static boolean villageSuppliesEnabled() {
        boolean supplies = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_SUPPLIES.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && supplies;
    }

    public static boolean villageWelfareEnabled() {
        boolean welfare = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_WELFARE.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && welfare;
    }

    public static boolean villageHouseholdsEnabled() {
        boolean households = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_HOUSEHOLDS.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && households;
    }

    public static boolean villageHouseConstructionEnabled() {
        boolean construction = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_HOUSE_CONSTRUCTION.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageHouseholdsEnabled() && construction;
    }

    public static boolean villageMarketStallsEnabled() {
        boolean stalls = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_MARKET_STALLS.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && stalls;
    }

    public static boolean villageCurrenciesEnabled() {
        boolean currencies = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_CURRENCIES.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && currencies;
    }

    public static boolean villagePlayerTradesEnabled() {
        boolean playerTrades = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_VILLAGE_PLAYER_TRADES.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return villageEcologyEnabled() && playerTrades;
    }

    public static boolean autoSeedEmptyHivesEnabled() {
        return switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> AUTO_SEED_EMPTY_HIVES.get();
            case DEBUG_TESTING -> true;
            case VANILLA_SAFE, LIGHT_ECOLOGY, FULL_SIMULATION -> false;
        };
    }

    public static boolean hiveSimulationEnabled() {
        return advancedBeeSimulationEnabled() && hiveColonyTickingEnabled();
    }

    public static boolean beeRelocationItemsEnabled() {
        boolean relocationItems = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_BEE_RELOCATION_ITEMS.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return hiveSimulationEnabled() && relocationItems;
    }

    public static boolean hiveHealthEnabled() {
        boolean health = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_HIVE_HEALTH.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return hiveSimulationEnabled() && health;
    }

    public static boolean healthyPollinationBonusEnabled() {
        boolean bonus = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_HEALTHY_POLLINATION_BONUS.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return hiveHealthEnabled() && bonus;
    }

    public static boolean colonyTraitsEnabled() {
        boolean traits = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_COLONY_TRAITS.get();
            case VANILLA_SAFE -> false;
            case LIGHT_ECOLOGY, FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return hiveSimulationEnabled() && traits;
    }

    public static boolean swarmingEnabled() {
        boolean swarming = switch (GAMEPLAY_PRESET.get()) {
            case CUSTOM -> ENABLE_SWARMING.get();
            case VANILLA_SAFE, LIGHT_ECOLOGY -> false;
            case FULL_SIMULATION, DEBUG_TESTING -> true;
        };
        return hiveSimulationEnabled() && swarming;
    }

    public enum EcologyPreset {
        CUSTOM,
        VANILLA_SAFE,
        LIGHT_ECOLOGY,
        FULL_SIMULATION,
        DEBUG_TESTING
    }
}
