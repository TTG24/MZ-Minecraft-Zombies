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
