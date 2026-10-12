# MinecraftZombies 1.1.1

## Fixed

- Disabled arenas now prevent players from breaking or placing blocks inside their boundaries. Previously, disabling an arena also skipped these protection checks.
- Protection uses the linked WorldGuard region when available, with the existing p1/p2 bounds as fallback.

## Upgrade

Stop the server, replace the previous MinecraftZombies JAR with **MinecraftZombies-1.1.1.jar**, and restart. Keep only one MinecraftZombies JAR installed. No configuration changes are required.

## Validation

Gradle build passed. In-game verification of this fix is still needed.
