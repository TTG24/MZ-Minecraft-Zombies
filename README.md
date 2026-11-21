# MZ-Minecraft-Zombies

**MinecraftZombies**: round-based zombie survival for Paper/Purpur servers, inspired by Call of Duty Zombies.
Players hold barriers, buy doors and weapons, hit the mystery box and survive as long as they can.

A fork of [Call of Minecraft: Zombies](https://github.com/TheTurkeyDev/Call_Of_Minecraft-Zombies) by TurkeyDev
(MIT License). Changes per version are in [RELEASE_NOTES.md](RELEASE_NOTES.md), and the jars are on the
[releases page](https://github.com/TTG24/MZ-Minecraft-Zombies/releases).

## Features

- **Zombie AI.** Arena zombies and hellhounds use MZ's own route planner: they pick barriers past opened doors,
  hold at a barrier until it breaks, and never hit through fences, walls or slabs.
- **Crawlers, grenades and gore.** Grenades can blow a zombie's legs off and leave it crawling. Players start with
  4 grenades and get 2 more each round.
- **COD-style economy.** Points for barrier repairs and kills (knife 130, headshot 100, body 60, configurable),
  doors, perks, Pack-a-Punch and a mystery box.
- **Press F to interact.** Doors, barriers, power switches, mystery boxes, perk machines and Pack-a-Punch work from
  up to 3 blocks away with Swap Item With Offhand, and show an on-screen prompt. Game signs are optional.
- **Menus and accessibility.** `/z` opens a clickable menu (`/z admin` for staff) that shows only the commands you
  may use. `/z access` offers hold-or-toggle barrier repair and a teammate glow in a colour you pick.
- **Many matches per map.** One arena can run several independent copies at once, plus private solo copies.
- **Quality of life.** A 5-minute rejoin window, a game-over leaderboard, round music, configurable signs and
  sounds, and daily backups.

## Requirements

| | |
|---|---|
| Server | **Paper** or a fork such as **Purpur**, Minecraft 26.3 |
| Java | 25 |
| Optional | [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/) (or WorldEdit) for arena copies, WorldGuard for arena regions, Vault, PlaceholderAPI |
| Recommended | [BlockBlocker](https://github.com/TTG24/BlockBlocker) for barrier collision, and the [MZ-Movement](https://github.com/TTG24/MZ-Movement) client mod for players |

## Installation

1. Download `MinecraftZombies-<version>.jar` from the [releases page](https://github.com/TTG24/MZ-Minecraft-Zombies/releases).
2. Put it in `plugins/` (keep only one MinecraftZombies jar) and restart the server.
3. Edit `plugins/MinecraftZombies/config.yml` if needed, then run `/z reload` between games. A reload ends
   games that are running.

## Commands

`/zombies` has the aliases `/z`, `/zo` and `/zom`. Run `/z` for the clickable menu or `/z help` for the full list.

| Command | What it does |
|---|---|
| `/z join <arena>` | Join a match (also from a join sign) |
| `/z solo <arena>` | Play a private one-player copy of an arena |
| `/z leave` / `/z rejoin` | Leave a match / get back into it within 5 minutes |
| `/z spectate <arena>` | Watch a match |
| `/z access` | Accessibility options |
| `/z leaderboard` | Lifetime stats |
| `/z admin` | Staff menu |
| `/z createarena <name>` | Create an arena, then follow the chat prompts |
| `/z adddoor`, `/z addbarrier`, `/z editspawns` | Set up doors, barriers and zombie spawns |
| `/z showall` | Outline everything set up in an arena |
| `/z instances <arena> <count>` | Run several copies of one arena |
| `/z setregion <arena> <regionId>` | Link a WorldGuard region as the arena's shape |
| `/z start` / `/z end <arena>` | Force a match to start / end |

## Setting up an arena

1. `/z createarena <name>` and follow the prompts. Only the game warp (`gw`) and spectator warp (`sw`) are
   required. Without a lobby warp (`lw`), the match starts as soon as enough players join, with a 3-second spawn
   freeze.
2. Add doors and barriers. Select the blocks, type `done`, select their spawn points, type `done` again, then enter
   the price (`POWER:PRICE` for powered doors) or the repair reward. No signs are needed.
3. Place machines (power switch, perks, mystery box, Pack-a-Punch) with the usual game signs. Signs can be hidden
   afterwards with `/z signs`, and the machines keep working with F.
4. Check everything with `/z showall`, then `/z enable <arena>`.

Prompts and messages are set under `config.interaction`, `config.doorProximity` and `config.barrierProximity`.
They use `&` colour codes and placeholders such as `{key}`, `{price}` and `{missing}`. Set a message to `''` to
hide it.

## Several matches on one map

`/z instances Hospital 3` followed by a restart or `/z reload` prepares three independent copies of the arena
(`Hospital~1`, `Hospital~2`, ...). Players still use `/z join Hospital`, and busy copies queue players in join
order. The original arena becomes the template you edit, and changes are applied on the next reload.

- Needs FastAsyncWorldEdit (recommended) or WorldEdit. Each copy is a void world holding only the arena's
  two-corner box at its original coordinates, so keep every warp, spawn, door and machine inside that box.
- Copies are kept ready on demand (4 ready plus 1 spare) and pasted in slices so the server doesn't freeze. The
  size limit is `config.instances.maxCopyBlocks` (default 5,000,000 blocks).
- With WorldGuard, each copy gets the template world's regions, so a linked region still controls the arena's
  shape.
- `mz_instance_*` world folders belong to the plugin and are rebuilt as needed. Set the count to `0` and reload
  to remove them.

## Building

```
./gradlew build        # .\gradlew.bat build on Windows
```

The jar is written to `Core/build/libs`. Gradle runs on JDK 21 and compiles with a Java 25 toolchain, which it
downloads automatically if it isn't installed.

## Related projects

- [BlockBlocker](https://github.com/TTG24/BlockBlocker): server plugin for invisible barriers that block players and mobs.
- [MZ-Movement](https://github.com/TTG24/MZ-Movement): the Fabric client mod (tactical movement, zombie animations, crawlers, gore).

## Credits and license

Based on [Call of Minecraft: Zombies](https://github.com/TheTurkeyDev/Call_Of_Minecraft-Zombies) by TurkeyDev,
released under the MIT License. Contributions are welcome: open a pull request, and keep to the style of the
surrounding code.
