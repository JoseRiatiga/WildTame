# WildTame

A fully customizable pet-taming and progression plugin for Paper servers. Tame wolves, cats, parrots, horses, donkeys, mules, and llamas, then level them up, unlock unique abilities, and manage them all through an in-game GUI.

## Features

- **Real progression** — pets gain XP, level up to 50, and grow stronger with attribute bonuses unique to each species.
- **Species identities** — every type has its own stat spread and ability: Wolf strength aura, Cat dodge chance, Parrot hostile-mob radar, Llama aggro taunt, and passive owner auras (Strength, Luck, Slow Falling, Speed, Haste, Resistance).
- **Full menu system** — `/wildtame menu` opens a hub with your pet list (filter/sort/paginate), global ranking, your stats, help, and an admin panel — no memorizing commands required.
- **Persistent appearance** — a pet's color, variant, and "birth roll" stats are saved and restored exactly, even after storing and re-summoning it.
- **Per-pet inventory** that grows with level, separate from the entity's native chest.
- **Fully translatable** — ships with Spanish and English out of the box (`lang/` folder), plus every menu, message, and item name is editable without touching code.
- **Custom-head support** — swap any menu icon for a custom player-head texture (minecraft-heads.com Value format), with a toggle to fall back to vanilla items.
- **Config-driven balance** — level cap, XP rates, cooldowns, ability unlock levels, and every attribute bonus are all set in `config.yml`, reloadable with `/wildtame admin reload`.
- **PlaceholderAPI support**, automatic `pets.yml` backups, and a ghost-pet cleanup command for admins.

## Requirements

| Requirement | Value |
|---|---|
| Server software | Paper (or a fork such as Purpur/Pufferfish) — **not** vanilla Spigot or Folia |
| Minecraft version | 1.21.x |
| Java | 21 |
| Optional dependency | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — enables `%wildtame_*%` placeholders |

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

## Downloads

Compiled releases are published on [Modrinth](https://modrinth.com).

## Contributing

Found a bug or want to add a feature? Issues and pull requests are welcome. To build from source: `./gradlew build` (jar output in `build/libs/`).

## License

[MIT](LICENSE)
