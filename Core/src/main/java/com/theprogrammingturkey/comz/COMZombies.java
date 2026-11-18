package com.theprogrammingturkey.comz;

import java.io.File;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
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
import com.theprogrammingturkey.comz.game.managers.DoorProximityManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.kits.KitManager;
import com.theprogrammingturkey.comz.listeners.ArenaListener;
import com.theprogrammingturkey.comz.listeners.DiveListener;
import com.theprogrammingturkey.comz.listeners.DoorProximityListener;
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
import com.theprogrammingturkey.comz.util.Colors;
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
	 * Matches the "(MC: x.y)" segment in a Bukkit server-version string.
	 * Group 1 is the version number only (the trailing parenthesis is not captured).
	 */
	static final Pattern VERSION_PATTERN = Pattern.compile("\\(MC: (\\d+(?:\\.\\d+)*)\\)");
	/**
	 * Default plugin logger.
	 */
	public static final Logger log = Logger.getLogger("MinecraftZombies");
	/**
	 * Players currently performing some sort of action or maintenance
	 */
	// Read from the chat thread as well: thread-safe
	public java.util.concurrent.ConcurrentHashMap<Player, BaseAction> activeActions = new java.util.concurrent.ConcurrentHashMap<>();


	/**
	 * Players who are contained in this hash map are in sign edit for a given
	 * sign, the value that corresponds to the player is the sign that the
	 * player is editing.
	 */
	public java.util.concurrent.ConcurrentHashMap<Player, Location> isEditingASign = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Called when the plugin is reloading to cancel every remove spawn, create
	 * door, and arena setup operation.
	 */
	public void clearAllSetup()
	{
		activeActions.clear();
	}

	public static final String CONSOLE_PREFIX = "";
	// Matches the server theme (MOTD, join messages): [☠ MZ] in dark gray, dark red and red
	public static final String PREFIX = Colors.DARK_GRAY + "[" + Colors.DARK_RED + "☠ " + Colors.RED + "MZ" + Colors.DARK_GRAY + "]" + Colors.GRAY + " ";

	public static INMSUtil nmsUtil;

	public Vault vault;

	@Override
	public void onLoad()
	{
		migrateOldDataFolder();
	}

	/**
	 * The plugin used to be called COM_Zombies, and the data folder is named after the plugin. Moves an old
	 * plugins/COM_Zombies folder to the new name so existing arenas, signs and config carry over.
	 */
	private void migrateOldDataFolder()
	{
		File oldFolder = new File(getDataFolder().getParentFile(), "COM_Zombies");
		if(!oldFolder.isDirectory() || getDataFolder().exists())
			return;

		if(oldFolder.renameTo(getDataFolder()))
			log.log(Level.INFO, "Moved the old COM_Zombies data folder to {0}", getDataFolder().getName());
		else
			log.log(Level.SEVERE, "Could not move the old COM_Zombies data folder to {0}! Move it yourself while the server is off.", getDataFolder().getName());
	}

	@Override
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
		DoorProximityManager.INSTANCE.start();
		com.theprogrammingturkey.comz.util.Accessibility.load();
		com.theprogrammingturkey.comz.game.managers.GlowManager.INSTANCE.start();
		com.theprogrammingturkey.comz.game.ArenaWeather.start(this);
		com.theprogrammingturkey.comz.game.ZombieSounds.start(this);
		com.theprogrammingturkey.comz.game.BoxBeacons.start(this);
		com.theprogrammingturkey.comz.game.TeleporterLinks.start(this);
		com.theprogrammingturkey.comz.game.ZombieBrain.start(this);
		getServer().getMessenger().registerOutgoingPluginChannel(this, com.theprogrammingturkey.comz.util.BloodMessages.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, com.theprogrammingturkey.comz.util.GameOverMessages.CHANNEL);
		getServer().getMessenger().registerOutgoingPluginChannel(this, com.theprogrammingturkey.comz.game.ZombieRise.CHANNEL);
		com.theprogrammingturkey.comz.util.RoundMessages.start(this);
	}

	public void loadConfigFiles()
	{
		reloadConfig();
		ConfigManager.loadFiles();
		WeaponManager.loadGuns();
		KitManager.loadKits();
	}

	private void loadVersionSpecificCode()
	{
		String version = getMinecraftVersion();
		if(version == null)
			throw new IllegalStateException("Sorry, MinecraftZombies does not currently support server version " + Bukkit.getVersion());

		log.log(Level.INFO, COMZombies.CONSOLE_PREFIX + "Version info | MC: {0} | Bukkit: {1} & {2} | CB: {3}",
				new Object[] {version, Bukkit.getVersion(), Bukkit.getBukkitVersion(), Bukkit.getServer().getClass().getPackage().getName()});

		if(!isSupportedVersion(version))
			throw new IllegalStateException("Sorry, MinecraftZombies does not currently support server version " + version);

		nmsUtil = new BukkitApiUtil();
	}

	/**
	 * Any server from 1.21 onwards. Only the public Bukkit API is used, so newer versions
	 * (including the 26.x numbering) don't need a new support module.
	 */
	private static boolean isSupportedVersion(String version)
	{
		try
		{
			String[] parts = version.split("\\.");
			int major = Integer.parseInt(parts[0]);
			int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
			return major > 1 || minor >= 21;
		}
		catch(NumberFormatException e)
		{
			log.log(Level.WARNING, COMZombies.CONSOLE_PREFIX + "Could not parse server version \"{0}\" as a supported version.", version);
			return false;
		}
	}

	/**
	 * Registers every event in the event package
	 */
	public void registerEvents()
	{
		PluginManager m = getServer().getPluginManager();
		
		m.registerEvents(new ArenaListener(), this);
		m.registerEvents(new EntityListener(), this);
		m.registerEvents(new com.theprogrammingturkey.comz.listeners.GameOverGuard(), this);
		m.registerEvents(new PlayerChatListener(), this);
		m.registerEvents(new com.theprogrammingturkey.comz.listeners.ChatFormatListener(), this);
		m.registerEvents(new SignListener(), this);
		m.registerEvents(new OnPreCommandEvent(), this);
		m.registerEvents(new OnBlockInteractEvent(), this);
		m.registerEvents(new EXPListener(), this);
		m.registerEvents(new PowerUpDropListener(), this);
		m.registerEvents(new OnOutsidePlayerInteractEvent(), this);
		m.registerEvents(new PlayerListener(), this);
		m.registerEvents(new com.theprogrammingturkey.comz.listeners.InspectToolListener(), this);
		m.registerEvents(new OnInventoryChangeEvent(), this);
		m.registerEvents(new ScopeListener(), this);
		m.registerEvents(new DoorProximityListener(), this);
		m.registerEvents(new com.theprogrammingturkey.comz.listeners.ResourcePackListener(), this);
		m.registerEvents(new com.theprogrammingturkey.comz.listeners.HurtSoundListener(), this);
		// Sprint stamina and the sneak-jump dive/prone are now provided by the TacticalMovementPlugin + client mod,
		// so MZ's own copy of stamina was removed and the DiveListener events are no longer registered.
		// DiveListener is still constructed because WeaponListener / CrouchCommand reference it.
		DiveListener dive = new DiveListener();
		m.registerEvents(new WeaponListener(dive), this);
		// R-to-reload: the Tactical Movement client mod sends this empty plugin message when R is pressed.
		getServer().getMessenger().registerIncomingPluginChannel(this, "tacticalmovement:reload",
			(channel, player, message) -> WeaponListener.reloadHeldGun(player));
		// G-to-throw: the client mod sends this when G is pressed (throws a grenade without switching to it).
		getServer().getMessenger().registerIncomingPluginChannel(this, "tacticalmovement:grenade",
			(channel, player, message) -> WeaponListener.throwGrenade(player));
		com.theprogrammingturkey.comz.game.Crawlers.register(this);
		com.theprogrammingturkey.comz.util.Backups.start();
	}

	/**
	 * Disables the plugin.
	 */
	@Override
	public void onDisable()
	{
		com.theprogrammingturkey.comz.util.Backups.stop();
		// Persist current session points before endAll() clears them from the map.
		PointManager.INSTANCE.saveAll();
		DoorProximityManager.INSTANCE.stop();
		com.theprogrammingturkey.comz.util.Highlight.clearAll();
		com.theprogrammingturkey.comz.game.BoxBeacons.clearAll();
		GameManager.INSTANCE.endAll();
		// Players still in an arena (including post-match spectators) go home now: scheduled tasks no longer run here.
		for(org.bukkit.entity.Player online : Bukkit.getOnlinePlayers())
		{
			if(com.theprogrammingturkey.comz.game.CachedPlayerInfo.hasSavedInfo(online.getUniqueId()))
				com.theprogrammingturkey.comz.game.CachedPlayerInfo.restorePlayerInfoNow(online);
			if(com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.isInstanceWorld(online.getWorld()))
				online.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
		}
		GameManager.INSTANCE.getGames().forEach(game -> game.retire());
		com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.shutdown();
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
		return extractVersion(Bukkit.getVersion());
	}

	/**
	 * Extracts the Minecraft version from a Bukkit server-version string, e.g.
	 * "26.3-2647-4cd2cf6 (MC: 26.3)". Returns the version ("26.3") or null if absent.
	 * Package-private for unit testing without a running server.
	 */
	static String extractVersion(String bukkitVersion)
	{
		if(bukkitVersion == null)
			return null;
		Matcher matcher = VERSION_PATTERN.matcher(bukkitVersion);
		if(matcher.find())
			return matcher.group(1);
		return null;
	}
}

