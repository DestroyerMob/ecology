# Bee System Layout

The bee system is intentionally split between a stable facade and focused helpers.

## Core Facade

`EcologyBeeSystem` is the compatibility layer used by goals, events, items, commands, and mixins. Keep public entry points there when outside code already calls them, but avoid adding new feature logic directly to the class unless it is truly shared AI/pathing glue.

Good candidates to keep in `EcologyBeeSystem`:

- Bee memory access and hive colony access.
- Worker route assignment and local flower/crop searches.
- Hive entry, release timing, and stored-bee conversion.
- Public facade methods that delegate to focused helpers.

## Focused Bee Helpers

- `ColonyTraits`: trait serialization, starting traits, mutation, trait-based route/lifespan/agitation modifiers.
- `ColonySwarming`: simulation rules for swarm readiness, natural swarms, queen-cell creation, and queen-cell installation.
- `ColonyTreatments`: apiary smoker, hive stand, queen excluder, and brood frame behavior.
- `ColonyRelocationData`: brood-comb/captured-worker snapshot NBT and relocation birth-day/origin handling.
- `BeekeeperActions`: player-tool readiness checks for queen cells and swarm lures, mapping simulation states into clear failure/success messages.
- `BeekeeperAdvice`: inspection-tray focus and next steps derived from colony health, swarm readiness, forage, and treatments.
- `BeeDataKeys`: shared NBT key names for bee items and journal snapshots.
- `BeeText`: shared health color/status and trait-list formatting for Journal, Jade, tooltips, and inspection tools.

## Layer Boundary

Entity helpers should answer simulation questions: is the colony healthy, what role is needed, can it swarm, what data should move with a daughter colony. Beekeeper helpers should answer player questions: what can I do next, why did this item fail, and what action would fix the hive.

Avoid putting translation keys, chat phrasing, or player advice directly into AI/pathing/simulation helpers. Return a small state enum from simulation code, then map it to `BeekeeperActionCheck` or `BeekeeperAdvice`.

## Feature Flow

1. Hive ticking calls `EcologyBeeSystem.tickHiveColony`.
2. Daily colony simulation handles aging, decline, brood production, and delegates swarming to `ColonySwarming`.
3. Worker AI goals use `EcologyBeeSystem` route APIs; trait modifiers come from `ColonyTraits`.
4. Items call the facade where possible, then beekeeper helpers translate simulation state into tool feedback.
5. Journal, Jade, and inspection tools use `BeeText` so display formatting remains consistent.

When adding a new bee subsystem, add a focused helper first and expose only a small facade method if goals, events, items, commands, or mixins need to call it.
