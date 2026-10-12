# MinecraftZombies 1.1.1

## Fixed

- Corrected arena block protection: disabled arenas allow breaking and placing for map editing; enabled arenas prevent both inside their boundaries.
- Protection uses the linked WorldGuard region when available, with the existing p1/p2 bounds as fallback.

## Upgrade

This corrected build replaces the earlier 1.1.1 download. Stop the server, replace the previous MinecraftZombies JAR with **MinecraftZombies-1.1.1.jar**, and restart. Keep only one MinecraftZombies JAR installed. No configuration changes are required.

## Validation

Gradle build passed. In-game verification of this fix is still needed.
