package com.theprogrammingturkey.comz.config;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.signs.SignRegistry;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager
{
	private static final Map<COMZConfig, CustomConfig> CONFIGS = new HashMap<>();
	private static ConfigSetup mainConfig;

	/** False while config.yml is not valid YAML: then nothing may be saved to it (it would replace the whole file). */
	private static boolean configWritable = true;

	/**
	 * Whether config.yml may be saved right now: it was valid when loaded, and still is (it is read again, as it may have
	 * been edited since). A file that can't be parsed would load empty, and saving that would replace all of it.
	 */
	public static boolean isConfigWritable()
	{
		if(!configWritable)
			return false;
		java.io.File file = new java.io.File(COMZombies.getPlugin().getDataFolder(), "config.yml");
		if(!file.exists())
			return true;
		try
		{
			new org.bukkit.configuration.file.YamlConfiguration().loadFromString(
					java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
			return true;
		} catch(java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e)
		{
			COMZombies.getPlugin().getLogger().warning("config.yml is not valid YAML right now, so it was not saved to: " + e.getMessage());
			return false;
		}
	}

	public static void loadFiles()
	{
		COMZombies plugin = COMZombies.getPlugin();
		// Put an existing config.yml in the current layout (order, descriptions, new settings), keeping every value
		ConfigOrganizer.Result organized = ConfigOrganizer.organize(plugin);
		configWritable = organized.valid();
		plugin.saveDefaultConfig(); // only writes the default when there is no config.yml yet
		plugin.reloadConfig();
		plugin.getConfig().options().copyDefaults(true);

		boolean changed = false;
		// One-time updates of old default values, for configs made before layout 2. They used to run on every start,
		// which put a radius of 2 or 4, or the key label E, back to the old default even when chosen on purpose.
		if(organized.previousVersion() < ConfigOrganizer.LAYOUT_VERSION)
		{
			double doorRadius = plugin.getConfig().getDouble("config.doorProximity.radius");
			if(doorRadius == 2.0 || doorRadius == 4.0)
			{
				plugin.getConfig().set("config.doorProximity.radius", 3);
				changed = true;
			}
			if("E".equals(plugin.getConfig().getString("config.doorProximity.key")))
			{
				plugin.getConfig().set("config.doorProximity.key", "F");
				changed = true;
			}
		}
		// Old default texts become the new ones; a text that was changed is left alone
		changed |= replaceOldDefault(plugin, "config.doorProximity.tooltip.open", "&aPress {key} to open &7- {price} points",
				"&aRight-click to open &7- {price}", "&aPress {key} to open &7- {price}");
		changed |= replaceOldDefault(plugin, "config.doorProximity.tooltip.no_power", "&cRequires power &7- {price} points",
				"&cThis door requires power!");
		changed |= replaceOldDefault(plugin, "config.doorProximity.tooltip.no_funds", "&cNeed {missing} more points &7- {price} points",
				"&cNot enough points!");
		changed |= replaceOldDefault(plugin, "config.barrierProximity.tooltip.repair", "&aPress {key} to start repairing &7- 1 stage per second",
				"&aPress {key} to fully repair &7- 1 second cooldown");
		if(!plugin.getConfig().contains("config.interaction.key", true))
		{
			plugin.getConfig().set("config.interaction.key", plugin.getConfig().getString("config.doorProximity.key", "F"));
			changed = true;
		}
		if(changed && configWritable)
			plugin.saveConfig();

		mainConfig = new ConfigSetup();
		CONFIGS.put(COMZConfig.GUNS, new CustomConfig(COMZConfig.GUNS));
		CONFIGS.put(COMZConfig.ARENAS, new CustomConfig(COMZConfig.ARENAS));
		CONFIGS.put(COMZConfig.SIGNS, new CustomConfig(COMZConfig.SIGNS));
		CONFIGS.put(COMZConfig.KITS, new CustomConfig(COMZConfig.KITS));
		CONFIGS.put(COMZConfig.STATS, new CustomConfig(COMZConfig.STATS));
		CONFIGS.put(COMZConfig.GAME_SIGNS, new CustomConfig(COMZConfig.GAME_SIGNS));

		mainConfig.setup();
		SignRegistry.INSTANCE.load();
		com.theprogrammingturkey.comz.game.signs.SignStyles.load();
	}

	private static boolean replaceOldDefault(COMZombies plugin, String path, String current, String... old)
	{
		String value = plugin.getConfig().getString(path);
		for(String o : old)
		{
			if(o.equals(value))
			{
				plugin.getConfig().set(path, current);
				return true;
			}
		}
		return false;
	}

	public static CustomConfig getConfig(COMZConfig comzConfig)
	{
		//The default is just here so we don't get warnings in code about possible nulls really
		return CONFIGS.getOrDefault(comzConfig, CONFIGS.get(COMZConfig.ARENAS));
	}

	public static ConfigSetup getMainConfig()
	{
		return mainConfig;
	}
}
