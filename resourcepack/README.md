# MZ Round Music resource pack

The default MinecraftZombies resource pack: it plays a stinger when a round **starts** and
when a round **ends**, for everyone in the match. Ships with 1.3+. The plugin plays the
events `mz:round_start` and `mz:round_end`; clients without this pack simply hear nothing
(the built-in round sounds are unaffected), so the pack is fully optional.

## Layout

```
resourcepack/
  pack.mcmeta                  # pack metadata (format 34-97.1, i.e. 1.16.5 - 26.x)
  assets/mz/sounds.json        # maps the two events to the two .ogg files
  assets/mz/sounds/round_start.ogg
  assets/mz/sounds/round_end.ogg
```

## Serving the default pack

The built archive `MZ-RoundMusic-1.21.zip` (not committed to git; rebuild it from the files
above) is hosted at a fixed URL keyed by its SHA1:

```
https://download.mc-packs.net/pack/<sha1 of the zip>.zip
```

In your server's `server.properties`:

```properties
require-resource-pack=false
resource-pack=https\://download.mc-packs.net/pack/<sha1>.zip
resource-pack-id=<any uuid, e.g. `uuidgen -r`>
resource-pack-prompt={"text"\:"Custom zombie sounds", "color"\:"gold"}
resource-pack-sha1=<sha1>
```

Restart the server after changing these. `require-resource-pack=false` keeps players who
don't accept the pack playable — they just don't hear the stingers.

## Making your own .ogg sounds

1. **Encode to Ogg Vorbis.** Minecraft only plays `.ogg`. From an mp3:
   ```
   ffmpeg -i my_start.mp3 -c:a libvorbis -ar 44100 round_start.ogg
   ```
   Do the same for the end stinger. Anything ffmpeg can decode (wav, flac, m4a, ...) works as
   input. Keep the files short; they play for every round.

2. **Replace the files in place.** Drop your encodes at
   `assets/mz/sounds/round_start.ogg` and `assets/mz/sounds/round_end.ogg`.
   **Keep the file names** — `sounds.json` references them. The event keys are fixed by the
   plugin (`mz:round_start` / `mz:round_end`); only the audio behind them changes.

3. **Rebuild the zip with tar, not Compress-Archive.** Windows PowerShell's
   `Compress-Archive` writes zip entries with backslashes, which Minecraft silently rejects.
   From inside this folder:
   ```
   tar -a -c -f MZ-RoundMusic-1.21.zip pack.mcmeta assets
   ```
   (bsdtar ships with Windows 10+; the `a` flag makes it create a zip from the extension.)
   Sanity-check the entry names — they must use forward slashes:
   ```
   tar -tf MZ-RoundMusic-1.21.zip
   ```

4. **Host it and update the server.** Upload the new zip to your pack host, then put the new
   URL and SHA1 into `server.properties` (`resource-pack` / `resource-pack-sha1`) and restart.
   On mcpacks.net the URL is derived from the SHA1, so a new file = a new URL.

5. **Volume.** `config.gameSettings.roundMusicVolume` scales the stingers; `0` turns them off.

## sounds.json gotchas (verified in-game)

- Event **keys are not namespaced**: `"round_start"` is correct; it is registered as
  `mz:round_start` from the folder namespace. Writing `"mz.round_start"` registers
  `mz:mz.round_start` and the plugin's `mz:round_start` event finds nothing.
- Sound **names are namespaced**: `"name": "mz:round_start"` is correct. A bare
  `"round_start"` resolves against the `minecraft` namespace and fails with
  `File minecraft:sounds/round_start.ogg does not exist`.
- `pack.mcmeta` for 1.21.9+ clients must use `min_format`/`max_format` as `[major, minor]`
  arrays (or `supported_formats`); a plain `pack_format` integer is ignored and the pack is
  rejected as incompatible.
