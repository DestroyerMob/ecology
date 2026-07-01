# Ecology

Ecology is a NeoForge 1.21.1 mod for opt-in ecosystem experiments. Its current focus is advanced bee colony behavior, beekeeper tools, and a small village iron golem construction pass.

## Current Status

Internal playtesting. The advanced bee simulation is implemented but intentionally disabled by default so adding the mod does not change vanilla bee AI, hive capacity, or hive release timing unless a pack explicitly opts in.

## Current Features

- Advanced bee simulation with worker, drone, and queen roles.
- Colony data stored on hives, including queen state, worker/drone counts, aging, reproduction, inbreeding pressure, and unloaded-chunk catch-up.
- Worker bee flower/crop routes with optional route defense behavior.
- Hive health scoring based on colony records, queen/workers, forage, overcrowding, mating need, and inbreeding pressure.
- Healthy-colony pollination bonus where strong hives can occasionally grow crops by an extra stage.
- Colony traits that make hives calm, industrious, hardy, fertile, long-lived, or restless.
- Natural and player-guided swarming, including prepared queen cells for starting daughter colonies in empty hives.
- Apiary management tools: smoker, hive stand, queen excluder, brood frame, and inspection tray.
- Drone mating and queen migration goals.
- Configurable bee lifespans, route ranges, hive capacity, and daily simulation limits.
- Wax Goggles and Debug Wax Goggles for route visualization.
- Beekeeper's Journal for recording recent hive health, trait, treatment, and swarm-readiness snapshots in-game.
- Hive Day Simulator for advancing a hive by one simulated day during testing.
- Beekeeper's Knife, Brood Comb, captured worker bees, and honeycomb sealing for moving a cut-out nest into a player hive.
- JEI descriptions for bee tools and Jade providers for bee/hive diagnostics.
- Villager iron golem construction, where villagers build the structure before the golem appears.
- Village ecology surveys with food, shelter, safety, green space, water, and upkeep scoring.
- Village Ledger for player-readable settlement advice.
- Light villager upkeep: replanting empty farmland, patching path gaps, and adding small garden flowers.
- Village vocations: jobless adult villagers can receive professions from parent hints, settlement needs, and a small random chance.
- Village supplies: settlements track lightweight food, wood, stone, metal, paper, cloth, tools, medicine, and valuables that affect villager trade capacity and catch up while unloaded.
- Village welfare: confined traders lose Ecology market benefits and gain price penalties until they can reach homes and meeting space.
- Market stalls: players can mark stall tiles with the Village Ledger and assign villagers to work there during work hours.
- Village currencies: whole settlements can trade in emerald, or in tagged ruby/sapphire items supplied by another mod, without mixed-currency villagers in the same village.
- Debug commands for bee nest setup and village golem construction testing.

## Configuration

The common config is `ecology-common.toml`.

- `gameplayPreset=CUSTOM` by default. `CUSTOM` uses the individual switches below, `VANILLA_SAFE` keeps Ecology passive, `LIGHT_ECOLOGY` enables colony data and beekeeper relocation without replacing vanilla bee AI, `FULL_SIMULATION` enables the complete bee simulation and village construction, and `DEBUG_TESTING` adds diagnostics plus auto-seeded hives.
- `enableBeeSystem=false` by default. This is the master switch for advanced bee AI, colony ticking, hive data, hive capacity changes, release timing changes, route networking, and bee relocation items.
- `enableHiveColonyTicking=true`, `replaceVanillaBeeGoals=true`, `enableBeeLifespanDeath=true`, `enableDroneMatingGoal=true`, and `enableQueenMigrationGoal=true` only matter after `enableBeeSystem` is turned on.
- `enableBeeRelocationItems=true` controls beekeeper knife, brood comb, captured worker bee, honeycomb nest sealing, and hive day simulator interactions. It also requires `enableBeeSystem` and `enableHiveColonyTicking`.
- `enableHiveHealth=true` controls hive health scoring for Jade, the Beekeeper's Journal, and health-reactive systems.
- `enableHealthyPollinationBonus=true` and `healthyPollinationBonusChance=0.35` control the extra crop-growth chance from stable or thriving colonies.
- `enableColonyTraits=true` lets colonies gain inherited traits that affect health, brood, lifespan, temperament, route size, and swarming.
- `enableSwarming=true`, `swarmingChance=0.18`, and `swarmCooldownDays=3` control natural daughter-colony creation from crowded healthy hives.
- `enableVillagerGolemConstruction=true` controls the village golem construction feature.
- `enableVillageEcology=true` controls Village Ledger surveys and village-health effects on golem construction.
- `enableVillageMaintenance=true`, `villageMaintenanceIntervalTicks=1200`, and `villageMaintenanceChance=0.20` control small villager upkeep actions.
- `enableVillageVocations=true` lets Ecology assign professions to jobless adult villagers using parent professions, village needs, and a small random profession chance.
- `enableVillageSupplies=true`, `villageSupplyUpdateIntervalTicks=600`, `villageSupplySurveyIntervalTicks=2400`, and `villageSupplyCatchupDays=3` control the lightweight supply ledger behind trade capacity and unloaded-village catch-up.
- `enableVillageWelfare=true`, `villageWelfareCheckIntervalTicks=1800`, `villageWelfareGraceChecks=3`, and `villageWelfareMaxPricePenalty=16` control confined-villager market penalties.
- `enableVillageMarketStalls=true` and `villageMarketStallWalkIntervalTicks=160` control Village Ledger stall assignment and work-hour walking nudges.
- `enableVillageCurrencies=true` lets each village use emerald, plus ruby or sapphire when a loaded mod/datapack supplies items through `ecology:village_currency/ruby`, `ecology:village_currency/sapphire`, `c:gems/ruby`, or `c:gems/sapphire`.
- `debugBeeSystemLogging=false` and `debugVillagerGolemConstruction=false` keep noisy diagnostics out of normal play.

With the defaults, vanilla bees remain vanilla. To test the bee simulation, set `gameplayPreset` to `LIGHT_ECOLOGY` or `FULL_SIMULATION` and restart the game.

## Supported Versions

- Minecraft 1.21.1
- NeoForge 21.1.234
- Java 21

## Building

```sh
./gradlew build
```

The built jar is written to `build/libs/`.

## Development Notes

- Bee-system structure is documented in `docs/bee-system.md`.
- Player-facing apiculture loops are documented in `docs/apiculture-player-loop.md`.
- A player-facing bee guide is documented in `docs/player-bee-guide.md`.
- Village ecology is documented in `docs/village-ecology.md`.

## Known Limitations

- Bee simulation balance is still experimental and should be enabled deliberately per pack.
- Beekeeper relocation tools depend on Ecology colony data, so they are disabled while the bee simulation is off.
- Final textures and public release packaging are not done yet.

## License

All rights reserved.
