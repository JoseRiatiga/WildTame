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
2. Tame an animal the vanilla way (feed it or ride it, depending on the species).
3. Run `/wildtame menu` to see it, name it, and start leveling it up.

## Requirements

| Requirement | Value |
|---|---|
| Server software | Paper (or a fork like Purpur/Pufferfish) |
| Minecraft version | 1.21.x |
| Java | 21 |
| Optional | PlaceholderAPI, for `%wildtame_*%` placeholders |

## Source & support

Source code and issue tracker: [GitHub](https://github.com/JoseRiatiga/WildTame).
