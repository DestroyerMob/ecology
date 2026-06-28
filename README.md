# Ecology

Ecology is a NeoForge 1.21.1 mod for opt-in ecosystem experiments. Its current focus is advanced bee colony behavior, beekeeper tools, and a small village iron golem construction pass.

## Current Status

Internal playtesting. The advanced bee simulation is implemented but intentionally disabled by default so adding the mod does not change vanilla bee AI, hive capacity, or hive release timing unless a pack explicitly opts in.

## Current Features

- Advanced bee simulation with worker, drone, and queen roles.
- Colony data stored on hives, including queen state, worker/drone counts, aging, reproduction, inbreeding pressure, and unloaded-chunk catch-up.
- Worker bee flower/crop routes with optional route defense behavior.
- Drone mating and queen migration goals.
- Configurable bee lifespans, route ranges, hive capacity, and daily simulation limits.
- Wax Goggles and Debug Wax Goggles for route visualization.
- Hive Day Simulator for advancing a hive by one simulated day during testing.
- Beekeeper's Knife, Brood Comb, captured worker bees, and honeycomb sealing for moving a cut-out nest into a player hive.
- JEI descriptions for bee tools and Jade providers for bee/hive diagnostics.
- Villager iron golem construction, where villagers build the structure before the golem appears.
- Debug commands for bee nest setup and village golem construction testing.

## Configuration

The common config is `ecology-common.toml`.

- `enableBeeSystem=false` by default. This is the master switch for advanced bee AI, colony ticking, hive data, hive capacity changes, release timing changes, route networking, and bee relocation items.
- `enableHiveColonyTicking=true`, `replaceVanillaBeeGoals=true`, `enableBeeLifespanDeath=true`, `enableDroneMatingGoal=true`, and `enableQueenMigrationGoal=true` only matter after `enableBeeSystem` is turned on.
- `enableBeeRelocationItems=true` controls beekeeper knife, brood comb, captured worker bee, honeycomb nest sealing, and hive day simulator interactions. It also requires `enableBeeSystem` and `enableHiveColonyTicking`.
- `enableVillagerGolemConstruction=true` controls the village golem construction feature.
- `debugBeeSystemLogging=false` and `debugVillagerGolemConstruction=false` keep noisy diagnostics out of normal play.

With the defaults, vanilla bees remain vanilla. To test the bee simulation, enable `enableBeeSystem` and restart the game.

## Supported Versions

- Minecraft 1.21.1
- NeoForge 21.1.234
- Java 21

## Building

```sh
./gradlew build
```

The built jar is written to `build/libs/`.

## Known Limitations

- Bee simulation balance is still experimental and should be enabled deliberately per pack.
- Beekeeper relocation tools depend on Ecology colony data, so they are disabled while the bee simulation is off.
- Final textures and public release packaging are not done yet.

## License

All rights reserved.
