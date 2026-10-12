package com.theprogrammingturkey.comz.config;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.signs.SignRegistry;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager
{
	private static final Map<COMZConfig, CustomConfig> CONFIGS = new HashMap<>();
	private static ConfigSetup mainConfig;

	public static void loadFiles()
	{
		COMZombies plugin = COMZombies.getPlugin();
		plugin.getConfig().options().copyDefaults(true);
		plugin.saveDefaultConfig();
		// Migrate the old door interaction labels while preserving custom tooltip text.
		boolean doorPromptChanged = false;
		// Update the former default ranges for existing installations.
		double doorRadius = plugin.getConfig().getDouble("config.doorProximity.radius");
		if(doorRadius == 2.0 || doorRadius == 4.0)
		{
			plugin.getConfig().set("config.doorProximity.radius", 3);
			doorPromptChanged = true;
		}
		if("E".equals(plugin.getConfig().getString("config.doorProximity.key")))
		{
			plugin.getConfig().set("config.doorProximity.key", "F");
			doorPromptChanged = true;
		}
		String openPrompt = plugin.getConfig().getString("config.doorProximity.tooltip.open");
		if("&aRight-click to open &7- {price}".equals(openPrompt) || "&aPress {key} to open &7- {price}".equals(openPrompt))
		{
			plugin.getConfig().set("config.doorProximity.tooltip.open", "&aPress {key} to open &7- {price} points");
			doorPromptChanged = true;
		}
		if("&cThis door requires power!".equals(plugin.getConfig().getString("config.doorProximity.tooltip.no_power")))
		{
			plugin.getConfig().set("config.doorProximity.tooltip.no_power", "&cRequires power &7- {price} points");
			doorPromptChanged = true;
		}
		if("&cNot enough points!".equals(plugin.getConfig().getString("config.doorProximity.tooltip.no_funds")))
		{
			plugin.getConfig().set("config.doorProximity.tooltip.no_funds", "&cNeed {missing} more points &7- {price} points");
			doorPromptChanged = true;
		}
		if("&aPress {key} to fully repair &7- 1 second cooldown".equals(plugin.getConfig().getString("config.barrierProximity.tooltip.repair")))
		{
			plugin.getConfig().set("config.barrierProximity.tooltip.repair", "&aPress {key} to start repairing &7- 1 stage per second");
			doorPromptChanged = true;
		}
		// Persist missing defaults on existing installations so every tooltip is editable.
		if(!plugin.getConfig().contains("config.interaction.key", true))
			plugin.getConfig().set("config.interaction.key", plugin.getConfig().getString("config.doorProximity.key", "F"));
		// copyDefaults adds missing keys while keeping existing custom values.
		if(doorPromptChanged || plugin.getConfig().options().copyDefaults())
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
