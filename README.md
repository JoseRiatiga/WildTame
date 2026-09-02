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

## Commands

- `/wildtame` (alias `/wt`) — command summary
- `/wildtame menu` — the main hub (also where you store/summon pets)
- `/wildtame info`, `llamar`, `renombrar`, `top`
- `/wildtame admin reload|nivel|limpiar` — admin tools

## Requirements

- Paper 1.21.x (or a Paper fork like Purpur/Pufferfish) — not compatible with vanilla Spigot or Folia
- Java 21

## Building

```
./gradlew build
```

The compiled jar is written to `build/libs/`.

## License

[MIT](LICENSE)
