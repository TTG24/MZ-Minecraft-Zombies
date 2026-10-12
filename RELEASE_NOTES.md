# MinecraftZombies 1.1

## Added

- Optional WorldGuard arena regions. Link an arena with `/zombies setregion <arena> <regionId>`, view its link with `/zombies setregion <arena>`, or clear it with `/zombies setregion <arena> none`. Arena containment, spawn checks, and hounds use the linked region. Missing WorldGuard or missing regions fall back to the arena's min/max bounds. Requires `zombies.setregion` permission.
- Nearby interactions using Minecraft's **Swap Item With Offhand** action (F by default): doors, barriers, power switches, mystery boxes, perk machines, and Pack-a-Punch. Players can rebind the action in Minecraft Controls.
- Configurable interaction ranges, enabled settings, and action-bar tooltips, including prices, missing points, and power requirements. Default range is 3 blocks.
- Sign-free door and barrier setup and removal. Existing arena signs remain supported; machines still use their existing setup locations and signs.
- Automatic barrier repairs: press F once to repair one stage per second until full. Press again or leave range to stop. Each repaired stage awards the configured points.
- Arena zombies can survive Peaceful on Paper/Purpur versions that support the per-mob override, including 26.3. Enabled by default through `config.gameSettings.zombiesInPeaceful`; world difficulty stays unchanged. Spigot and the 26.2 client do not support this feature.

## Fixed

- Barriers start fully repaired, preventing a free repair and points at the beginning of a game.
- Barrier repairs award points for each stage rather than stopping at the previous per-round cap.
- WorldGuard is declared as an optional dependency so it loads before MinecraftZombies when installed.
- Missing configuration defaults are now written to existing configuration files on startup.

## Installation and upgrade

1. Stop the server and back up the plugin configuration and arena data.
2. Replace the previous plugin JAR with **MinecraftZombies-1.1.jar**; keep only one copy installed.
3. Start the server. Missing configuration defaults are added automatically, preserving custom values. Legacy `COM_Zombies` data-folder migration is supported.
4. Review `config.interaction`, `config.doorProximity`, `config.barrierProximity`, and `config.gameSettings.zombiesInPeaceful` in `plugins/MinecraftZombies/config.yml`.

`config.interaction.key` changes the displayed key label; players rebind the actual action in Minecraft Controls. Use `&` color codes in tooltips and `''` to hide a message. Apply configuration changes with a restart or `/zombies reload` between games; reloading ends active games.

Paper/Purpur is recommended for barrier pathfinding and Peaceful support. The compatibility layer targets Minecraft 1.21 and newer; not every server/client combination has been tested. Build with JDK 21. Server Java requirements depend on the Minecraft version.

## Validation

The Gradle build passes. There are no automated test sources. Interaction changes have been tested in-game during development; the latest Peaceful support and barrier-start initialization fix still need in-game verification.
