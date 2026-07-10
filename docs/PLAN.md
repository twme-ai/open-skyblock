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
- Official SkyBlock Menu reference: https://wiki.hypixel.net/SkyBlock_Menu
- Official Private Island reference: https://wiki.hypixel.net/Private_Island
- Official Teleport Pad reference: https://wiki.hypixel.net/Teleport_Pad
- Official profile reference: https://wiki.hypixel.net/Profile
- Community co-op reference: https://hypixelskyblock.minecraft.wiki/w/Co-op
- Community quests and chapters reference: https://hypixelskyblock.minecraft.wiki/w/Quests_%26_Chapters
- Official skills reference: https://wiki.hypixel.net/Skills
- Official collections reference: https://wiki.hypixel.net/Collections
- Official minions reference: https://wiki.hypixel.net/Minions
- Official minion fuel reference: https://wiki.hypixel.net/Minion_Fuel
- Official minion upgrades reference: https://wiki.hypixel.net/Minion_Upgrades
- Official minion storage reference: https://wiki.hypixel.net/Minion_Storage
- Official Compactor reference: https://wiki.hypixel.net/Compactor
- Official Budget Hopper reference: https://wiki.hypixel.net/Budget_Hopper
- Official Enchanted Hopper reference: https://wiki.hypixel.net/Enchanted_Hopper
- Official SkyBlock levels reference: https://wiki.hypixel.net/SkyBlock_Levels
- Official sacks reference: https://wiki.hypixel.net/Sacks
- Official enchantments reference: https://wiki.hypixel.net/Enchantments
- Official Enchantment Table reference: https://wiki.hypixel.net/Enchantment_Table
- Official Anvil reference: https://wiki.hypixel.net/Anvil
- Official essence and item star upgrade reference: https://wiki.hypixel.net/Essence
- Official Recombobulator 3000 reference: https://wiki.hypixel.net/Recombobulator_3000
- Official gemstones reference: https://wiki.hypixel.net/Gemstones
- Official equipment reference: https://wiki.hypixel.net/Equipment
- Official wardrobe reference: https://wiki.hypixel.net/Wardrobe
- Official storage reference: https://wiki.hypixel.net/Storage
- Official backpack reference: https://wiki.hypixel.net/Backpacks
- Official pets reference: https://wiki.hypixel.net/Pets
- Official pet score reference: https://wiki.hypixel.net/Pets#Pet_Score
- Official pet items reference: https://wiki.hypixel.net/Pet_Items
- Official Autopet reference: https://wiki.hypixel.net/Autopet
- Official Pet Luck reference: https://wiki.hypixel.net/Pet_Luck
- Official Diana reference: https://wiki.hypixel.net/Diana
- Official quiver reference: https://wiki.hypixel.net/Quiver
- Official God Potion reference: https://wiki.hypixel.net/God_Potion
- Official potions reference: https://wiki.hypixel.net/Potions
- Official Century Cakes reference: https://wiki.hypixel.net/Century_Cakes
- Official Elizabeth and Community Shop reference: https://wiki.hypixel.net/Elizabeth
- Official profile upgrades reference: https://wiki.hypixel.net/Profile
- Community account/profile upgrade reference: https://hypixelskyblock.minecraft.wiki/w/Account_%26_Profile_Upgrades
- Official reforging reference: https://wiki.hypixel.net/Reforging
- Official Reforge Anvil reference: https://wiki.hypixel.net/Reforge_Anvil
- Official Reforge Stones reference: https://wiki.hypixel.net/Reforge_Stones
- Official auction house reference: https://wiki.hypixel.net/Auction_House
- Official bazaar reference: https://wiki.hypixel.net/Bazaar
- Official trading reference: https://wiki.hypixel.net/Trading
- Official NPC reference: https://wiki.hypixel.net/NPCs
- Official mobs reference: https://wiki.hypixel.net/Mobs
- Official Sea Creature Chance reference: https://wiki.hypixel.net/Sea_Creature_Chance
- Official Marina reference: https://wiki.hypixel.net/Marina
- Official Bestiary reference: https://wiki.hypixel.net/Bestiary
- Official slayer reference: https://wiki.hypixel.net/Slayer
- Official Mining Fortune reference: https://wiki.hypixel.net/Mining_Fortune
- Official Farming Fortune reference: https://wiki.hypixel.net/Farming_Fortune
- Official Foraging Fortune reference: https://wiki.hypixel.net/Foraging_Fortune
- Open-source Bazaar plugin reference: https://github.com/MatejLorinc/bazaar
- Paper ItemStack API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/inventory/ItemStack.html
- Paper YamlConfiguration API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/configuration/file/YamlConfiguration.html
- Paper PlayerQuitEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/player/PlayerQuitEvent.html
- Paper InventoryCloseEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/inventory/InventoryCloseEvent.html
- Paper Inventory API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/inventory/Inventory.html
- Paper custom inventory holder reference: https://docs.papermc.io/paper/dev/custom-inventory-holder/
- Paper teleportation reference: https://docs.papermc.io/paper/dev/entity-teleport/
- Paper Location API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/Location.html
- Paper InventoryClickEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/inventory/InventoryClickEvent.html
- Paper EntityPickupItemEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/EntityPickupItemEvent.html
- Paper EntityDeathEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/EntityDeathEvent.html
- Paper World entity spawning API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/World.html
- Paper EnderDragon API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/EnderDragon.html
- Paper EnderDragon phase API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/EnderDragon.Phase.html
- Paper DragonFireball API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/DragonFireball.html
- Paper ProjectileHitEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/ProjectileHitEvent.html
- Existing configurable Ender Dragon plugin reference: https://github.com/iXanadu13/EnderDragon
- Adventure BossBar API reference: https://jd.advntr.dev/api/latest/net/kyori/adventure/bossbar/BossBar.html
- Paper WorldBorder API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/WorldBorder.html
- Paper Server world unloading API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/Server.html
- Java Files recursive deletion API reference: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html
- Paper Scheduler API reference: https://docs.papermc.io/paper/dev/scheduler/
- Paper Attribute API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/attribute/Attribute.html
- Paper EntityShootBowEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/EntityShootBowEvent.html
- Paper PotionEffect API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/potion/PotionEffect.html
- Paper LivingEntity potion API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/LivingEntity.html
- Paper BlockPlaceEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/block/BlockPlaceEvent.html
- Paper BlockBreakEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/block/BlockBreakEvent.html
- Paper BlockFace API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/block/BlockFace.html
- Paper Block API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/block/Block.html
- Paper PlayerInteractEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/player/PlayerInteractEvent.html
- Paper PlayerMoveEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/player/PlayerMoveEvent.html
- Paper PlayerItemHeldEvent API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/player/PlayerItemHeldEvent.html
- Paper ArmorStand API reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/ArmorStand.html
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
   - Minion definitions, placeable minion items, persisted locations, generated storage, adjacent external storage blocks, configurable cosmetic skins, configurable fuel, configurable upgrade slots, compactor-style output conversion, hopper-style overflow selling, offline ticking, claim menus, and pickup flow.

2. Private Islands
   - World allocation, configurable world border, spawn, generated starter platforms, optional WorldEdit/FastAsyncWorldEdit starter schematic placement, saved island home, named island warps, block-based Teleport Pad linking, guarded reset flow, visitor access toggles, island visiting, basic co-op invites/members, configurable co-op action permissions, per-member co-op permission roles, island management UI, and protected interaction rules are implemented.
   - Fully shared cooperative profiles remain future work.
   - Minion placement blocks, adjacent external storage blocks, configurable cosmetic skins, configurable fuel, configurable upgrade slots, compactor-style output conversion, hopper-style overflow selling, generated storage, offline generation, and collection credit are implemented.

3. Menus and Recipes
   - Configurable SkyBlock menu, profile viewer, Quest Log, skill menu, paged category-filtered recipe book, paged collection menu, bank menu, and minion UI are implemented.
   - Recipe registration, shaped recipes, configurable recipe categories, collection-tier and Slayer-level crafting requirements, and unlock checks.
   - Deeper recipe category presentation and multi-output recipe types remain future work.

4. Stats and Combat
   - Base stat configuration, equipment stat aggregation, accessory item stat aggregation, player stat display, and basic combat damage/defense formulas are implemented.
   - Accessory Bag storage, unique accessory counting, Magical Power by rarity, and bagged accessory stat aggregation are implemented.
   - Accessory tuning with Magical Power-derived tuning points and configurable per-stat values is implemented.
   - Configurable armor set IDs and full-set stat bonuses are implemented.
   - Configurable item reforges with item metadata persistence, rarity-scaled stat tables, optional consumed Reforge Stones, purse costs, Reforge Anvil GUI, and stat/lore integration are implemented.
   - Configurable Recombobulator-style rarity upgrades with item metadata persistence, consumed upgrade items, lore rendering, and rarity-scaled reforge/star cost integration are implemented.
   - Configurable item enchantments with item metadata persistence, max levels, ultimate-enchantment exclusivity, purse costs, Enchanting Table GUI, Anvil enchanted-book combining, and stat/lore integration are implemented.
   - Configurable Essence balances and item stars with item metadata persistence, category limits, purse/Essence costs, held-item Essence salvage, display suffix/lore, and item stat scaling are implemented.
   - Configurable gemstone slots with item metadata persistence, item/category slot rules, tiered gemstone stat bonuses, purse costs, and stat/lore integration are implemented.
   - Configurable item abilities with right-click activation, cooldowns, basic mana regeneration, teleport, speed, heal, and nearby-damage actions are implemented.
   - Configurable equipment slots with profile persistence, equipment-slot item metadata, GUI equip/unequip flow, and stat aggregation are implemented.
   - Configurable Wardrobe slots with persistent armor setup storage, GUI swap/withdraw flow, and command access are implemented.
   - Configurable Storage pages with profile ItemStack persistence, command access, SkyBlock menu access, search, sorting, and inventory-close saving are implemented.
   - Configurable installed Backpacks with item metadata, profile ItemStack persistence, command access, right-click install flow, and inventory-close saving are implemented.
   - Configurable Sacks with persistent material storage, carry-item access checks, automatic pickup routing, GUI deposit/withdraw flow, and command access are implemented.
   - Configurable Quiver storage with automatic arrow pickup, selected arrow type, bow-shot proxy consumption, GUI controls, and command access is implemented.
   - Configurable Potion Effects and God Potion bundles with persistent online-only timers, private-island timer pause, vanilla effect refresh, item activation, stat bonuses, and command status are implemented.
   - Configurable Century Cakes with persistent placed cake locations, reusable right-click activation, visitor activation option, active buff timers, pickup flow, and stat aggregation are implemented.
   - Configurable profile/account upgrades with persistent tier levels, purchase costs, stat bonuses, bank-capacity bonuses, accessory-bag slot bonuses, and minion-slot bonuses are implemented.
   - Configurable pet definitions, persistent owned pets, active pet selection, pet XP/levels, pet menu activation, attachable pet items, Pet Score rewards, Autopet rules, cosmetic summoned pet entities, and active pet stat aggregation are implemented.
   - Vanilla/SkyBlock-specific enchantment effects, advanced Essence salvage menus/acquisition loops, dungeon-only star scaling, advanced potion modifiers, and advanced ability scripting remain future work.
   - Configurable custom mobs with level/health nameplates, stat-based damage and defense, admin spawning, kill rewards, configurable loot rolls, and rare drop announcements are implemented.
   - Configurable mob spawn zones with runtime caps, player activation checks, weighted mob selection, and periodic spawning are implemented.
   - Configurable Bestiary families with persistent kill counts, tier rewards, and stat aggregation are implemented.
   - Configurable Slayer quests with coin start costs, Combat XP progress, owner-tagged boss spawning, boss bars, timeouts, completion rewards, Slayer levels, milestone rewards, stat rewards, and persistent Slayer XP are implemented.
   - Aggro rules, advanced mob AI, Bestiary GUI polish, and boss encounters remain future work.

5. Economy
   - Base purse/bank deposit and withdraw flow is implemented.
   - Configurable NPC shop buy/sell flow with daily buy limits is implemented.
   - Optional physical shop NPC spawning and click interaction is implemented.
   - Configurable persistent Auction House GUI browsing plus BIN and bid listings with listing fees, bid escrow/refunds, direct buy/bid flow, cancellation rules, expired item returns, and seller/winner claim flow are implemented.
   - Configurable Bazaar GUI browsing plus products with persistent buy orders, sell offers, instant buy/sell matching, escrow refunds, cancellation, and claim flow are implemented.
   - Secure player trading with configurable trade menu, request/accept flow, item and coin escrow, review/confirm steps, cancellation, and disconnect cleanup is implemented.
   - Deeper Bazaar category filters, deeper escrow/claim queues, taxes, anti-duplication checks, market analytics, and audit logging remain future work.

6. World Content
   - Hub and resource islands, Garden-like farming progression, Deep Caverns/Dwarven-style mining progression, combat islands, foraging islands, fishing content, seasonal events, and commissions.

7. Advanced Activities
   - Advanced Slayer boss mechanics beyond owner tracking/timeouts, additional Ender Dragon arena/environment mechanics beyond configurable health phases and variant abilities, dungeon party finder, deeper class mechanics, dungeon item conversion/stars, boss loot chest presentation, Kuudra-style live waves, deeper Rift rules, interactive experiment GUIs, races, and deeper event scheduling.

8. Production Readiness
   - Database migrations, cross-server synchronization, backups, rate limits, exploit prevention, permissions, diagnostics, metrics, integration tests, load tests, and release automation.

## Configuration Standard

- Every player-facing string must be configurable and written as MiniMessage.
- Configuration keys must use English values by default.
- Feature content should be data-driven before being hard-coded.
- Server owners should be able to disable major systems independently as implementation matures.
- Each new subsystem or subfeature implementation should first check current online documentation, gameplay references, and relevant existing plugin patterns, then record durable links when they influence the implementation.

## Reference Links

- Paper custom recipe documentation: https://docs.papermc.io/paper/dev/recipes/
- Hypixel SkyBlock Recipe Book reference: https://hypixelskyblock.minecraft.wiki/w/Recipe_Book
- Paper event listener documentation: https://docs.papermc.io/paper/dev/event-listeners/
- Hypixel SkyBlock Private Island reference: https://hypixelskyblock.minecraft.wiki/w/Private_Island
- Hypixel Ender Dragon reference: https://wiki.hypixel.net/Ender_Dragon
- Hypixel Summoning Eye reference: https://wiki.hypixel.net/Summoning_Eye
- Paper EnderDragon phase reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/EnderDragon.Phase.html
- Paper DragonFireball reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/DragonFireball.html
- Paper ProjectileHitEvent reference: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/ProjectileHitEvent.html
- Existing configurable Ender Dragon plugin reference: https://github.com/iXanadu13/EnderDragon
- Hypixel SkyBlock Co-op reference: https://hypixelskyblock.minecraft.wiki/w/Co-op
- Bukkit command tab completion reference: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/command/TabCompleter.html
- Bukkit configuration section reference: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/configuration/ConfigurationSection.html
- WorldEdit clipboard and schematic API reference: https://worldedit.enginehub.org/en/latest/api/examples/clipboard/
- WorldEdit edit session API reference: https://worldedit.enginehub.org/en/latest/api/concepts/edit-sessions/
- Paper plugin dependency metadata reference: https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
- FastAsyncWorldEdit documentation reference: https://intellectualsites.github.io/fastasyncworldedit-documentation/

## Current Implementation Boundary

The current codebase is a foundation, not full parity. It proves the project compiles and establishes the core contracts for profiles, private island creation, configurable world border, generated starter platforms, optional WorldEdit/FastAsyncWorldEdit starter schematic placement, saved island home, named island warps, block-based Teleport Pads, guarded island reset flow, visitor access toggles, configurable visitor limits, island visiting, basic co-op invites/members, configurable co-op action permissions, per-member co-op permission roles, island management GUI, basic island protection, configurable SkyBlock menu GUI, configurable profile viewer GUI, configurable Quest Log GUI, configurable skill menu, paged collection and category-filtered recipe book menus, starter menu item flow, purse/bank movement, configurable NPC shops with optional physical NPCs, configurable Auction House GUI browsing plus BIN and bid listings, configurable Bazaar GUI browsing plus order books, secure configurable trade menu flow, configurable Storage pages with search and sorting, configurable installed Backpacks, configurable SkyBlock mobs, loot rolls, and rare drop announcements, configurable mob spawn zones, configurable Bestiary progression, configurable Slayer quests with owner-tagged timed bosses and Slayer level rewards, configurable Sacks, configurable Quiver storage, configurable Potion Effects and God Potion bundles, configurable Century Cakes, configurable profile/account upgrades, configurable action coin rewards, skill XP, collections, custom item metadata, item ability activation, collection-gated and Slayer-gated recipes, stat aggregation, Accessory Bag, Magical Power, accessory tuning, equipment slots, Wardrobe armor storage, armor set bonuses, item reforges with optional Reforge Stone consumption and Reforge Anvil GUI, Recombobulator-style rarity upgrades, item enchantments with Enchanting Table GUI and Anvil enchanted-book combining, item stars with persistent Essence balances, configurable Essence costs, and held-item Essence salvage, gemstone slots with unlock and item-consumption flows, configurable active pets with attachable pet items, Pet Score rewards, Autopet rules, and cosmetic pet companions, basic combat formulas, and placeable minions with stored generation, adjacent external storage blocks, configurable cosmetic skins, configurable fuel speed boosts, configurable upgrade slots, compactor-style output conversion, and hopper-style overflow selling. Full feature parity remains open until every milestone above is implemented, tested, and documented.
