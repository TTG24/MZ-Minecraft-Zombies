package com.theprogrammingturkey.comz.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Puts an existing config.yml into the layout of the default one (the plugin's own config.yml): the same order, the same
 * descriptions, every new setting added, and every value the server owner set kept. Settings the default does not have
 * (from older versions, the original COMZombies plugin, or added by hand) are kept too, at the end of their section,
 * with a note. The file is only rewritten when that changes something, and a copy of it as it was goes to
 * config-backups/ first. A file that is not valid YAML is never touched.
 */
final class ConfigOrganizer
{
	/** The default config's layout version (its configVersion). Configs older than this get the one-time migrations. */
	static final int LAYOUT_VERSION = 2;
	private static final int KEEP_BACKUPS = 10;

	/** Settings older versions or the original plugin had, that nothing reads any more. */
	private static final Set<String> UNUSED = Set.of("vID", "config.Economy.CostPerJoin", "config.gameSettings.maxWave",
			"config.gameSettings.waveSpawnInterval", "config.Guns", "config.Guns.PistolMaterial");

	/**
	 * @param valid           false when config.yml could not be read as YAML (then it was left exactly as it was)
	 * @param previousVersion the file's configVersion before organizing (1 when it had none: an older config)
	 */
	record Result(boolean valid, int previousVersion)
	{
	}

	private ConfigOrganizer()
	{
	}

	static Result organize(JavaPlugin plugin)
	{
		File file = new File(plugin.getDataFolder(), "config.yml");
		if(!file.exists())
			return new Result(true, LAYOUT_VERSION); // saveDefaultConfig writes the default as it is

		String before;
		YamlConfiguration user = new YamlConfiguration();
		try
		{
			before = Files.readString(file.toPath(), StandardCharsets.UTF_8);
			user.loadFromString(before);
		} catch(IOException | InvalidConfigurationException e)
		{
			plugin.getLogger().log(Level.SEVERE, "config.yml could not be read, so it was left exactly as it is and the default "
					+ "settings are used until it is fixed (nothing is saved to it meanwhile): " + e.getMessage());
			return new Result(false, LAYOUT_VERSION);
		}
		int previous = user.getInt("configVersion", 1);

		YamlConfiguration out = new YamlConfiguration();
		try(InputStream in = plugin.getResource("config.yml"))
		{
			if(in == null)
				return new Result(true, previous);
			try(Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				out.load(reader);
			}
		} catch(IOException | InvalidConfigurationException e)
		{
			plugin.getLogger().log(Level.WARNING, "Could not read the default config.yml; config.yml was not reorganized", e);
			return new Result(true, previous);
		}

		String after = merge(user, out);
		if(after.equals(before))
			return new Result(true, previous);
		try
		{
			backup(plugin, before);
			Files.writeString(file.toPath(), after, StandardCharsets.UTF_8);
			plugin.getLogger().info("config.yml was put in the current layout (your values are kept; the old file is in config-backups/).");
		} catch(IOException e)
		{
			plugin.getLogger().log(Level.WARNING, "Could not rewrite config.yml in the current layout; it was left as it was", e);
		}
		return new Result(true, previous);
	}

	/** The default config (out) with every value of the server owner's (user) put in; returns the text to save. */
	static String merge(YamlConfiguration user, YamlConfiguration out)
	{
		for(String path : user.getKeys(true))
		{
			if(path.equals("configVersion"))
				continue;
			if(user.isConfigurationSection(path))
			{
				// An empty section the default doesn't have would otherwise be dropped
				if(!out.contains(path) && user.getConfigurationSection(path).getKeys(false).isEmpty())
					out.createSection(path);
				continue;
			}
			boolean known = out.contains(path) && !out.isConfigurationSection(path);
			out.set(path, user.get(path));
			if(!known && !path.startsWith("Players."))
				out.setComments(path, note(path, user.getComments(path)));
		}
		return out.saveToString();
	}

	/** The comment above a setting the default config doesn't have. */
	private static List<String> note(String path, List<String> own)
	{
		for(String unused : UNUSED)
			if(path.equals(unused) || path.startsWith(unused + "."))
				return List.of("No longer used by MinecraftZombies (from an older version). Safe to delete.");
		if(path.equals("config.gameSettings.barrierSignText"))
			return List.of("Old barrier sign text: used instead of signs.barrier while it is set.");
		if(own != null && !own.isEmpty())
			return own;
		return List.of("Not in the default config: kept as you set it.");
	}

	private static void backup(JavaPlugin plugin, String content) throws IOException
	{
		File dir = new File(plugin.getDataFolder(), "config-backups");
		if(!dir.exists() && !dir.mkdirs())
			throw new IOException("could not create " + dir);
		String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		Files.writeString(new File(dir, "config-" + stamp + ".yml").toPath(), content, StandardCharsets.UTF_8);
		File[] old = dir.listFiles((d, n) -> n.startsWith("config-") && n.endsWith(".yml"));
		if(old != null && old.length > KEEP_BACKUPS)
		{
			Arrays.sort(old, Comparator.comparing(File::getName));
			for(int i = 0; i < old.length - KEEP_BACKUPS; i++)
				old[i].delete();
		}
	}
}
