# Village Ecology

Village Ecology is the first non-bee settlement system in Ecology. It gives villages a readable health score and lets villagers perform small, slow upkeep tasks when the feature is enabled.

## Player Loop

1. Craft or obtain a Village Ledger.
2. Use it near a village, on a bell, or on village blocks.
3. Read the village score, category scores, needs, and advice.
4. Improve weak categories with farms, beds, paths, water, flowers, lighting, bells, job blocks, and golems.
5. Re-survey after changes.

Ecology does not require naturally generated villages. Player-made villages work when they have the normal pieces villagers understand, such as beds, a bell or gathering space, safe paths, work areas, and optionally marked market stalls.

## Survey Categories

- Food: crops, mature crops, composters, water, and farmers.
- Shelter: beds, doors, job blocks, and bells.
- Safety: iron golems, bells, lighting, and hostile pressure.
- Green space: flowers, saplings, and leaves.
- Water: nearby water blocks.
- Upkeep: paths, composters, bells, job blocks, and empty farmland penalties.

The total score becomes one of four statuses: Neglected, Struggling, Stable, or Thriving.

## Village Ledger

The Village Ledger is the player-facing inspection tool, not the place where village data lives. Ecology stores each village's hidden ecology, supply, welfare, vocation, and stall state in the world automatically; the crafted ledger lets the player inspect that state, donate resources, and mark market stalls.

Using the ledger near a village prints a chat report with:

- Overall score and status.
- Villager, golem, and bed counts.
- Category scores.
- Crop, mature crop, flower, water, and path counts.
- Needs and advice.
- Village supply levels and daily trends when village supplies are enabled.

The ledger can also accept supply donations. Crouch-use the ledger while holding a donation item in the other hand to add that item to the nearest village supply account. Food, wood, stone, metal, paper, cloth, tools, medicine, and valuables are recognized.

## Village Supplies

Village supplies are a lightweight economy layer behind normal villager trading. They do not create a shared shop and do not make villagers pathfind out to gather every item. Instead, Ecology stores an abstract supply account for each village area.

- Villagers still trade through their normal trade screens.
- Each village tracks food, wood, stone, metal, paper, cloth, tools, medicine, and valuables.
- Village professions and ecology scores create daily supply gains and costs.
- Trades that sell goods to the player draw from the relevant supply.
- Trades where the player sells useful goods to a villager add to the relevant supply.
- Low supply reduces the max uses of related sell trades, so those trades exhaust faster.
- High supply increases the max uses of related sell trades.
- Low supply increases the capacity of buy trades, so villagers are more willing to buy what the village lacks.
- Unloaded villages catch up lazily the next time their ledger, villagers, or trades are touched. Catch-up is capped so it cannot become an infinite resource printer.

The goal is that a strong farming village can support food trades, a well-built artisan village can support tools and paper, and a player can restore weak areas by building infrastructure or donating resources.

## Market Welfare

Trading halls are supported as market districts, but Ecology does not reward captive villagers. A healthy market is a place a villager can work and trade while still being able to reach village life.

- Villagers should be able to reach a home or bed area and a meeting space such as a bell.
- Compact stalls are fine when villagers can leave and return.
- Repeatedly confined villagers lose Ecology market benefits.
- Confined villagers do not contribute profession supply production.
- Trading with confined villagers does not add to the village supply ledger.
- Confined villagers receive rising special price penalties until access improves.
- The Village Ledger reports confined traders as a market issue.

The check is deliberately slow and forgiving. A single failed path or temporary obstruction does not immediately punish the player; pressure builds after repeated failed welfare checks and falls again when the villager can reach civic space.

## Relocating Villagers

Villagers can be adopted into a player-built village after the player physically brings them there. Ecology does not teleport villagers; it changes which civic space they remember as home.

1. Build the new village with at least a bell, beds, safe paths, and work areas.
2. Crouch-use the Village Ledger on the village bell.
3. Use the marked ledger on a villager.
4. If the villager is far away, it begins following the player toward the marked village.
5. When the villager reaches the marked bell area, Ecology adopts it into the village.

Vanilla transport still works, including boats, minecarts, nether routes, curing a zombie villager near the new village, or breeding villagers in place. Guided relocation is the player-friendly path for moving a specific villager without building rails for every move. Crouch-use the ledger on a guided villager to stop guiding it.

The villager follows the player while it can path to them. Once it reaches the marked village, it remembers the marked bell as its meeting point, forgets old home and job memories, and tries to bind to nearby beds and matching workstations. This also lets the villager inherit the new village's currency and supply account.

## Market Stalls

The Village Ledger can plan a trading hall without physically trapping villagers. A normal ledger click on a villager still opens that villager's trade screen; the ledger only takes over villager interaction when it has a marked stall to assign, or when the player crouch-uses it to clear an assigned stall.

1. Build a stall tile that the villager can stand on and path to.
2. Crouch-use the Village Ledger on the stall tile with your other hand empty.
3. Use the ledger on the villager you want assigned to that stall.
4. During work hours, Ecology nudges the villager toward the assigned stall when pathing allows.
5. Crouch-use the ledger on an assigned villager to clear that villager's stall.

Assigned stalls are saved on the villager. They do not replace the villager's home, meeting point, or village life; they give the villager a known market workplace. A villager with an assigned stall still needs to reach home and meeting space to avoid welfare pressure.

## Households And Homes

When village households are enabled, Ecology tracks family homes, parent hints, household savings, and adult children moving into open houses. The goal is to make villages feel inhabited without asking villagers to run heavy building or gathering AI every tick.

- Villagers form households as they settle, partner, trade, and have children.
- A household's home is stored as a nearby bed, but Ecology counts beds by bed objects rather than by both bed blocks.
- Existing generated houses are preferred. If a household is crowded and has savings, Ecology first tries to add one or two extra beds inside clear, sheltered space near that household's current bed.
- If no suitable upgrade space exists, crowded households can fund a new house on a player-approved plot.
- New houses use vanilla village house structure templates for the local village style, such as plains, desert, savanna, taiga, or snowy.
- House plot banners set the completed home's household banner color. Existing-home upgrades use a stable household color.
- Claimed homes get an Ecology-managed deed sign near the household bed. The sign shows the named occupants, while the nearby banner provides a color marker for the household.
- Use the Village Ledger on a deed sign to read that household's current occupants, savings, partner links, parents, and children.
- Ecology requires Villager Names for this feature so households can be read as people rather than anonymous villager records.

To approve a new house plot:

1. Crouch-use the Village Ledger on the village bell.
2. Hold a banner in the other hand.
3. Use the ledger on one plot corner.
4. Use the ledger on the opposite corner.
5. Keep the plot clear and mostly level. The plot must fit a vanilla village house and stay within the configured maximum size.

Adult children still prefer moving into an empty home. If none is available, approved plots let the village expand with homes that match vanilla village architecture instead of custom Ecology boxes.

## Tradeboards

Tradeboards let players define village shop offers without trapping villagers or injecting permanent free stock. A tradeboard is a wall-mounted rectangle of `ecology:tradeboard` blocks. It can be any filled rectangle from 1x1 up to 15x15. Each board tile can define one offer.

1. Place a filled rectangular tradeboard.
2. Use an item stack on a board tile to set what the villager sells for that slot. The stack count becomes the per-trade output count.
3. Use the village currency on that tile to set the price. The currency stack count becomes the cost.
4. Crouch-use a Village Ledger on the tradeboard.
5. Crouch-use the same ledger on an input inventory. The villager removes sold stock from this inventory.
6. Crouch-use the same ledger on a different output inventory. The villager deposits paid currency here.
7. Use the completed ledger on an adult, non-confined villager.

The villager's trade screen generates player-stocked offers from the assigned board and the current input inventory contents. If the input inventory runs out, the offer disappears the next time trades refresh. Tradeboard offers do not draw from village supplies and do not receive high-supply bonus uses.

Confined villagers do not use tradeboards. A custom player trade belongs in a working market, with the villager still able to reach village life.

## Village Vocations

When village vocations are enabled, Ecology helps jobless adult villagers settle into professions instead of waiting for workstation races to decide every identity. The system does not overwrite nitwits, babies, or villagers that already have a profession.

- New adult villagers can choose a profession from stored parent hints, village needs, and a smaller random pool.
- Baby villagers remember their parents' professions and have a higher chance to continue one of those lines when they grow up.
- Food problems favor farmers, fishermen, and butchers.
- Safety problems favor armorers, weaponsmiths, toolsmiths, and clerics.
- Shelter and upkeep problems favor practical village builders such as masons and toolsmiths.

Workstations still matter as village infrastructure and restock anchors, but Ecology can give the villager a vocation before a block happens to claim them first.

## Village Currency

When village currencies are enabled, each village uses one available currency. Emerald is always available. Ruby and sapphire become available only when another mod or datapack supplies items through Ecology's village currency tags or common gem tags. The currency is assigned from the village anchor area, so villagers in the same settlement share the same trade currency and base clothing.

- Emerald villages behave like vanilla villages.
- Ruby villages use a tagged ruby item in every villager offer that would normally use emerald.
- Sapphire villages use a tagged sapphire item in every villager offer that would normally use emerald.
- Babies inherit the village currency visually, so a settlement should not show mixed base villager currencies.

The system converts existing villager offers when trades are generated or opened, so saved villagers can move onto their village currency without rebuilding the vanilla trade tables.

Compatibility tags, in priority order, are `ecology:village_currency/ruby`, `c:gems/ruby`, `forge:gems/ruby`, and the matching sapphire variants. If a ruby or sapphire tag is empty, that currency is skipped and villages will not select it.

## Villager Upkeep

When village maintenance is enabled, adult villagers occasionally attempt one small upkeep action:

- Farmers can replant empty farmland with wheat.
- Villagers can repair small dirt or grass gaps next to existing dirt paths.
- Villagers can add a small flower near paths, bells, or composters.

These actions are deliberately small and slow. They are meant to make cared-for villages feel maintained, not to automate building.

## Golem Construction

Village ecology lightly affects the existing golem construction feature:

- Thriving villages can begin construction with one fewer required participant, down to a minimum of three.
- Thriving villages build each construction step a little faster.
- Neglected villages build each construction step a little slower.

Vanilla-safe presets keep village ecology disabled. In pack presets that opt into Ecology systems, the feature is enabled by default.
