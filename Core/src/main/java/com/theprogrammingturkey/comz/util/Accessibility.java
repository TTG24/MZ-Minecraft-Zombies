package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's accessibility choices (/z access), kept in accessibility.yml in the plugin folder:
 * <ul>
 *   <li>repair: "toggle" (press F to start repairing a barrier, F again to stop) or "hold" (repairs while F is held)</li>
 *   <li>glow: see the other players of your match glowing through walls, and in which colour</li>
 * </ul>
 */
public final class Accessibility
{
	/** Glow colours (the 16 chat colours, by their usual names). */
	public static final List<String> COLORS = List.of("white", "gray", "dark_gray", "black", "red", "dark_red", "gold", "yellow",
			"green", "dark_green", "aqua", "dark_aqua", "blue", "dark_blue", "light_purple", "dark_purple");

	public record Settings(boolean holdRepair, boolean glow, String color)
	{
	}

	private static final Settings DEFAULTS = new Settings(false, false, "aqua");
	private static final Map<UUID, Settings> SETTINGS = new ConcurrentHashMap<>();
	private static File file;

	private Accessibility()
	{
	}

	public static void load()
	{
		file = new File(COMZombies.getPlugin().getDataFolder(), "accessibility.yml");
		SETTINGS.clear();
		if(!file.exists())
			return;
		YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
		for(String key : yml.getKeys(false))
		{
			ConfigurationSection s = yml.getConfigurationSection(key);
			if(s == null)
				continue;
			try
			{
				String color = s.getString("glow-color", DEFAULTS.color()).toLowerCase(Locale.ROOT);
				SETTINGS.put(UUID.fromString(key), new Settings("hold".equalsIgnoreCase(s.getString("repair", "toggle")),
						s.getBoolean("glow", false), COLORS.contains(color) ? color : DEFAULTS.color()));
			} catch(IllegalArgumentException ignored)
			{
			}
		}
	}

	private static void save()
	{
		if(file == null)
			return;
		YamlConfiguration yml = new YamlConfiguration();
		SETTINGS.forEach((id, s) ->
		{
			yml.set(id + ".repair", s.holdRepair() ? "hold" : "toggle");
			yml.set(id + ".glow", s.glow());
			yml.set(id + ".glow-color", s.color());
		});
		try
		{
			yml.save(file);
		} catch(IOException e)
		{
			COMZombies.getPlugin().getLogger().warning("Couldn't save accessibility.yml: " + e.getMessage());
		}
	}

	public static Settings get(UUID id)
	{
		return SETTINGS.getOrDefault(id, DEFAULTS);
	}

	public static void setHoldRepair(UUID id, boolean hold)
	{
		Settings s = get(id);
		SETTINGS.put(id, new Settings(hold, s.glow(), s.color()));
		save();
	}

	public static void setGlow(UUID id, boolean glow)
	{
		Settings s = get(id);
		SETTINGS.put(id, new Settings(s.holdRepair(), glow, s.color()));
		save();
	}

	public static void setColor(UUID id, String color)
	{
		Settings s = get(id);
		SETTINGS.put(id, new Settings(s.holdRepair(), s.glow(), color));
		save();
	}
}
