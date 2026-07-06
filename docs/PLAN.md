# OpenSkyBlock Development Plan

## Research Snapshot

Primary systems to model:

- Persistent profiles, co-op membership, purse, bank, personal stats, SkyBlock level, and progression rewards.
- Private islands, island upgrades, visitors, guests, permissions, island NPCs, farms, portals, and placed minions.
- Skills: Farming, Mining, Combat, Foraging, Fishing, Enchanting, Alchemy, Carpentry, Taming, Social, Runecrafting, Dungeoneering, and special-area skills where applicable.
- Collections: material-specific progression tiers unlocking recipes, minions, trades, utility items, and stat rewards.
- Minions: placed workers with fuel, upgrades, storage, skins, generation timers, offline progress, and collection credit.
- Custom items: rarity, recombobulation-style rarity changes, enchantments, reforges, stars, gemstone slots, abilities, cooldowns, soulbinding, museum value, and NPC sell value.
- Economy: purse, bank, NPC shops, auction house, bazaar, trades, fees, escrow, order books, and anti-duplication invariants.
- Combat: custom stats, damage formula, health/defense/effective-health math, strength, crit chance, crit damage, ability damage, ferocity, magic find, pet luck, attack speed, invulnerability windows, and custom mobs.
- Islands and zones: hub, farming islands, mining islands, combat islands, foraging zones, dungeon hub, Crimson Isle-style combat zones, Garden-style farming area, and Rift-style alternate progression.
- Bosses and activities: dragons, slayers, dungeons, Kuudra-style encounters, seasonal events, Jacob-style farming contests, commissions, experiments, races, and dojo-style challenges.
- Player power systems: pets, accessories, accessory bag, magical power, tunings, equipment, wardrobe, armor sets, drills, arrows, sacks, quiver, potion effects, god potions, cakes, and account upgrades.
- User interfaces: SkyBlock menu, profile viewer, recipe book, collection menu, skill menu, quest log, bank, auction house, bazaar, trades, storage, ender chest, pets, wardrobe, and island management.

Useful reference categories:

- PaperMC API and plugin metadata documentation for current Paper plugin structure and server integration.
- Adventure MiniMessage documentation for all player-facing text.
- Hypixel SkyBlock wiki pages and community documentation for gameplay mechanics and content taxonomy.
- Open-source SkyBlock-like plugins for implementation patterns only, not for copying incompatible assets or code.

Verified reference links:

- Paper development guide: https://docs.papermc.io/paper/dev/
- Adventure MiniMessage guide: https://docs.papermc.io/adventure/minimessage/
- Paper API Maven metadata: https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml
- Official Hypixel SkyBlock wiki main page: https://wiki.hypixel.net/Main_Page
- Official Hypixel SkyBlock introduction: https://wiki.hypixel.net/Introduction
- Official collections reference: https://wiki.hypixel.net/Collections
- Official minions reference: https://wiki.hypixel.net/Minions
- Official SkyBlock levels reference: https://wiki.hypixel.net/SkyBlock_Levels
- Official sacks reference: https://wiki.hypixel.net/Sacks
- Official enchantments reference: https://wiki.hypixel.net/Enchantments
- Official essence and item star upgrade reference: https://wiki.hypixel.net/Essence
- Official gemstones reference: https://wiki.hypixel.net/Gemstones
- Official equipment reference: https://wiki.hypixel.net/Equipment
- Official wardrobe reference: https://wiki.hypixel.net/Wardrobe
- Official pets reference: https://wiki.hypixel.net/Pets
- Official quiver reference: https://wiki.hypixel.net/Quiver
- Official reforging reference: https://wiki.hypixel.net/Reforging
- Official auction house reference: https://wiki.hypixel.net/Auction_House
- Official bazaar reference: https://wiki.hypixel.net/Bazaar
- Official NPC reference: https://wiki.hypixel.net/NPCs
- Paper custom inventory holder reference: https://docs.papermc.io/paper/dev/custom-inventory-holder/
- Paper EntityPickupItemEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/EntityPickupItemEvent.html
- Paper EntityShootBowEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/EntityShootBowEvent.html
- Paper persistent data container reference: https://docs.papermc.io/paper/dev/pdc/
- MyPet Spigot/Paper pet plugin reference: https://github.com/MyPetORG/MyPet

## Library Choices

Initial implementation:

- Paper API `1.21.8-R0.1-SNAPSHOT` with Java 21.
- Adventure MiniMessage through Paper's bundled Adventure API.
- Bukkit `YamlConfiguration` for lightweight editable configuration and profile storage.

Planned additions when each subsystem needs them:

- MariaDB or PostgreSQL plus HikariCP for production profile, auction, bazaar, and island data.
- Redis for cross-server cache, pub/sub, auction and bazaar invalidation, and profile locks.
- WorldEdit/FastAsyncWorldEdit adapter for island schematic placement.
- Cloud command framework or Incendo Cloud for large command trees.
- PacketEvents or ProtocolLib only where Paper API cannot represent required UI or visual behavior.
- MiniPlaceholders integration for server-owner configurable dynamic text.

## Milestones

1. Foundation
   - Plugin bootstrap, configuration, MiniMessage text service, profile persistence, commands, and event listeners.
   - Skill XP, collection progression, item metadata, and basic custom item stats.
   - Minion definitions, placeable minion items, persisted locations, storage, offline ticking, claim menus, and pickup flow.

2. Private Islands
   - World allocation, island border, spawn, reset flow, cooperative profiles, permissions, visitors, warps, schematic placement, and protected interaction rules.
   - Minion placement blocks, fuel, storage, upgrades, offline generation, and collection credit.

3. Menus and Recipes
   - SkyBlock menu, paged recipe book, paged collection menu, skill menu, bank menu, profile viewer, and minion UI.
   - Recipe registration, shaped recipes, crafting requirements, and unlock checks.
   - Forge-style timers and advanced recipe categories remain future work.

4. Stats and Combat
   - Base stat configuration, equipment stat aggregation, accessory item stat aggregation, player stat display, and basic combat damage/defense formulas are implemented.
   - Accessory Bag storage, unique accessory counting, Magical Power by rarity, and bagged accessory stat aggregation are implemented.
   - Accessory tuning with Magical Power-derived tuning points and configurable per-stat values is implemented.
   - Configurable armor set IDs and full-set stat bonuses are implemented.
   - Configurable item reforges with item metadata persistence, rarity-scaled stat tables, purse costs, and stat/lore integration are implemented.
   - Configurable item enchantments with item metadata persistence, max levels, ultimate-enchantment exclusivity, purse costs, and stat/lore integration are implemented.
   - Configurable item stars with item metadata persistence, category limits, purse costs, display suffix/lore, and item stat scaling are implemented.
   - Configurable gemstone slots with item metadata persistence, item/category slot rules, tiered gemstone stat bonuses, purse costs, and stat/lore integration are implemented.
   - Configurable equipment slots with profile persistence, equipment-slot item metadata, GUI equip/unequip flow, and stat aggregation are implemented.
   - Configurable Wardrobe slots with persistent armor setup storage, GUI swap/withdraw flow, and command access are implemented.
   - Configurable Sacks with persistent material storage, carry-item access checks, automatic pickup routing, GUI deposit/withdraw flow, and command access are implemented.
   - Configurable Quiver storage with automatic arrow pickup, selected arrow type, bow-shot proxy consumption, GUI controls, and command access is implemented.
   - Configurable pet definitions, persistent owned pets, active pet selection, pet XP/levels, pet menu activation, and active pet stat aggregation are implemented.
   - Pet items, pet score, autopet rules, cosmetic summoned pet entities, advanced reforge stones, reforge anvil UI, vanilla/SkyBlock-specific enchantment effects, enchanting table/anvil UI, essence currencies, dungeon-only star scaling, gemstone slot unlocking, gemstone item consumption, potion modifiers, and ability cooldowns remain future work.
   - Custom mobs, loot tables, spawn zones, boss bars, damage formula, aggro, and death rewards.

5. Economy
   - Base purse/bank deposit and withdraw flow is implemented.
   - Configurable NPC shop buy/sell flow with daily buy limits is implemented.
   - Optional physical shop NPC spawning and click interaction is implemented.
   - Bank upgrades, trading, auction house, bazaar order books, escrow, claim queues, taxes, anti-duplication checks, and audit logging remain future work.

6. World Content
   - Hub and resource islands, Garden-like farming progression, Deep Caverns/Dwarven-style mining progression, combat islands, foraging islands, fishing content, seasonal events, and commissions.

7. Advanced Activities
   - Slayers, dragons, dungeons, party finder, class XP, dungeon item stars, boss loot chests, Kuudra-style waves, Rift-like alternate rules, experiments, contests, races, and event scheduling.

8. Production Readiness
   - Database migrations, cross-server synchronization, backups, rate limits, exploit prevention, permissions, diagnostics, metrics, integration tests, load tests, and release automation.

## Configuration Standard

- Every player-facing string must be configurable and written as MiniMessage.
- Configuration keys must use English values by default.
- Feature content should be data-driven before being hard-coded.
- Server owners should be able to disable major systems independently as implementation matures.
- Each new subsystem or subfeature implementation should first check current online documentation, gameplay references, and relevant existing plugin patterns, then record durable links when they influence the implementation.

## Current Implementation Boundary

The current codebase is a foundation, not full parity. It proves the project compiles and establishes the core contracts for profiles, private island creation, basic island protection, configurable SkyBlock menu GUI, paged collection and recipe book menus, starter menu item flow, purse/bank movement, configurable NPC shops with optional physical NPCs, configurable Sacks, configurable Quiver storage, configurable action coin rewards, skill XP, collections, custom item metadata, collection-gated recipes, stat aggregation, Accessory Bag, Magical Power, accessory tuning, equipment slots, Wardrobe armor storage, armor set bonuses, item reforges, item enchantments, item stars, gemstone slots, configurable active pets, basic combat formulas, and placeable minions with stored generation. Full feature parity remains open until every milestone above is implemented, tested, and documented.
