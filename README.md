# OpenSkyBlock

OpenSkyBlock is a PaperMC plugin project that aims to recreate the broad gameplay loop of MMO-style SkyBlock: persistent profiles, private island progression, skills, collections, custom items, minions, economy, NPC menus, bosses, dungeons, auctions, bazaar trading, pets, accessories, reforges, quests, and social systems.

This project is not affiliated with Hypixel. It is an independent implementation intended for configurable private servers.

## Current State

The repository currently contains the foundation needed for later full parity work:

- Paper 1.21.8 API Maven project.
- Persistent player profiles with purse, bank, skills, collections, and SkyBlock level calculation.
- Private island world creation with starter platform, starter chest, home teleport, and basic island protection.
- Configurable SkyBlock menu GUI and starter menu item.
- Paged collection browser and recipe book menus with configurable layout, lore, and navigation.
- Configurable MiniMessage messages in `messages.yml`.
- Configurable skills, collection tiers, action XP, coin rewards, custom items, item abilities, mana regeneration, and minion definitions.
- Placeable minion items with island-only placement, generated storage, claim menu, pickup flow, and offline ticking from persisted timestamps.
- Data-driven shaped recipes for vanilla, custom item, and minion outputs with collection-tier and Slayer-level crafting gates.
- Purse and bank economy with deposit, withdraw, deposit-all, withdraw-all, capacity limits, and a configurable bank menu.
- Configurable NPC shops with buy menus, right-click sell flow, daily buy limits, and `/skyblock sell`.
- Optional physical shop NPCs spawned from `shops.yml` with persistent shop tags and right-click shop opening.
- Configurable Auction House BIN-style listings with persistent item storage, listing fees, cancellation grace, buy flow, expired item returns, and seller claim flow.
- Configurable Bazaar products with persistent buy orders, sell offers, instant buy/sell matching, escrow refunds, cancellation, and claim flow.
- Secure player trading with request/accept flow, item and coin escrow, review/confirm steps, cancellation, and disconnect cleanup.
- Persistent configurable Storage pages with command and SkyBlock menu access, saved back to profiles on inventory close.
- Configurable installed Backpacks with admin-granted backpack items, right-click install flow, profile-backed contents, open/list/remove commands, and inventory-close saving.
- Configurable SkyBlock mobs with level/health nameplates, admin spawn commands, stat-based damage/defense, custom loot rolls, rare drop announcements, and kill rewards.
- Configurable mob spawn zones with runtime caps, player activation checks, weighted mob selection, and force-spawn commands.
- Configurable Bestiary families with persistent kill counts, milestone rewards, and stat aggregation.
- Configurable Slayer quests with coin start costs, Combat XP progress, owner-tagged boss spawning, boss bars, timeouts, completion rewards, Slayer levels, milestone rewards, and persistent Slayer XP.
- Configurable Sacks with persistent material storage, carry-item access checks, automatic pickup routing, GUI deposit/withdraw flow, and command access.
- Configurable Quiver with persistent arrow storage, pickup routing, arrow selection, bow-shot proxy consumption, GUI access, and command access.
- Configurable Potion Effects and God Potion bundles with persistent online-only timers, private-island timer pause, vanilla effect refresh, stat bonuses, item activation, and command status.
- Configurable Century Cakes with persistent placed cake furniture, reusable right-click buffs, visitor activation option, active buff timers, pickup flow, and stat aggregation.
- Configurable profile/account upgrades with persistent tier levels, purse-cost purchases, stat bonuses, bank-capacity bonuses, accessory-bag slot bonuses, and minion-slot bonuses.
- Aggregated combat stats from configurable base stats, held/armor items, equipment slots, accessory-category custom items, and Slayer rewards.
- Configurable item reforges with persistent item metadata, rarity-scaled stat tables, purse costs, lore rendering, and stat aggregation.
- Configurable item enchantments with persistent item metadata, max levels, ultimate-enchantment exclusivity, purse costs, lore rendering, and stat aggregation.
- Configurable item stars with persistent item metadata, category limits, purse costs, display suffix/lore, and item stat scaling.
- Configurable gemstone slots with persistent item metadata, item/category slot rules, tiered gemstone stat bonuses, purse costs, lore rendering, and stat aggregation.
- Configurable equipment slots with persistent equipped items, GUI equip/unequip flow, equipment-slot item metadata, and stat aggregation.
- Configurable Wardrobe slots with persistent armor setup storage, GUI swap/withdraw flow, and command access.
- Accessory Bag with unique accessory storage, Magical Power, GUI add/remove flow, and stat aggregation from bagged accessories.
- Accessory tuning with Magical Power-derived tuning points, configurable per-stat values, commands, GUI controls, and stat aggregation.
- Configurable armor set IDs and full-set stat bonuses, with a starter Farm Suit set.
- Configurable pets with persistent ownership, active pet selection, XP/level tracking, GUI activation, attachable pet items, and active pet stat aggregation.
- `/skyblock` command with profile, purse, skills, collections, custom item, minion, and reload subcommands.
- Listeners for block breaking, entity kills, item pickup, custom item combat bonuses, join, and quit persistence.

## Build

```bash
mvn package
```

The plugin jar is written to:

```text
target/OpenSkyBlock-0.1.0-SNAPSHOT.jar
```

## Install

1. Build the jar with Maven.
2. Place the jar in a Paper server `plugins/` directory.
3. Start the server once to generate configuration files.
4. Edit `plugins/OpenSkyBlock/*.yml` to tune messages, rewards, collections, items, and minions.

## Commands

- `/skyblock help`
- `/skyblock menu`
- `/skyblock island create`
- `/skyblock island home`
- `/skyblock island info`
- `/skyblock bank`
- `/skyblock bank deposit <amount|all>`
- `/skyblock bank withdraw <amount|all>`
- `/skyblock shops`
- `/skyblock shop <id>`
- `/skyblock shopnpcs refresh`
- `/skyblock shopnpcs remove`
- `/skyblock sell <hand|all>`
- `/skyblock auctions`
- `/skyblock auction list [page]`
- `/skyblock auction create <price>`
- `/skyblock auction buy <id>`
- `/skyblock auction cancel <id>`
- `/skyblock auction claim`
- `/skyblock auction mine`
- `/skyblock bazaar`
- `/skyblock bazaar products [page]`
- `/skyblock bazaar info <product>`
- `/skyblock bazaar instabuy <product> <amount>`
- `/skyblock bazaar instasell <product> <amount|all>`
- `/skyblock bazaar buyorder <product> <amount> <price>`
- `/skyblock bazaar selloffer <product> <amount|all> <price>`
- `/skyblock bazaar claim`
- `/skyblock bazaar cancel <id>`
- `/skyblock bazaar orders`
- `/skyblock trade <player>`
- `/skyblock trade accept <player>`
- `/skyblock trade deny <player>`
- `/skyblock trade offerhand`
- `/skyblock trade offercoins <amount>`
- `/skyblock trade remove <slot>`
- `/skyblock trade ready`
- `/skyblock trade confirm`
- `/skyblock trade status`
- `/skyblock trade cancel`
- `/skyblock storage [page]`
- `/skyblock enderchest [page]`
- `/skyblock ec [page]`
- `/skyblock backpack [slot]`
- `/skyblock backpack list`
- `/skyblock backpack install`
- `/skyblock backpack remove <slot>`
- `/skyblock backpack give <id> [player]`
- `/skyblock mobs`
- `/skyblock mob spawn <id> [amount]`
- `/skyblock mobzones`
- `/skyblock mobzone <id>`
- `/skyblock mobzone spawn <id> [amount]`
- `/skyblock bestiary [family]`
- `/skyblock slayers`
- `/skyblock slayer start <id> <tier>`
- `/skyblock slayer status`
- `/skyblock slayer cancel`
- `/skyblock sacks`
- `/skyblock sack deposit <id>`
- `/skyblock sack withdraw <id> <item> <amount|all>`
- `/skyblock sack summary [id]`
- `/skyblock quiver`
- `/skyblock quiver deposit`
- `/skyblock quiver withdraw <item> <amount|all>`
- `/skyblock quiver select <item>`
- `/skyblock quiver summary`
- `/skyblock potions`
- `/skyblock potion clear`
- `/skyblock potion activate <bundle>`
- `/skyblock cakes`
- `/skyblock cake placed`
- `/skyblock cake clear`
- `/skyblock cake list`
- `/skyblock upgrades`
- `/skyblock upgrade info <id>`
- `/skyblock upgrade buy <id>`
- `/skyblock reforges`
- `/skyblock reforge <id|remove>`
- `/skyblock enchants`
- `/skyblock enchant <id> <level>`
- `/skyblock enchant remove <id>`
- `/skyblock stars`
- `/skyblock star add [amount]`
- `/skyblock star set <amount>`
- `/skyblock star clear`
- `/skyblock gemstones`
- `/skyblock gemstone slots`
- `/skyblock gemstone apply <slot> <gemstone> <tier>`
- `/skyblock gemstone remove <slot>`
- `/skyblock equipment`
- `/skyblock equipment equip [slot]`
- `/skyblock equipment unequip <slot>`
- `/skyblock equipment summary`
- `/skyblock wardrobe`
- `/skyblock wardrobe save <slot>`
- `/skyblock wardrobe equip <slot>`
- `/skyblock wardrobe withdraw <slot>`
- `/skyblock wardrobe summary`
- `/skyblock profile`
- `/skyblock purse`
- `/skyblock skills`
- `/skyblock stats`
- `/skyblock accessorybag`
- `/skyblock accessorybag add`
- `/skyblock accessorybag remove <id>`
- `/skyblock tuning`
- `/skyblock tuning add <stat>`
- `/skyblock tuning remove <stat>`
- `/skyblock tuning reset`
- `/skyblock pets`
- `/skyblock pet list`
- `/skyblock pet activate <slot>`
- `/skyblock pet give <id> [player]`
- `/skyblock pet xp <amount> [player]`
- `/skyblock collections`
- `/skyblock recipes`
- `/skyblock giveitem <id> [player]`
- `/skyblock minion give <id> [player]`
- `/skyblock minion add <id>`
- `/skyblock minion list`
- `/skyblock minion claim [slot|all]`
- `/skyblock reload`

Alias: `/sb`

## Permissions

- `openskyblock.command`: access to basic `/skyblock` commands.
- `openskyblock.admin`: access to administrative commands such as reload and custom item granting.

## Development Plan

The detailed feature plan is in [`docs/PLAN.md`](docs/PLAN.md).
