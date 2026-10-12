# MZ-Minecraft-Zombies
A fork of the Call_Of_Minecraft-Zombies Repo from TurkeyDev under MIT Licensing

Original project: https://github.com/TheTurkeyDev/Call_Of_Minecraft-Zombies

## Release 1.1

See [the 1.1 release notes](RELEASE_NOTES.md) for installation, configuration, changes, and validation details.

## What this fork changes

- **One jar for 1.21 and newer.** The per-version NMS modules are replaced by a single module that only uses the
  Bukkit API. The compatibility layer targets Minecraft 1.21 and newer; not every server/client combination has been tested.
- **WorldGuard arena regions.** Use `/zombies setregion <arena> <regionId>` to link an existing region in the arena's
  world. Spawns, hounds, and player containment use its shape. `/zombies setregion <arena>` shows the link;
  `/zombies setregion <arena> none` clears it. Requires `zombies.setregion`. WorldGuard is optional; unavailable
  regions fall back to the arena's min/max bounds.
- **Configurable signs.** The text on every game sign (guns, perks, doors, mystery box, barriers, join signs and more)
  is set in the `signs` section of `config.yml`. Signs are recognised by their location, so the text can be anything.
- **Smarter barriers.** Zombies have to be right next to a barrier to break it, and on Paper they are walked to their
  barrier and held there until it breaks (see [Server software](#server-software)).
- **Fixes**, including: barriers letting players through at negative coordinates, zombies breaking barriers from far
  away, barrier cracks disappearing between rounds, the power switch never working, kit round rewards never firing,
  and the gun zoom getting stuck.

## Nearby interactions and setup

Press Swap Item With Offhand (F by default) within 3 blocks to use a nearby door, barrier, or machine.
For barriers, one press starts repairing one stage per second and awards points for each stage.
Press again or move out of range to stop. Barriers start fully repaired.

Door and barrier setup no longer asks for signs. Select the blocks, type `done`, select spawn points,
and type `done` again. Then enter the door price (`POWER:PRICE` for powered doors) or barrier repair reward.
Existing arena signs remain supported. Removal commands now let you click a door/barrier block with the
wooden sword instead of requiring a sign.

Nearby door and barrier prompts are customizable in `plugins/MinecraftZombies/config.yml` under
`config.doorProximity.tooltip` and `config.barrierProximity.tooltip`. Missing settings are added on startup.
Edit the message values using `&` color codes, or set a message to `''` to hide it. Door messages support
`{key}` and `{price}`, plus `{missing}` in `no_funds`; barrier messages support `{key}`.
Apply edits by restarting or running `/zombies reload` between games (reloading ends active games).

Swap Item With Offhand (F by default) also activates nearby power switches, mystery boxes, perk machines,
and Pack-a-Punch. The closest feature supplies the prompt and action; an ongoing barrier repair takes
priority so pressing the key again stops it. Mystery boxes support both buying a roll and picking up your
weapon, including fire sale prices. Existing machine locations are used; set them up with the usual game
signs. You do not have to click the signs to interact.

Customize `config.interaction.key` for the displayed key label. Players change the actual key using
Minecraft Controls → Swap Item With Offhand. Each new machine type under `config.interaction` has its own
`enabled`, `radius` (default 3 blocks), and `tooltip` settings. Machine messages support `{key}`, `{price}`,
`{missing}`, and `{perk}` for perk names. Existing door/barrier prompt settings still work.

## Building

Requires JDK 21. From the project folder run `./gradlew build` (`.\gradlew.bat build` on Windows). The plugin jar is
created in `Core/build/libs`. No BuildTools run is needed.

Servers need Java 21 for 1.21.x, or Java 25 for 26.x.

## Server software

Arena zombies can survive Peaceful on Paper/Purpur with the per-mob Peaceful override, including 26.3.
This is enabled by `config.gameSettings.zombiesInPeaceful` and only affects arena zombies. The world's
difficulty stays unchanged. Spigot does not expose this override; the 26.2 client also does not support it.

**Paper (or a fork such as Purpur) is recommended.** In addition to Peaceful support, on Paper,
zombies that spawn behind a barrier are walked to it and held there until they break it down. Spigot has no
pathfinding API for this, so there zombies simply chase the nearest player. On Spigot, build maps so a barrier is the
only way out of a spawn room, or zombies can get stuck at a closer wall and never reach the barrier.

---

*The original project README follows.*

# Call Of MineCraft: Zombies
COMZ, is a Bukkit plugin that adds the Zombies minigame from the Call Of Duty© Franchise

BukkitDev page: https://www.curseforge.com/minecraft/bukkit-plugins/call-duty-zombies


### Contributing:
For those interested in contributing simply pull down the master branch, add your changes and make a PR! I'm not overly
stringent on formatting, just try and keep the same style that you see in the code, or when in doubt just ask!

This is a
gradle based project, so to make your life easier I recommend importing the project as such in your IDE. To build the
plugin, simple run `gradlew build` on the root folder and then the plugin jar should be found in `Core/build/libs` when
the build completes.

All core code that you will ikely need to edit will be located inside the `Core` module, but version compatibility code
can be found in the NMS Support folder and then the corresponding version module.
