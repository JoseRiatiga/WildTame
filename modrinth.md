# WildTame

### Every wild animal you tame becomes a real companion — not a decoration.

Vanilla taming ends the moment you put the lead away. The wolf just sits there, the horse just... exists. WildTame turns that moment into the start of something: your pet gains levels, unlocks abilities tied to its species, and grows stronger the longer it fights by your side — all without a single command to memorize, because everything lives in one in-game menu.

## What makes it different

**Every species has its own identity.** A Wolf hits harder and shares Strength with you. A Cat has a chance to dodge attacks outright and shares Luck. A Llama at high level turns every nearby monster's aggro onto itself. A Parrot warns you in the action bar before a mob even gets close. This isn't one system reskinned seven times — each animal plays differently.

**One menu, not a dozen commands.** `/wildtame menu` opens a hub: your full pet roster (filterable, sortable, paginated), a server-wide ranking, your own stats, a help screen, and — if you're an admin — a panel to hand out items and reload the config, all without touching chat.

**Nothing gets lost.** Store a pet and its color, variant, and even its original "birth roll" stats are saved exactly — summon it back later and it's identical to how you left it, not a fresh roll.

**Built to be reshaped, not just configured.** Every message, every menu title, and every item name lives in an editable language file — Spanish and English ship out of the box. Balance numbers (XP rates, level cap, cooldowns, every attribute bonus) live in `config.yml` and reload live with one command. Even the menu icons can become custom player heads instead of vanilla items.

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
