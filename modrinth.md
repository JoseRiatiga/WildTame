# WildTame

A fully customizable pet-taming and progression plugin for Paper. Tame wolves, cats, parrots, horses, donkeys, mules, and llamas, level them up, and manage them all through one in-game menu — no commands to memorize.

## Feature breakdown

| Category | What you get |
|---|---|
| Progression | Level cap 50, XP from combat assists, and a craftable Pet Treat item for direct XP |
| Species | Wolf, Cat, Parrot, Horse, Donkey, Mule, Llama — each with unique stats and one exclusive ability |
| Menus | Main hub, pet list with filters/sort/pagination, global ranking, personal stats, help, admin panel |
| Persistence | Appearance, birth-roll stats, and a per-pet inventory that grows with level |
| Customization | Full ES/EN translation, editable messages and menu text, optional custom-head icons |
| Admin tools | `/wildtame admin reload\|nivel\|limpiar`, automatic backups, ghost-pet cleanup, PlaceholderAPI support |

## Getting started

1. Drop the jar into your `plugins/` folder and restart.
2. Equip the **Taming Collar** in your off-hand (craft one, or have an admin give you one from the Admin Panel).
3. Tame an animal using the method below for its species. Taming isn't guaranteed on the first try — keep at it.
4. Run `/wildtame menu` to see it, name it, and start leveling it up.

## How to tame each species

| Species | How to tame |
|---|---|
| Wolf | Feed it a bone |
| Cat | Feed it raw fish |
| Parrot | Feed it seeds |
| Horse / Donkey / Mule | Mount and ride it repeatedly |
| Llama | Mount and ride it repeatedly |

Once tamed, it's already registered — level it up by fighting near it or feeding it a crafted **Pet Treat** (golden carrot + bone + berries) for instant XP.

## Commands

| Command | Description |
|---|---|
| `/wildtame` (alias `/wt`) | Shows a quick command summary in chat |
| `/wildtame menu` | Opens the main hub — browse, summon, and store your pets |
| `/wildtame info` | Opens your active pet's panel |
| `/wildtame llamar` | Teleports your active pet to you |
| `/wildtame renombrar <name>` | Renames your active pet |
| `/wildtame top` | Shows the global pet leaderboard |
| `/wildtame admin reload` | Reloads `config.yml` and the language files |
| `/wildtame admin nivel <player> <pet> <level>` | Sets a specific pet's level |
| `/wildtame admin limpiar` | Removes desynced ghost pet entities |

## Permissions

| Node | Default | Description |
|---|---|---|
| `wildtame.user` | `true` | Use the normal pet commands (info, llamar, renombrar, menu, top) |
| `wildtame.admin.collar` | `op` | Receive the Taming Collar from the Admin Panel |
| `wildtame.admin.golosina` | `op` | Receive Pet Treats from the Admin Panel |
| `wildtame.admin.nivel` | `op` | Set a player's active pet level (`/wildtame admin nivel`) |
| `wildtame.admin.reload` | `op` | Reload `config.yml` and the languages (`/wildtame admin reload`) |
| `wildtame.admin.limpiar` | `op` | Remove desynced ghost pets (`/wildtame admin limpiar`) |
| `wildtame.admin.*` | `op` | Grants all of the admin permissions above |

## Requirements

| Requirement | Value |
|---|---|
| Server software | Paper (or a fork like Purpur/Pufferfish) |
| Minecraft version | 1.21.x |
| Java | 21 |
| Optional | PlaceholderAPI, for `%wildtame_*%` placeholders |

## Source & support

Source code and issue tracker: [GitHub](https://github.com/JoseRiatiga/WildTame).
