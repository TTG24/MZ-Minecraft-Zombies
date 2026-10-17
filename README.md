# MZ-Minecraft-Zombies
A fork of the Call_Of_Minecraft-Zombies Repo from TurkeyDev under MIT Licensing

Original project: https://github.com/TheTurkeyDev/Call_Of_Minecraft-Zombies

## Release 1.2.1

**Configurable starting armor.** `config.gameSettings.startingArmor` (default `true`) controls
whether players join wearing the default unbreakable leather armor. Set it to `false` to let
players start with no armor. Re-read on `/zombies reload`.

**Optional lobby warp.** The lobby warp (`lw`) is no longer required when setting up an arena —
only the game warp (`gw`) and spectator warp (`sw`) are. Without a lobby, players wait at the
game warp before the match. Type `rlw` during arena setup to remove a lobby from an existing
arena.

**Instant start with spawn freeze.** Arenas without a lobby start immediately once enough
players have joined, skipping the start countdown. At the start, players are frozen in place
for 3 seconds: they can look around, but movement and jumping snap them back to their spawn
spot. Arenas with a lobby are unaffected.

### Previous release 1.2

Host independent matches from one arena template, with automatic instance selection and queues.
Each instance copies the arena's two-corner box into a void world using FastAsyncWorldEdit
(recommended) or WorldEdit (synchronous fallback),
while WorldGuard supplies its gameplay boundary and protections. Setting the count to `0`
removes owned instance folders on restart/reload. See [instance setup](#multiple-matches-on-one-arena-template)
and [release notes](RELEASE_NOTES.md).

### Previous release 1.1.2

Fixed arena zombies targeting players and reaching barriers on Peaceful. Game, lobby, and spectator warps now preserve position and facing direction after a restart.
See [the 1.1.2 release notes](RELEASE_NOTES.md) for details and upgrade instructions, including re-setting existing warps once.

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

## Multiple matches on one arena template

Configure an arena normally, then use `/zombies instances Hospital 3` (requires
`zombies.createarena`). Restart or run `/zombies reload` **between games** to prepare
three independent copies. Players use `/zombies join Hospital`; existing join signs
for Hospital also route to the copies. Busy pools queue players in join order;
`/zombies leave` cancels a queue. Joining another match cancels the old queue.
Queues are cleared on reload/restart. Instances inherit the template's join permission.

Each copy has its own rounds, players, mobs, points, power and machines. The original
arena becomes a setup template and cannot host matches while its pool is enabled.
Copies appear as `Hospital~1`, `Hospital~2`, etc. Administrators can target these names
with the existing start/end commands. Disabling/enabling Hospital affects its whole pool.
Configure and edit the original arena; apply changes with restart/reload. Runtime copies
are excluded from `arenas.json`. Set the count to `0` and reload to return to a normal arena.

Instances require **FastAsyncWorldEdit** (recommended) or **WorldEdit**. Each copy is a blank void
world containing only the arena's inclusive **two-corner box**, at its original coordinates. The
snapshot includes blocks, block entities (such as containers), entities and biomes. No terrain or
structures generate outside the selection, including in Nether/End instances. Difficulty, time and
game rules come from the template world. Ordinary arenas without instances do not require either plugin.

**FastAsyncWorldEdit is the recommended plugin.** It is a fork of WorldEdit that provides the same
API plus an asynchronous block-queue engine, so arena copies run on its faster, lower-memory engine
when FAWE is installed. Install FAWE instead of WorldEdit (they both provide the same
`com.sk89q.worldedit` package and are not meant to run side by side). WorldGuard works unchanged
alongside FAWE. If only WorldEdit is present, the plugin still works but each copy runs
synchronously on the server thread and the console logs a recommendation to install FAWE.

Include the arena's floor, walls, warps, spawn points, doors and machines within the two corners.
Configured arena locations outside the box cause preparation to fail with a console explanation.
The map is captured once per template and pasted into each slot at startup/reload. The default
volume limit is 5,000,000 blocks, including air; adjust `config.instances.maxCopyBlocks` if needed.
The copy is applied in order and each instance is only opened after its blocks are fully written.
With FAWE this runs through its fast engine; with WorldEdit only, it runs on the server thread, so
large selections or many slots can still cause a pause. Start with a few copies and measure server
load; the allowed range is 0–16 per template.

WorldGuard is optional. When installed, each instance receives an independent copy of all
regions in the template world: cuboids, polygons, global regions, priorities, owners, members,
parent relationships and flags. Link your arena normally with
`/zombies setregion Hospital hospital_region`, then configure the instance count. Region IDs
and coordinates stay the same in each copy, so the linked shape controls arena containment.
The two-corner box controls which map blocks are copied, independently of the linked region.
Teleport/spawn flag destinations inside the template world are remapped to the instance;
destinations in other worlds stay unchanged. Changes to a template's regions are applied on
restart/reload. Existing instance region data is replaced during preparation. If a linked
region is missing or WorldGuard region copying fails, affected copies are unavailable and
the server logs explain why. WorldGuard must remain enabled for linked instance arenas.
WorldGuard's per-world configuration files and other plugins' world settings are not copied.

Between matches the plugin performs its normal door/barrier/mob/machine reset and reuses
the same world. The selected map is restored on startup/reload, not after every match;
arbitrary terrain changes from other plugins are not undone between matches. Instance world
directories named `mz_instance_*` are plugin-owned disposable copies. They are unloaded on
shutdown and rebuilt on next use from an empty directory and the selected map snapshot.
Only copies carrying the matching ownership marker can be replaced or deleted. Setting the count
to `0` removes all owned slots, including older staging directories, on the next restart/reload.
Loaded worlds are kept if unloading fails; check the console and retry after restart. Reducing
the count to a nonzero value leaves unused directories on disk.

## Building

From the project folder run `./gradlew build` (`.\gradlew.bat build` on Windows). The plugin jar is
created in `Core/build/libs`. No BuildTools run is needed.

The Gradle daemon runs on JDK 21; the Java 25 toolchain required by the Paper 26.x API is
picked up automatically. If neither JDK 21 nor JDK 25 is installed locally, Gradle downloads
a Temurin toolchain on the first build (Foojay resolver in `settings.gradle`).

## Server software

Servers need Java 21 for 1.21.x, or Java 25 for 26.x.

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
