package com.theprogrammingturkey.comz;

import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.theprogrammingturkey.comz.api.INMSUtil;
import com.theprogrammingturkey.comz.commands.CommandManager;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.actions.BaseAction;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.kits.KitManager;
import com.theprogrammingturkey.comz.listeners.ArenaListener;
import com.theprogrammingturkey.comz.listeners.EXPListener;
import com.theprogrammingturkey.comz.listeners.EntityListener;
import com.theprogrammingturkey.comz.listeners.OnBlockInteractEvent;
import com.theprogrammingturkey.comz.listeners.OnInventoryChangeEvent;
import com.theprogrammingturkey.comz.listeners.OnOutsidePlayerInteractEvent;
import com.theprogrammingturkey.comz.listeners.OnPreCommandEvent;
import com.theprogrammingturkey.comz.listeners.PlayerChatListener;
import com.theprogrammingturkey.comz.listeners.PlayerListener;
import com.theprogrammingturkey.comz.listeners.PowerUpDropListener;
import com.theprogrammingturkey.comz.listeners.ScopeListener;
import com.theprogrammingturkey.comz.listeners.SignListener;
import com.theprogrammingturkey.comz.listeners.WeaponListener;
import com.theprogrammingturkey.comz.support.compat.BukkitApiUtil;
import com.theprogrammingturkey.comz.util.PlaceholderHook;

/**
 * Main class plugin handler.
 *
 * @author COMZ
 */
public class COMZombies extends JavaPlugin
{
	public static final Random rand = new Random();
	/**
	 * Default plugin logger.
	 */
	public static final Logger log = Logger.getLogger("COM:Z");
	/**
	 * Players currently performing some sort of action or maintenance
	 */
	public HashMap<Player, BaseAction> activeActions = new HashMap<>();


	/**
	 * Players who are contained in this hash map are in sign edit for a given
	 * sign, the value that corresponds to the player is the sign that the
	 * player is editing.
	 */
	public HashMap<Player, Location> isEditingASign = new HashMap<>();

	/**
	 * Called when the plugin is reloading to cancel every remove spawn, create
	 * door, and arena setup operation.
	 */
	public void clearAllSetup()
	{
		activeActions.clear();
	}

	public static final String CONSOLE_PREFIX = "[Zombies] ";
	public static final String PREFIX = ChatColor.GREEN + "[ " + ChatColor.RED + ChatColor.ITALIC + "Zombies" + ChatColor.GREEN + " ]" + ChatColor.GRAY + " ";

	public static INMSUtil nmsUtil;

	public Vault vault;

	public void onEnable()
	{
		loadVersionSpecificCode();
		loadConfigFiles();

		vault = new Vault();

		if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
		{
			new PlaceholderHook().register();
		}

		registerEvents();

		getCommand("zombies").setExecutor(CommandManager.INSTANCE);

		log.info(COMZombies.CONSOLE_PREFIX + "has been enabled!");

		GameManager.INSTANCE.loadAllGames();
	}

	public void loadConfigFiles()
	{
		reloadConfig();
		ConfigManager.loadFiles();
		WeaponManager.loadGuns();
		KitManager.loadKits();
		PointManager.INSTANCE.saveAll();
	}

	private void loadVersionSpecificCode()
	{
		String version = getMinecraftVersion();
		if(version == null)
			throw new IllegalStateException("Sorry, COM:Z Does not current support server version" + Bukkit.getVersion());

		log.info(COMZombies.CONSOLE_PREFIX + "Version info | MC: " + version + " | Bukkit: " + Bukkit.getVersion() + " & " + Bukkit.getBukkitVersion() + " | CB: " + Bukkit.getServer().getClass().getPackage().getName());

		if(!isSupportedVersion(version))
			throw new IllegalStateException("Sorry, COM:Z Does not current support server version" + version);

		nmsUtil = new BukkitApiUtil();
	}

	/**
	 * Any server from 1.21 onwards. Only the public Bukkit API is used, so newer versions
	 * (including the 26.x numbering) don't need a new support module.
	 */
	private static boolean isSupportedVersion(String version)
	{
		String[] parts = version.split("\\.");
		int major = Integer.parseInt(parts[0]);
		int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
		return major > 1 || minor >= 21;
	}

	/**
	 * Registers every event in the event package
	 */
	public void registerEvents()
	{
		PluginManager m = getServer().getPluginManager();
		m.registerEvents(new WeaponListener(), this);
		m.registerEvents(new ArenaListener(), this);
		m.registerEvents(new EntityListener(), this);
		m.registerEvents(new PlayerChatListener(), this);
		m.registerEvents(new SignListener(), this);
		m.registerEvents(new OnPreCommandEvent(), this);
		m.registerEvents(new OnBlockInteractEvent(), this);
		m.registerEvents(new EXPListener(), this);
		m.registerEvents(new PowerUpDropListener(), this);
		m.registerEvents(new OnOutsidePlayerInteractEvent(), this);
		m.registerEvents(new PlayerListener(), this);
		m.registerEvents(new OnInventoryChangeEvent(), this);
		m.registerEvents(new ScopeListener(), this);
	}

	/**
	 * Disables the plugin
	 */
	public void onDisable()
	{
		reloadConfig();
		GameManager.INSTANCE.endAll();
		log.info(COMZombies.CONSOLE_PREFIX + "has been disabled!");
	}


	public static COMZombies getPlugin()
	{
		return JavaPlugin.getPlugin(COMZombies.class);
	}

	public static int scheduleTask(Runnable runnable)
	{
		return COMZombies.scheduleTask(0, runnable);
	}

	public static int scheduleTask(long delay, Runnable runnable)
	{
		return COMZombies.scheduleTask(delay, -1, runnable);
	}

	public static int scheduleTask(long delay, long period, Runnable runnable)
	{
		COMZombies plugin = COMZombies.getPlugin();
		if(plugin.isEnabled())
			return Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, runnable, delay, period);
		return -1;
	}

	public static String getMinecraftVersion()
	{
		Matcher matcher = Pattern.compile("(\\(MC: )([\\d.]+)(\\))").matcher(Bukkit.getVersion());
		if(matcher.find())
			return matcher.group(2);
		return null;
	}
}
