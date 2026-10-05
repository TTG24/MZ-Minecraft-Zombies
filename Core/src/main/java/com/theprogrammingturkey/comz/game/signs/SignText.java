package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the text shown on game signs from the "config.signs" section of config.yml.
 * Each sign type has 4 lines that can use & color codes and {placeholders}.
 */
public class SignText
{
	private static final Map<String, List<String>> DEFAULTS = new HashMap<>();

	static
	{
		DEFAULTS.put("join_waiting", Arrays.asList("&c[Zombies]", "&bJoin", "{arena}", "&aPlayers: {players}/{max}"));
		DEFAULTS.put("join_ingame", Arrays.asList("&a{arena}", "&cInProgress", "&cWave: {wave}", "&4Alive: {alive}"));
		DEFAULTS.put("join_maintenance", Arrays.asList("&4[MAINTENANCE]", "{arena}", "Game will be", "available soon!"));
		DEFAULTS.put("spectate", Arrays.asList("&c[Zombies]", "&bSpectate", "&cArena:", "{arena}"));
		DEFAULTS.put("kit", Arrays.asList("&c[Zombies]", "&bKit", "&c{kit}", ""));
		DEFAULTS.put("power", Arrays.asList("&c[Zombies]", "&bPower", "", ""));
		DEFAULTS.put("ammo_crate", Arrays.asList("&c[Zombies]", "&bAmmo Crate", "{price}", ""));
		DEFAULTS.put("grenade", Arrays.asList("&c[Zombies]", "&bGrenade", "{price}", ""));
		DEFAULTS.put("teleporter", Arrays.asList("&c[Zombies]", "&bTeleporter", "{teleporter}", "{price}"));
		DEFAULTS.put("gun", Arrays.asList("&e[Zombies]", "&aGun", "&9{gun}", "&c{price} / {refill}"));
		DEFAULTS.put("perk", Arrays.asList("&c[Zombies]", "&bPerk Machine", "&a{perk}", "&e{price}"));
		DEFAULTS.put("pack_a_punch", Arrays.asList("&c[Zombies]", "&5Pack-a-Punch", "&6{price}", ""));
		DEFAULTS.put("mystery_box", Arrays.asList("&c[Zombies]", "&bMystery Box", "&9{price}", ""));
		DEFAULTS.put("barrier", Arrays.asList("[BarrierRepair]", "", "Break to repair", ""));
		DEFAULTS.put("door", Arrays.asList("&c[Zombies]", "&6Door", "&aOpen for:", "&9{price}"));
	}

	/**
	 * @param key    the sign type's key under "config.signs"
	 * @param values placeholder values, e.g. "price" replaces {price}
	 * @return the 4 lines to show on the sign
	 */
	public static String[] render(String key, Map<String, String> values)
	{
		FileConfiguration config = COMZombies.getPlugin().getConfig();
		List<String> lines = Collections.emptyList();
		// The barrier sign was configurable before the rest, so its old setting still takes priority if present
		if(key.equals("barrier"))
			lines = config.getStringList("config.gameSettings.barrierSignText");
		if(lines.isEmpty())
			lines = config.getStringList("config.signs." + key);
		if(lines.isEmpty())
			lines = DEFAULTS.getOrDefault(key, Collections.emptyList());

		String[] result = new String[4];
		for(int i = 0; i < 4; i++)
		{
			String line = i < lines.size() ? lines.get(i) : "";
			for(Map.Entry<String, String> value : values.entrySet())
				line = line.replace("{" + value.getKey() + "}", value.getValue());
			result[i] = ChatColor.translateAlternateColorCodes('&', line);
		}
		return result;
	}

	public static String[] render(String key)
	{
		return render(key, Collections.emptyMap());
	}

	public static void apply(Sign sign, String[] lines)
	{
		for(int i = 0; i < 4; i++)
			sign.setLine(i, lines[i]);
		sign.update(true);
	}
}
