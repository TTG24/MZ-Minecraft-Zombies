# MinecraftZombies 1.3.1

## Post-match game over

- When a match genuinely ends (everyone down), a `mz:game_over` stinger from the bundled
  resource pack now plays for everyone in the arena. Clients without the pack simply hear
  nothing; existing round stingers are unaffected.
- Every surviving player is then held in **spectator mode** at the arena's spectate warp for
  `config.gameSettings.gameOverSpectateSeconds` (default `30`), so the whole party can hear the
  ending together before being returned to the spot they were in when they joined. Setting the
  value to `0` disables the hold and sends players home immediately. The setting is re-read on
  `/zombies reload`.

## Game over leaderboard

- While players spectate after a match, a **Game Over** sidebar shows the final standings: the
  round reached plus one line per player with their points, kills, revives and deaths, sorted by
  kills (then points as a tiebreaker).
- Points, kills, revives and deaths are tracked **per match** and captured the moment the match
  ends, so they are independent of the lifetime `/zombies leaderboard` totals.
- `/zombies leave` (or `/zombies l`) skips the wait: a player watching the board is sent straight
  home, and the last one to leave tears the solo instance down immediately instead of waiting out
  the timer.

## Fixes

- Solo arenas no longer unload while a player is still on the post-match board; the instance now
  waits for the spectate window to finish (or the player to `/zombies leave`) before it is
  removed.

## Upgrade

Stop the server, replace the previous plugin JAR with **MinecraftZombies-1.3.1.jar**, and
restart. Keep only one MinecraftZombies JAR installed. Existing arena settings remain; the new
setting (`gameOverSpectateSeconds`) is added automatically. Servers using the 1.3 resource pack
should update to the republished pack for the new `mz:game_over` sound.

## Validation

The full Gradle build and automated checks pass. The game-over stinger, post-match spectate,
leaderboard, `/zombies leave` skip and solo instance teardown were verified in-game by the server
owner.

# MinecraftZombies 1.3

## Round music resource pack

- Two round stingers now play for everyone in the match: one when a round starts and one when
  a round ends. They are delivered through a server resource pack (the default pack source
  lives in the repository's `resourcepack/` folder; your server can force it via the
  `resource-pack` settings in `server.properties`, e.g. an mcpacks.net URL).
- The pack ships `mz:round_start.ogg` and `mz:round_end.ogg` under the `mz` namespace, mapped
  to the events `mz:round_start` and `mz:round_end` in `assets/mz/sounds.json`. Clients without
  the pack simply hear nothing; the built-in round sounds keep playing as before, so nothing
  breaks on servers that don't offer the pack.
- New `config.gameSettings.roundMusicVolume` (default `1.0`) sets the volume; `0` disables the
  music. The setting is re-read on `/zombies reload`.
- **Making your own stingers:** encode two audio files to `.ogg` (Vorbis), for example
  `ffmpeg -i my_start.mp3 -c:a libvorbis -ar 44100 round_start.ogg`, replace
  `resourcepack/assets/mz/sounds/round_start.ogg` and `round_end.ogg` (keep the file names, the
  event names are fixed), rebuild the zip with `tar -a -c -f MZ-RoundMusic-1.21.zip pack.mcmeta assets`
  (never Windows' Compress-Archive, which writes backslash zip entries Minecraft rejects), and
  re-upload the pack, updating the SHA1 in `server.properties`. See `resourcepack/README.md`
  for the full procedure, including the `sounds.json` namespace gotchas.

## Configurable barrier sounds

- New `config.gameSettings.barrierSounds` section replaces the hard-coded barrier sounds:
  - `zombieDamage` (list, cycled in order so consecutive hits don't sound identical),
  - `zombieBreak` (played once when the barrier breaks), and
  - `playerRepair` (played per repair stage, alternating between two pitches so it doesn't drone).
- Any valid Minecraft sound name works (for example `ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR` or
  `BLOCK_BONE_BLOCK_BREAK`). An empty value mutes that sound, and an invalid name logs a
  warning on startup and falls back to the previous default. The setting is re-read on
  `/zombies reload`.

## Solo arenas

- New `/zombies solo <arena>` (alias `/zombies js <arena>`) spins up a **private, one-player
  copy** of any fully-configured arena, drops you straight in, and auto-starts the match.
- The copy is created on demand and is **removed the moment it empties** — when you die and
  leave, when you quit, or when the match ends on its own. The world is unloaded and its
  folder deleted, so nothing is left behind. Dying only drops you to the spectate area; the
  arena stays alive so you can `/zombies rejoin` or keep spectating.
- Solo copies reuse the same arena-copy machinery as the arena instance pools (FastAsyncWorldEdit
  or WorldEdit required). New `config.instances.soloInstances` (default `true`) can disable the
  command. Permission: `zombies.solo` (or `zombies.solo.<arena>`).

## Late joins for lobby-less arenas

- New `config.gameSettings.lateJoinRound` (default `5`) lets players join an instant-start
  (lobby-less) match after it has begun. While the running round is below that number,
  `/zombies join` and join signs drop new players into the match at the spawn point with a
  fresh 3-second spawn freeze and fading blindness, the same way the match start does.
- Once the limit is reached, new joiners become spectators as before. Arenas with a lobby are
  unaffected, and setting the value to `0` disables late joins entirely. The setting is
  re-read on `/zombies reload`.

## Fading blindness

- The spawn freeze of instant-start arenas (and the personal freeze of late joiners) is now
  paired with a blindness overlay that starts at full strength and fades away in steps, being
  removed exactly when the freeze ends, easing players into the match.

## Instance decorations

- Instance snapshots now copy decorative entities from the template — armor stands, paintings,
  item frames, boats, minecarts and passive mobs — so every instance looks like the original
  arena. Live zombies, projectiles, dropped items, XP orbs and players are excluded. Entities
  that WorldEdit/FAWE cannot serialize are skipped with a console warning instead of breaking
  the copy.
- Copied decorations are protected in instance worlds: damage to, and right-click interaction
  with, armor stands, paintings and item frames is cancelled, so they persist for the life of
  the instance.

## Internal

- Removed all remaining Java deprecation warnings against Paper 26.3 (WorldEdit/FAWE location
  accessors, `Sound.valueOf` lookups, legacy switch statements in the help command, test
  proxies), and the build now compiles warning-free.

## Upgrade

Stop the server, replace the previous plugin JAR with **MinecraftZombies-1.3.jar**, and restart.
Keep only one MinecraftZombies JAR installed. Existing arena settings remain; the new settings
(`roundMusicVolume`, `barrierSounds`, `lateJoinRound`, `soloInstances`) are added automatically.
For round music, install the bundled resource pack (see `resourcepack/README.md`) and point
`server.properties` at it. Apply instance counts and map edits with a restart or `/zombies reload`
between games; reload ends active games.

## Validation

The full Gradle build and automated checks pass with zero deprecation warnings. Solo arenas,
late joins, fading blindness, barrier sounds and the round music pack were verified in-game by
the server owner.

# MinecraftZombies 1.2.1

## Configurable starting armor

- New `config.gameSettings.startingArmor` (default `true`) controls whether players join
  wearing the default unbreakable leather armor. Set it to `false` so players start with no
  armor at all. Perks that grant armor, such as the Juggernog chainmail chestplate, are
  unaffected. The setting is re-read on `/zombies reload`, and applies when players join.

## Optional lobby warp

- The lobby warp (`lw`) is no longer required when setting up an arena. Only the game warp
  (`gw`) and spectator warp (`sw`) are mandatory; without a lobby, players wait at the game
  warp before the match.
- Type `rlw` during arena setup to remove a lobby from an existing arena; players then wait at
  the game warp. `/zombies info` shows "(using game spawn)" for the lobby line when none is set.
- Arena saves omit the lobby entry when unset, and remain compatible with existing saves.
- Arenas with a lobby keep their previous behavior exactly.

## Instant start with spawn freeze

- Arenas without a lobby have no pre-game holding spot, so they now start immediately once
  the minimum number of players have joined, skipping the configured start countdown.
- At the start, players are frozen in place for 3 seconds: they can still look around, but
  walking, sprinting and jumping snap them back to their spawn spot. Arenas with a lobby are
  unaffected.

## Internal

- Door/barrier proximity distance checks extracted into pure, unit-tested helpers with 9 new
  automated tests covering anchor distance, world filtering, radius boundaries and tie-breaks.

## Upgrade

Stop the server, replace the previous plugin JAR with **MinecraftZombies-1.2.1.jar**, and restart.
Keep only one MinecraftZombies JAR installed. Existing arena settings remain;
`startingArmor` defaults to `true`, so current behavior is unchanged unless you opt in.
Apply lobby changes with a restart or `/zombies reload` between games; reload ends active games.

## Validation

The full Gradle build and automated checks pass, including the new proximity geometry tests.
The spawn freeze was confirmed in-game by the server owner (players can look around but cannot
move or jump). Optional-lobby setup and instant start were verified against a lobby-less arena.

# MinecraftZombies 1.2

## Arena instances

- Configure one arena with `/zombies instances <arena> <0-16>` and host independent matches on
  reusable copies. Each has its own rounds, players, mobs, points, power and machines.
- `/zombies join <arena>` selects an available copy or queues the player; `/zombies leave`
  cancels the queue. The original arena becomes an editing template while instances are enabled.
- Copies contain only the inclusive two-corner arena box, at its original coordinates, in a
  blank void world. WorldEdit or FastAsyncWorldEdit snapshots blocks, block entities, entities
  and biomes once per template and pastes them into each slot at startup/reload.
- WorldGuard regions are independently copied, including polygons, parents, priorities,
  membership and flags. The linked region controls gameplay containment and protection;
  the two-corner box controls the map copy. Template-world teleport flags point to each copy.
- Setting the count to `0` deletes the arena's owned instance world folders on the next
  restart/reload after unloading, including legacy staging folders. The original arena is kept.
  Loaded worlds and folders without a matching ownership marker are preserved.

## Compatibility and configuration

- Supports legacy world folders and Paper/Purpur 26.x unified dimension storage, with independent
  world identities and runtime-compatible game rule lookup.
- Pooled arenas require WorldEdit or FAWE; ordinary arenas without instances do not.
  Linked WorldGuard arenas also require WorldGuard to remain enabled.
- Include all floors, walls, warps, spawns and machines inside the two corners. Configured arena
  locations outside the box fail preparation with a console explanation.
- `config.instances.maxCopyBlocks` defaults to 5,000,000 blocks, including air. Preparation runs
  on the server thread; large selections or many slots can pause startup/reload.
- Between matches, the existing door/barrier/mob/machine reset reuses the world. The selection
  is pasted again on startup/reload, rather than after every match.

## Upgrade

Stop the server, replace the previous plugin JAR with **MinecraftZombies-1.2.jar**, and restart.
Keep only one MinecraftZombies JAR installed. Existing arena settings remain; missing configuration
settings are added automatically. Apply instance counts and map edits with a restart or
`/zombies reload` between games; reload ends active games.

## Validation

The full Gradle build and automated checks pass for exact selection boundaries, negative
coordinates, external-location rejection, void generation, ownership and deletion guards,
legacy/unified storage, WorldGuard region copying and game rule compatibility.
Arena selection copies and WorldGuard gameplay were confirmed working in-game by the server owner.
The latest zero-count folder cleanup passes regression checks; live server verification remains.

# MinecraftZombies 1.1.2

## Fixed

- Arena zombies on Peaceful now follow and attack eligible players instead of relying on vanilla targeting, which refuses player targets on Peaceful. Arena damage and perk handling remain in use.
- Barrier navigation tries reachable positions beside the barrier. Zombies no longer leave their barrier early for a player hidden behind it.
- Game, lobby, and spectator warps now save facing direction and load exact coordinates, preventing south-facing teleports and coordinate truncation after a restart.
- Existing arena saves remain compatible. Disabled arenas remain editable; enabled arenas remain protected, including linked WorldGuard regions.

## Upgrade

Stop the server, replace the previous MinecraftZombies JAR with **MinecraftZombies-1.1.2.jar**, and restart. Keep only one MinecraftZombies JAR installed.

Existing warps did not save facing direction. After installing, use `/zombies edit <arena>` and re-set `gw`, `lw`, and `sw` while facing the desired direction at each location. Type `done`, then re-enable the arena with `/zombies enable <arena>`. Newly saved warps retain their position and direction across restarts.

Peaceful support requires Paper/Purpur with the per-mob Peaceful override and `config.gameSettings.zombiesInPeaceful` enabled. The world's difficulty stays unchanged.

## Validation

- Gradle build passed.
- Warp JSON save/load checks passed for fractional and negative coordinates, yaw, pitch, and legacy saves.
- Peaceful targeting and barrier behavior were confirmed in-game by the server owner. The warp persistence fix passed save/load checks; an in-game restart check is still recommended.
