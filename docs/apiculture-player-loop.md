# Apiculture Player Loop

Ecology has two bee layers:

- **Entity simulation**: bee AI, hive ticking, roles, route learning, mating, swarming, age, and colony health.
- **Apiculture systems**: tools and feedback that let players understand and intentionally shape that simulation.

The player layer should always answer three questions:

1. What is happening in this hive?
2. What can I do next?
3. Why did this tool fail?

## Primary Tools

- **Inspection Tray**: live diagnosis. This is the best place for direct advice such as missing queen, low workers, low forage, unmated queen, overcrowding, or swarm readiness.
- **Inspection Focus**: the tray labels the current player loop as relocation setup, colony recovery, population growth, breeding and splitting, honey and pollination, or monitoring.
- **Beekeeper's Journal**: longer-term memory. It records health, population, traits, treatments, and swarm readiness so players can compare colonies over time.
- **Beekeeper's Knife, Brood Comb, Worker Bee**: relocation loop. These move a nest colony into a player hive while preserving queen age, workers, drones, lineage, inbreeding, and traits.
- **Queen Cell and Swarm Lure**: breeding and expansion loop. Queen cells are controlled daughter colonies; swarm lures split a ready colony into a nearby empty hive.
- **Apiary Smoker, Hive Stand, Queen Excluder, Brood Frame**: management loop. These calm, support, constrain, or encourage colony growth.

## Design Rule

Simulation can stay complex, but player tools should never return a vague failure if the code knows the reason. Entity helpers should expose readiness states; `BeekeeperActions` and `BeekeeperAdvice` should turn those states into item feedback and next-step guidance.
