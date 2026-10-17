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
