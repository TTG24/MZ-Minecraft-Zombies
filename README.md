# MZ-Minecraft-Zombies
A fork of the Call_Of_Minecraft-Zombies Repo from TurkeyDev under MIT Licensing

Original project: https://github.com/TheTurkeyDev/Call_Of_Minecraft-Zombies

## What this fork changes

- **One jar for 1.21 and newer.** The per-version NMS modules are replaced by a single module that only uses the
  Bukkit API, so the plugin runs on 1.21 through 26.x without needing a new build for each Minecraft version.
- **Configurable signs.** The text on every game sign (guns, perks, doors, mystery box, barriers, join signs and more)
  is set in the `signs` section of `config.yml`. Signs are recognised by their location, so the text can be anything.
- **Smarter barriers.** Zombies have to be right next to a barrier to break it, and on Paper they are walked to their
  barrier and held there until it breaks (see [Server software](#server-software)).
- **Fixes**, including: barriers letting players through at negative coordinates, zombies breaking barriers from far
  away, barrier cracks disappearing between rounds, the power switch never working, kit round rewards never firing,
  and the gun zoom getting stuck.

## Building

Requires JDK 21. From the project folder run `gradlew build` (`.\gradlew.bat build` on Windows). The plugin jar is
created in `Core/build/libs`. No BuildTools run is needed.

Servers need Java 21 for 1.21.x, or Java 25 for 26.x.

## Server software

**Paper (or a fork such as Purpur) is recommended.** Everything works on Spigot too, except one feature: on Paper,
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
