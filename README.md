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
- Configurable skills, collection tiers, action XP, coin rewards, custom items, and minion definitions.
- Placeable minion items with island-only placement, generated storage, claim menu, pickup flow, and offline ticking from persisted timestamps.
- Data-driven shaped recipes for vanilla, custom item, and minion outputs with collection-tier crafting gates.
- Purse and bank economy with deposit, withdraw, deposit-all, withdraw-all, capacity limits, and a configurable bank menu.
- Configurable NPC shops with buy menus, right-click sell flow, daily buy limits, and `/skyblock sell`.
- Optional physical shop NPCs spawned from `shops.yml` with persistent shop tags and right-click shop opening.
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
- `/skyblock profile`
- `/skyblock purse`
- `/skyblock skills`
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
