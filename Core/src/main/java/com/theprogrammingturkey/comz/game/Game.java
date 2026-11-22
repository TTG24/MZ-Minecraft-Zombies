//MODES
//DISABLED
//INGAME
//STARTING
//WAITING
//ERROR

package com.theprogrammingturkey.comz.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.ConfigSetup;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.game.managers.BarrierManager;
import com.theprogrammingturkey.comz.game.managers.BoxManager;
import com.theprogrammingturkey.comz.game.managers.DoorManager;
import com.theprogrammingturkey.comz.game.managers.DownedPlayerManager;
import com.theprogrammingturkey.comz.game.managers.PerkManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.PowerUpManager;
import com.theprogrammingturkey.comz.game.managers.SignManager;
import com.theprogrammingturkey.comz.game.managers.TeleporterManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.kits.KitManager;
import com.theprogrammingturkey.comz.leaderboards.Leaderboard;
import com.theprogrammingturkey.comz.leaderboards.PlayerStats;
import com.theprogrammingturkey.comz.listeners.customEvents.GameStartEvent;
import com.theprogrammingturkey.comz.spawning.RoundSpawnType;
import com.theprogrammingturkey.comz.spawning.SpawnManager;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.ApiCompat;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/**
 * Main game class.
 */
public class Game
{
	/** Default gun when an arena has no explicit StartingGun configured. */
	static final String DEFAULT_STARTING_GUN = "M1911";

	// Player inventory slot layout (absolute slots in the 9x6 + hotbar grid).
	// Knife occupies the hotbar; guns, perks and the grenade occupy one row
	// of the main inventory. Other systems (weapon swap, inventory clicks)
	// rely on these exact positions.
	static final int SLOT_KNIFE = 0;
	static final int SLOT_KNIFE_MARKER = 27;
	static final int SLOT_GUN_1 = 28;
	static final int SLOT_GUN_2 = 29;
	static final int SLOT_GUN_3 = 30;
	static final int SLOT_PERK_1 = 31;
	static final int SLOT_PERK_2 = 32;
	static final int SLOT_PERK_3 = 33;
	static final int SLOT_PERK_4 = 34;
	static final int SLOT_GRENADE = 35;

	/**
	 * List of every player contained in game.
	 */
	public Map<Player, GamePlayer> gamePlayers = new LinkedHashMap<>();
	/** How long a player who left (quit, crashed or /z leave) may still /z rejoin the running match. */
	public static final long REJOIN_WINDOW_MILLIS = 5 * 60 * 1000L;
	/** When each player who left mid-match left it. */
	private final Map<java.util.UUID, Long> leftAt = new java.util.HashMap<>();

	private boolean debugMode = false;

	/**
	 * Status of the game.
	 */
	private GameStatus status = GameStatus.DISABLED;

	/**
	 * If double points is active.
	 */
	private boolean doublePoints = false;

	/**
	 * If fire salse is active
	 */
	private boolean isFireSale = false;

	/**
	 * If insta kill is active.
	 */
	private boolean instaKill = false;

	/** Players held in spectator mode after a match ends, waiting to be sent home. */
	private final List<Player> gameOverSpectators = new ArrayList<>();

	/** Task id that returns {@link #gameOverSpectators} to their pre-match location. */
	private int gameOverReturnTask = -1;

	/** Repeating task id for the looping ambient track; -1 means none is running. */
	private int ambientMusicTask = -1;

	/** Per-match kill/revive/death counts accumulated while the match is live. */
	private final Map<UUID, PlayerMatchStats> matchStats = new HashMap<>();

	/** Final round of the last finished match; shown on the post-match leaderboard. */
	private int finalRoundReached;

	// Only templates are persisted. Runtime copies have their own managers and match state.
	private String templateName;
	private int instanceCount;
	private boolean templateMode;
	/** Ephemeral single-player match spun up on demand; the whole instance is destroyed when it empties. */
	private boolean soloInstance;
	private long matchGeneration;
	private boolean retired;

	/** Players pinned in place by the no-lobby spawn freeze, and the spot they are anchored to. */
	private final Map<Player, Location> frozenPlayers = new HashMap<>();

	public void retire() { retired = true; matchGeneration++; starter.endTimer(); powerUpManager.clear(); }

	/** Changes whenever a match ends (or the game is retired): delayed work checks it is still the same match. */
	public long getMatchGeneration() { return retired ? -1 : matchGeneration; }

	/** Discard delayed work from a finished match or an arena replaced by reload. */
	public int scheduleMatchTask(long delay, Runnable action)
	{
		long generation = matchGeneration;
		return COMZombies.scheduleTask(delay, () ->
		{
			if(!retired && generation == matchGeneration) action.run();
		});
	}

	public boolean isInstance() { return templateName != null; }
	public String getTemplateName() { return isInstance() ? templateName : getName(); }
	public void setTemplateName(String name) { templateName = name; }
	public int getInstanceCount() { return instanceCount; }
	public void setInstanceCount(int count) { instanceCount = count; }
	public boolean isSoloInstance() { return soloInstance; }
	public void setSoloInstance(boolean solo) { soloInstance = solo; }
	public boolean isTemplate() { return !isInstance() && templateMode; }

	/**
	 * If the power is on
	 */
	private boolean powerOn = false;

	/**
	 * If the game has power enabled
	 */
	private boolean powerSetup;

	/** The mystery box's teddy bear: the chance (%) a spin turns one up, once the box has had teddyBearSafeUses spins where it is. */
	private int teddyBearPercent;
	private int teddyBearSafeUses;

	private int dogRoundEveryX;

	private boolean maxAmmoReplishClip;

	private String startingGun = DEFAULT_STARTING_GUN;

	/**
	 * Current wave number.
	 */
	private int waveNumber = 0;

	private boolean forceNight;

	/**
	 * Arena contained in the game.
	 */
	public Arena arena;

	/**
	 * Manager controlling zombie spawning and spawn points for the game.
	 */
	public SpawnManager spawnManager;

	/**
	 * Auto start timer, constructed upon join.
	 */
	public AutoStart starter;

	/**
	 * contains all of the Mysteryboxes in the game
	 */
	public BoxManager boxManager;

	/**
	 * contains all of the Barriers in the game
	 */
	public BarrierManager barrierManager;

	/**
	 * contains all of the doors in the game
	 */
	public DoorManager doorManager;

	/**
	 * contains all of the perks in the game for the players
	 */
	public PerkManager perkManager;

	/**
	 * contains all of the powerUps in the game as well as the powerUps that are currently dropped
	 */
	public PowerUpManager powerUpManager;

	/**
	 * contains all of the teleporters in the game
	 */
	public TeleporterManager teleporterManager;
	public com.theprogrammingturkey.comz.game.managers.LinkManager linkManager;

	/**
	 * contains all of the downed players in the game
	 */
	public DownedPlayerManager downedPlayerManager;

	/**
	 * contains all of the downed players in the game
	 */
	public SignManager signManager;

	/**
	 * Scoreboard used to manage players points
	 */
	public GameScoreboard scoreboard;

	/**
	 * Max players is used to check for player count and if not to remove a player if the game is
	 * full.
	 */
	public int maxPlayers;
	/**
	 * minimum number of players before the game will auto start.
	 */
	public int minPlayers;

	public boolean changingRound = false;

	/**
	 * Creates a game based off of the parameters and arena configuration file.
	 *
	 * @param name of the game
	 */
	public Game(String name)
	{
		this.arena = new Arena(name);

		starter = new AutoStart(this, 60);

		spawnManager = new SpawnManager(this);
		boxManager = new BoxManager(this);
		barrierManager = new BarrierManager(this);
		doorManager = new DoorManager(this);
		perkManager = new PerkManager();
		powerUpManager = new PowerUpManager();
		teleporterManager = new TeleporterManager(this);
		linkManager = new com.theprogrammingturkey.comz.game.managers.LinkManager(this);
		downedPlayerManager = new DownedPlayerManager();
		signManager = new SignManager(this);

		scoreboard = new GameScoreboard(this);
	}

	/**
	 * Gets the weapons the player currently has
	 *
	 * @param player to get the weapons of
	 * @return PlayerWeaponManager of the players weapons
	 */
	public PlayerWeaponManager getPlayersWeapons(Player player)
	{
		return gamePlayers.getOrDefault(player, new GamePlayer(player)).getWeaponManager();
	}

	public List<Player> getPlayersInGame()
	{
		return gamePlayers.values().stream().filter(gp -> gp.isInGame() || gp.isDead()).map(gp -> gp.getPlayer()).collect(Collectors.toList());
	}

	/** True for a player who LEFT this match (quit, crashed, /z leave). Players who are only dead are not counted. */
	public boolean wasDisconnected(Player player)
	{
		GamePlayer gp = gamePlayers.get(player);
		return gp != null && gp.hasLeftGame();
	}

	/** Milliseconds this player still has to rejoin (0 when they can't). */
	public long rejoinMillisLeft(Player player)
	{
		Long at = leftAt.get(player.getUniqueId());
		if(status != GameStatus.INGAME || at == null || !wasDisconnected(player))
			return 0;
		return Math.max(0, at + REJOIN_WINDOW_MILLIS - System.currentTimeMillis());
	}

	public static String formatMillis(long millis)
	{
		long s = (millis + 999) / 1000;
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	/**
	 * @return if Double Points is active
	 */
	public boolean isDoublePoints()
	{
		return doublePoints;
	}

	/**
	 * @return if Insta Kill is active
	 */
	public boolean isInstaKill()
	{
		return instaKill;
	}

	/**
	 * Turns on or off instakill.
	 */
	public void setInstaKill(boolean isInstaKill)
	{
		instaKill = isInstaKill;
	}

	/**
	 * Turns on double points.
	 */
	public void setDoublePoints(boolean isDoublePoints)
	{
		doublePoints = isDoublePoints;
	}

	/**
	 * @return if the game currently has the power on
	 */
	public boolean isPowered()
	{
		return powerOn;
	}

	/**
	 * Turns off the power for the game
	 */
	public void turnOffPower()
	{
		powerOn = false;
		com.theprogrammingturkey.comz.game.signs.PowerSign.refreshSigns(this);
	}

	/**
	 * Turns on the power for the game
	 */
	public void turnOnPower()
	{
		turnOnPower(null);
	}

	/**
	 * Turns on the power. Lightning strikes a few times around the power switch (at), at random moments over a couple of
	 * seconds, each strike with an electric zap (mz:zap, from the pack), and everyone in the match hears the power surge
	 * (mz:power_on): a charge whine, the arc snapping on, then the mains buzzing and spitting sparks.
	 */
	public void turnOnPower(Location at)
	{
		powerOn = true;
		Location centre = at != null ? at : arena.getPlayerTPLocation();
		for(Player pl : getPlayersAndSpectators())
			pl.playSound(pl.getLocation(), "mz:power_on", org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
		if(centre != null && centre.getWorld() != null)
		{
			int strikes = 3 + COMZombies.rand.nextInt(3);
			long when = 14; // the first one as the arc snaps on in the sound
			for(int i = 0; i < strikes; i++)
			{
				final boolean first = i == 0;
				scheduleMatchTask(when, () -> strikePower(centre, first));
				when += 6 + COMZombies.rand.nextInt(18);
			}
		}

		// Power doors set to open with the power swing open by themselves
		for(com.theprogrammingturkey.comz.game.features.Door door : doorManager.getDoors())
			if(door.requiresPower() && door.opensWithPower() && !door.isOpened())
			{
				door.openDoor();
				door.playerDoorOpenSound();
			}

	}

	/** One power-on strike: lightning (just the effect: no fire, no damage) somewhere near the switch, and a zap there. */
	private void strikePower(Location centre, boolean first)
	{
		World world = centre.getWorld();
		if(world == null)
			return;
		double a = COMZombies.rand.nextDouble() * Math.PI * 2, r = first ? 0 : 3 + COMZombies.rand.nextDouble() * 9;
		int x = (int) Math.floor(centre.getX() + Math.cos(a) * r), z = (int) Math.floor(centre.getZ() + Math.sin(a) * r);
		// On the roof / ground above that spot (the bolt comes down from the sky)
		Location hit = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
		world.strikeLightningEffect(hit);
		world.playSound(hit, "mz:zap", org.bukkit.SoundCategory.MASTER, 3.0f, 0.85f + COMZombies.rand.nextFloat() * 0.3f);
		// Everyone in the match also hears it crackle, a bit quieter, wherever they are
		for(Player pl : getPlayersAndSpectators())
			if(pl.getWorld().equals(world) && pl.getLocation().distanceSquared(hit) > 48 * 48)
				pl.playSound(pl.getLocation(), "mz:zap", org.bukkit.SoundCategory.MASTER, 0.5f, 0.9f + COMZombies.rand.nextFloat() * 0.2f);
	}

	/**
	 * @return if power is enabled for the game
	 */
	public boolean hasPower()
	{
		return powerSetup;
	}

	public GameStatus getStatus()
	{
		return this.status;
	}

	public int getWave()
	{
		return this.waveNumber;
	}

	/** This match's kill/revive/death counts for {@code player}, or null before they have any (tab list placeholders). */
	public PlayerMatchStats getMatchStats(UUID player)
	{
		return matchStats.get(player);
	}

	/** Zombies still to come this round: not yet spawned plus alive. */
	public int getZombiesLeft()
	{
		return (spawnManager.getMobsToSpawn() - spawnManager.getMobsSpawned()) + spawnManager.getZombiesAlive();
	}

	public boolean isPlayerDowned(Player player)
	{
		return downedPlayerManager.isDownedPlayer(player);
	}

	/**
	 * Makes this arena use power, so power doors, perks, pack-a-punch and teleporters need the power sign first.
	 */
	public void enablePower(Player player)
	{
		powerSetup = true;
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Power enabled! Type /z disablepower " + getName() + " to disable the power!");
		GameManager.INSTANCE.saveAllGames();
	}

	public void removePower(Player player)
	{
		powerSetup = false;
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Power disabled!");
		GameManager.INSTANCE.saveAllGames();
	}

	public void showSpawnLocations()
	{
		for(SpawnPoint point : spawnManager.getPoints())
		{
			Block block = point.getLocation().getBlock();
			point.setMaterial(block.getType());
			block.setType(Material.END_PORTAL_FRAME);
		}
	}

	/**
	 * Resets the blocks to air at the spawn locations
	 */
	public void resetSpawnLocationBlocks()
	{
		for(SpawnPoint point : spawnManager.getPoints())
			BlockUtils.setBlockToAir(point.getLocation());
	}

	/**
	 * @return if the game has been created fully
	 */
	public boolean isCreated()
	{
		if(status == GameStatus.DISABLED)
			return false;
		return arena.areMinAndMaxSet() && arena.areAllLocationsSet();
	}

	/**
	 * force starts the arena
	 */
	public void setStarting(boolean forced)
	{
		if(isTemplate()) return;
		if(status != GameStatus.WAITING && status != GameStatus.STARTING)
			return;

		if(status == GameStatus.STARTING && !forced)
			return;

		if(forced && status == GameStatus.STARTING && !starter.forced)
			starter.endTimer();

		// Arenas without a lobby have no pre-game holding spot, so skip the
		// countdown and start right away. Players get a brief spawn freeze in
		// startArena to get their bearings before the first wave.
		if(!arena.hasLobbyLocation())
		{
			status = GameStatus.STARTING;
			startArena();
			return;
		}

		int delay = ConfigManager.getMainConfig().arenaStartTime + 1;

		if(forced && delay > 6)
			delay = 6;

		starter = new AutoStart(this, delay);
		starter.startTimer();

		if(forced)
			starter.forced = true;

		sendMessageToPlayers(Colors.GRAY + "Game starting soon!");
		status = GameStatus.STARTING;
	}

	/**
	 * starts the game normally
	 */
	public void startArena()
	{
		if(isTemplate()) return;
		if(status == GameStatus.INGAME)
			return;

		Bukkit.getPluginManager().callEvent(new GameStartEvent(this));

		waveNumber = 0;
		changingRound = false;
		status = GameStatus.INGAME;
		// Start the looping ambient track for everyone in the match (normal arenas and solo
		// instances alike, since both route through startArena); it stops in endGame().
		startAmbientMusic();
		assignWarps(getPlayersInGame());
		for(Player player : getPlayersInGame())
		{
			player.teleport(getPlayerWarp(player));
			player.setAllowFlight(false);
			player.setFlying(false);
			player.setHealth(20D);
			player.setFoodLevel(20);
			player.setExp(0);
			player.setLevel(0);
			PointManager.INSTANCE.setPoints(player, 500);
			Leaderboard.getPlayerStatFromPlayer(player).incGamesPlayed();
		}

		// Without a lobby, players are teleported straight into the match, so
		// freeze them for 3 seconds to let them look around before the first wave.
		if(!arena.hasLobbyLocation())
			freezePlayers();

		scoreboard.update();
		if(this.boxManager.isMultiBox())
		{
			sendMessageToPlayers(Colors.RED + "All mystery boxes are being generated.");
			this.boxManager.loadAllBoxes();
		}
		else
		{
			this.boxManager.unloadAllBoxes();
			RandomBox b = this.boxManager.getRandomBox(null);
			if(b != null)
			{
				this.boxManager.setCurrentBox(b);
				this.boxManager.getCurrentbox().loadBox();
			}
		}
		spawnManager.update();

		for(LivingEntity entity : arena.getWorld().getLivingEntities())
		{
			if(arena.containsBlock(entity.getLocation()))
			{
				if(entity instanceof Player)
					continue;

				int times = 0;
				while(!entity.isDead())
				{
					entity.damage(20D);
					if(times > 20)
						break;
					times++;
				}
			}
		}
		nextWave();
		signManager.updateGame();
		KitManager.giveOutKits(this);
	}

	/**
	 * Pins players in place for 3 seconds: they can still look around (their view
	 * direction is preserved), but any movement or jumping snaps them back to their
	 * anchored spot. Used when a lobby-less arena starts the match immediately.
	 */
	private void freezePlayers()
	{
		sendMessageToPlayers(Colors.RED + "Frozen for 3 seconds - get ready!");
		frozenPlayers.clear();
		scheduleMatchTask(1, () -> freezePlayersTick(1));
	}

	/**
	 * One tick of the spawn freeze; re-schedules itself until 60 ticks elapse.
	 * A blindness overlay is applied on top and fades away step by step over
	 * the 3 seconds, easing players into the match once it lifts.
	 */
	private void freezePlayersTick(int tick)
	{
		if(tick >= 60)
		{
			frozenPlayers.clear();
			for(Player player : gamePlayers.keySet())
				if(player.isOnline())
					player.removePotionEffect(PotionEffectType.BLINDNESS);
			return;
		}

		// Blindness fades from level 5 to 1 over the freeze window (one step
		// down every 12 ticks) and is removed outright when the freeze ends.
		int blindAmp = 5 - (tick / 12);

		for(Player player : getPlayersInGame())
			if(player.isOnline())
			{
				frozenPlayers.putIfAbsent(player, player.getLocation().clone());
				// Keep them from dashing far in a single tick before the pin pulls them back.
				player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 128, false, true, false));
				player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, blindAmp, false, false, false));
			}

		for(Map.Entry<Player, Location> entry : frozenPlayers.entrySet())
		{
			Player p = entry.getKey();
			if(!p.isOnline() || !gamePlayers.containsKey(p))
				continue;
			Location anchor = entry.getValue();
			Location cur = p.getLocation();
			if(cur.getX() != anchor.getX() || cur.getY() != anchor.getY() || cur.getZ() != anchor.getZ())
				// Teleport to the anchor position but keep the player's current facing.
				p.teleport(new Location(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(), cur.getYaw(), cur.getPitch()));
		}

		scheduleMatchTask(1, () -> freezePlayersTick(tick + 1));
	}

	/**
	 * Whether a brand-new player may join this running match instead of
	 * spectating: only lobby-less arenas, only while the late-join round
	 * limit allows it.
	 */
	public boolean canLateJoin()
	{
		return !isTemplate() && status == GameStatus.INGAME && !arena.hasLobbyLocation()
				&& ConfigManager.getMainConfig().lateJoinRound > 0;
	}

	/**
	 * One tick of a late joiner's personal spawn freeze; re-schedules itself
	 * until 60 ticks elapse. Same 3-second pin plus fading blindness as the
	 * match-start freeze, but only affects this one player.
	 *
	 * @param player the late joiner
	 * @param tick   current tick of the 60-tick window
	 */
	private void freezeLateJoiner(Player player, int tick)
	{
		if(tick >= 60)
		{
			frozenPlayers.remove(player);
			if(player.isOnline())
				player.removePotionEffect(PotionEffectType.BLINDNESS);
			return;
		}

		if(player.isOnline() && gamePlayers.containsKey(player))
		{
			// Blindness fades from level 5 to 1 over the freeze window.
			int blindAmp = 5 - (tick / 12);
			frozenPlayers.putIfAbsent(player, player.getLocation().clone());
			player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 128, false, true, false));
			player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, blindAmp, false, false, false));

			Location anchor = frozenPlayers.get(player);
			Location cur = player.getLocation();
			if(cur.getX() != anchor.getX() || cur.getY() != anchor.getY() || cur.getZ() != anchor.getZ())
				// Keep them at the spawn point until the freeze lifts.
				player.teleport(new Location(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(), cur.getYaw(), cur.getPitch()));
		}

		scheduleMatchTask(1, () -> freezeLateJoiner(player, tick + 1));
	}

	private int roundToken = 0;

	/**
	 * Debug: jumps straight to the given round. Kills the mobs, cancels the current round's spawning and starts the new
	 * round (with the usual 10 second countdown).
	 */
	public boolean forceRound(int round)
	{
		if(status != GameStatus.INGAME || round < 1 || getPlayersInGame().isEmpty())
			return false;
		changingRound = true; // keeps mob removals below from starting a round themselves
		roundToken++;
		// Going back to an earlier round (to retest) starts it with whole barriers, as a fresh game would
		if(round <= waveNumber)
			barrierManager.unloadAllBarriers();
		spawnManager.resetForForcedRound(round);
		spawnManager.nuke();
		waveNumber = round - 1;
		changingRound = false;
		nextWave();
		return true;
	}

	/**
	 * Spawns in the next wave of zombies.
	 */
	public void nextWave()
	{
		// Only a running match changes rounds (endGame's clean-up kills the last zombies, which used to call this again)
		if(status != GameStatus.INGAME)
			return;
		if(getPlayersInGame().isEmpty())
		{
			this.endGame();
			return;
		}

		if(spawnManager.getZombiesAlive() != 0 || spawnManager.getMobsToSpawn() > spawnManager.getMobsSpawned())
			return;
		if(changingRound)
			return;

		changingRound = true;

		// A wave is only requested again once the previous one was cleared, so the end
		// stinger plays then. The first wave follows the arena start, so nothing ends.
		if(waveNumber > 0)
			playRoundMusic("round_end");

		COMZombies plugin = COMZombies.getPlugin();
		for(Player pl : getPlayersInGame())
			for(Player p : getPlayersInGame())
				pl.showPlayer(plugin, p);

		if(status != GameStatus.INGAME)
		{
			endGame();
			return;
		}

		waveNumber++;

		//get death and downed players and let them respawn or revive
		for(Player player : getDeathPlayers())
			addPlayer(player);

		downedPlayerManager.reviveDownedPlayers();

		int delay = 0;
		if(waveNumber != 1)
		{
			KitManager.giveOutKitRoundRewards(this);
			for(Player pl : getPlayersAndSpectators())
			{
				// Players with the tactical mod see the round as tally marks in the corner instead: the old count blinks
				// and goes away now, and comes back with the new round when it starts (and they get no portal sound)
				if(com.theprogrammingturkey.comz.util.RoundMessages.send(pl, -(waveNumber - 1)))
					continue;
				pl.playSound(pl.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, ConfigManager.getMainConfig().roundSoundVolume, 1);
				com.theprogrammingturkey.comz.util.TitleHold.show(pl, Colors.legacy(Colors.RED + "Round " + waveNumber), Colors.legacy(Colors.GRAY + "starting in 10 seconds"), 10, 60, 10);
			}
			delay = 200;
		}

		RoundSpawnType spawnType = spawnManager.nextWave(waveNumber, getPlayersInGame());

		final int token = ++roundToken;
		scheduleMatchTask(delay, () ->
		{
			// A forced round (debug) replaced this one while it was counting down
			if(token != roundToken)
				return;
			// The wave is actually starting now, so this also covers the first round
			// right after the arena (or the lobby-less instant start) begins.
			playRoundMusic("round_start");
			for(Player pl : getPlayersAndSpectators())
			{
				boolean modded = com.theprogrammingturkey.comz.util.RoundMessages.send(pl, waveNumber);
				if(!modded)
					com.theprogrammingturkey.comz.util.TitleHold.show(pl, Colors.legacy(Colors.RED + "Round " + waveNumber), Component.empty(), 10, 60, 10);
				switch(spawnType)
				{
					case REGULAR -> {
						if(!modded)
							pl.playSound(pl.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, ConfigManager.getMainConfig().roundSoundVolume, 1);
					}
					case HELL_HOUNDS -> pl.playSound(pl.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, ConfigManager.getMainConfig().roundSoundVolume, 1);
				}
			}

			// Every round gives back 2 grenades (up to the full 4)
			for(Player pl : getPlayersInGame())
			{
				PlayerWeaponManager weapons = getPlayersWeapons(pl);
				if(weapons != null)
					weapons.addGrenades(2);
			}

			spawnManager.startWave(waveNumber);
			signManager.updateGame();
			changingRound = false;
			scoreboard.update();
		});

	}

	/**
	 * Plays one of the bundled round music sounds (mz:round_start / mz:round_end) for
	 * everyone in the match. The sounds ship in the plugin's resource pack; clients
	 * without it hear nothing, so the built-in round sounds stay as the fallback.
	 */
	private void playRoundMusic(String file)
	{
		float volume = ConfigManager.getMainConfig().roundMusicVolume;
		if(volume <= 0)
			return;

		// Dead players (waiting out the round in spectator mode) hear the stingers too, game over included.
		for(GamePlayer gp : gamePlayers.values())
			if((gp.isInGame() || gp.isSpectating() || gp.isDead()) && gp.getPlayer().isOnline())
				gp.getPlayer().playSound(gp.getPlayer().getLocation(), "mz:" + file, volume, 1f);
	}

	/**
	 * Starts the bundled ambient track (mz:ambient) for the life of the match. It ships in the
	 * same resource pack as the round stingers; clients without it hear nothing. The track is
	 * replayed on a loop so it keeps playing for the whole match, and is stopped in
	 * {@link #endGame()}. Volume is re-read each loop, so /zombies reload changes apply live.
	 */
	private void startAmbientMusic()
	{
		if(ConfigManager.getMainConfig().ambientMusicVolume <= 0)
			return;
		if(ambientMusicTask != -1)
			return; // already looping for this game
		// Play once immediately so there is no initial wait, then keep replaying on a loop.
		playAmbientLoop();
		// The track is ~308 s long; replay at 305 s (6100 ticks) so consecutive loops overlap
		// a little rather than leaving an audible gap.
		ambientMusicTask = COMZombies.scheduleTask(6100, 6100, this::playAmbientLoop);
	}

	private void playAmbientLoop()
	{
		float volume = ConfigManager.getMainConfig().ambientMusicVolume;
		if(volume <= 0)
			return;
		for(Player pl : getPlayersInGame())
			if(pl.isOnline())
				pl.playSound(pl.getLocation(), "mz:ambient", volume, 1f);
	}

	private void stopAmbientMusic()
	{
		if(ambientMusicTask != -1)
		{
			Bukkit.getScheduler().cancelTask(ambientMusicTask);
			ambientMusicTask = -1;
		}
	}


	/** The game mode players play a match in: config.gameSettings.playerGameMode (ADVENTURE by default). */
	public static GameMode playGameMode()
	{
		String name = COMZombies.getPlugin().getConfig().getString("config.gameSettings.playerGameMode", "ADVENTURE");
		try
		{
			GameMode mode = GameMode.valueOf(name == null ? "ADVENTURE" : name.trim().toUpperCase(java.util.Locale.ROOT));
			if(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
				return mode;
		}
		catch(IllegalArgumentException ignored)
		{
		}
		return GameMode.ADVENTURE;
	}

	private void internalAddPlayer(Player player, int points)
	{
		gamePlayers.get(player).setState(PlayerState.IN_GAME);
		CachedPlayerInfo.savePlayerInfo(player);
		scoreboard.addPlayer(player);
		player.setHealth(20D);
		player.setFoodLevel(20);
		player.getInventory().clear();
		player.getInventory().setArmorContents(null);
		player.setExp(0);
		player.setLevel(0);
		// No lobby: they wait at (or, respawning or joining a running match, go to) their own game warp
		player.teleport(arena.hasLobbyLocation() ? arena.getLobbyLocation() : getPlayerWarp(player));
		PointManager.INSTANCE.setPoints(player, points);
		assignPlayerInventory(player);
		player.setGameMode(playGameMode());
		player.setInvulnerable(false); // a leftover from being downed in an earlier match

		COMZombies plugin = COMZombies.getPlugin();
		for(Player pl : getPlayersInGame())
		{
			for(Player p : Bukkit.getOnlinePlayers())
			{
				if(!(getPlayersInGame().contains(p)))
					pl.hidePlayer(plugin, p);
				else
					pl.showPlayer(plugin, p);
			}
		}

		BaseGun gun = WeaponManager.getGun(startingGun);
		Game game = GameManager.INSTANCE.getGame(player);
		if(game != null && gun != null)
		{
			PlayerWeaponManager manager = game.getPlayersWeapons(player);
			manager.addWeapon(gun.getNewInstance(player, 1));
			// Everyone starts with a full set of grenades (4)
			com.theprogrammingturkey.comz.game.weapons.Weapon grenade = WeaponManager.getWeapon("grenade");
			if(grenade != null)
				manager.addWeapon(grenade);
		}
		else if(gun == null)
		{
			COMZombies.log.log(Level.SEVERE, "The {0} is listed as the starting gun, but it could not be found! Did you forget to change this?", new Object[]{startingGun});
		}
	}

	/**
	 * Adds a player to the game
	 *
	 * @param player to be added to the game
	 */
	public void addPlayer(Player player)
	{
		if(isTemplate())
			return;
		// Joining while still watching another match's ending: that ending stops for them first
		Game watching = GameManager.INSTANCE.getGameOverSpectatingGame(player);
		if(watching != null)
			watching.releaseGameOverSpectator(player);
		switch(status)
		{
			case WAITING, STARTING ->
			{
				gamePlayers.put(player, new GamePlayer(player));
				internalAddPlayer(player, 500);

				sendMessageToPlayers(player.getName() + " has joined with " + getPlayersInGame().size() + "/" + maxPlayers + "!");
				if(getPlayersInGame().size() >= minPlayers)
				{
					setStarting(false);
					signManager.updateGame();
				}
			}
			case INGAME ->
			{
				if(wasDisconnected(player))
				{
					if(rejoinMillisLeft(player) <= 0)
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "It has been more than 5 minutes since you left this match, so you can't rejoin it.");
						return;
					}
					leftAt.remove(player.getUniqueId());

					gamePlayers.put(player, new GamePlayer(player));
					setDead(player);

					sendMessageToPlayers(player.getName() + " rejoined and can play in the next wave!");
					player.sendRawMessage(COMZombies.PREFIX + "You will be able to play in the next wave!");
				}
				else if(isPlayerDeath(player))
				{
					gamePlayers.put(player, new GamePlayer(player));
					internalAddPlayer(player, 500 * waveNumber);

					sendMessageToPlayers(player.getName() + " can play again!");
				}
				else if(canLateJoin() && waveNumber < ConfigManager.getMainConfig().lateJoinRound)
				{
					gamePlayers.put(player, new GamePlayer(player));
					internalAddPlayer(player, 500 * waveNumber);
					freezeLateJoiner(player, 0);

					sendMessageToPlayers(player.getName() + " joined in round " + waveNumber + "!");
					player.sendRawMessage(COMZombies.PREFIX + "You joined in round " + waveNumber + "!");
				}
				else
				{
					gamePlayers.put(player, new GamePlayer(player));
					addSpectator(player);
					sendMessageToPlayers(player.getName() + " has joined as a spectator!");
				}
			}
			default -> CommandUtil.sendMessageToPlayer(player, "Something may have gone wrong here, but MinecraftZombies caught it and will continue without error.");
		}
		signManager.updateGame();
	}

	public void addSpectator(Player player)
	{
		gamePlayers.computeIfAbsent(player, GamePlayer::new).setState(PlayerState.SPECTATING);
		setPlayerSpectatorMode(player);
	}

	/** Where each player died this match (before being moved to the spectate spot), for the game-over cutscene. */
	private final Map<UUID, Location> deathSpots = new HashMap<>();

	public void setDead(Player player)
	{
		if(status == GameStatus.INGAME && player.getGameMode() != GameMode.SPECTATOR)
			deathSpots.put(player.getUniqueId(), player.getLocation().clone());
		gamePlayers.computeIfAbsent(player, GamePlayer::new).setState(PlayerState.DEAD);
		setPlayerSpectatorMode(player);
	}

	public void setPlayerSpectatorMode(Player player)
	{
		CachedPlayerInfo.savePlayerInfo(player);
		scoreboard.addPlayer(player);
		player.setGameMode(GameMode.SPECTATOR);
		player.teleport(arena.getSpectateLocation());
	}

	/**
	 * Removes a player from the game
	 *
	 * @param player to be removed
	 */
	public void removePlayer(Player player)
	{
		if(downedPlayerManager.isDownedPlayer(player))
		{
			setDead(player);
			// Leaving while downed must not leave them invulnerable or with the crawl speed.
			downedPlayerManager.removeDownedPlayer(player);
		}
		if(soloInstance)
		{
			// Mark them out of the match (even if dead) so the solo match can end, then let
			// the instance manager kick them out of the world and tear it down when empty.
			if(gamePlayers.containsKey(player))
				gamePlayers.get(player).setState(PlayerState.LEFT_GAME);
			ArenaInstances.INSTANCE.onSoloPlayerRemoved(this, player);
		}
		else if(gamePlayers.containsKey(player))
		{
			gamePlayers.get(player).setState(PlayerState.LEFT_GAME);
			if(status == GameStatus.INGAME)
			{
				// They may come back within 5 minutes (/z rejoin); tell them now (when they used /z leave) and when it runs out
				long at = System.currentTimeMillis();
				leftAt.put(player.getUniqueId(), at);
				java.util.UUID id = player.getUniqueId();
				String name = getName();
				CommandUtil.sendClickableMessageToPlayer(player, Colors.GRAY + "You left the match. You can rejoin within "
						+ Colors.RED + "5 minutes" + Colors.GRAY + " with /z rejoin or ", "CLICK HERE", "/z rejoin");
				COMZombies.scheduleTask(REJOIN_WINDOW_MILLIS / 50, () ->
				{
					Long still = leftAt.get(id);
					Player p = org.bukkit.Bukkit.getPlayer(id);
					if(still == null || still != at || p == null || status != GameStatus.INGAME || !wasDisconnected(p))
						return;
					CommandUtil.sendMessageToPlayer(p, Colors.GRAY + "It has been 5 minutes since you left the match in " + Colors.RED + name
							+ Colors.GRAY + ", so you can no longer rejoin it.");
				});
			}
		}

		resetPlayer(player);

		if(status != GameStatus.DISABLED)
			sendMessageToPlayers(player.getName() + " has left the game! Only " + getPlayersInGame().size() + "/" + this.maxPlayers + " player(s) left!");

		// Players waiting out the round (dead, or rejoined mid-match) are not playing: when nobody is left alive and
		// active, the match is over instead of running on with an empty arena.
		boolean nobodyActive = getPlayersInGame().isEmpty() || gamePlayers.values().stream().noneMatch(GamePlayer::isInGame);
		if(nobodyActive && status != GameStatus.WAITING && status != GameStatus.DISABLED)
			endGame();
	}

	private void removePlayerActions(Player player)
	{
		double points = waveNumber;
		COMZombies.getPlugin().vault.addMoney(player, points);
		// Ƶ is the Ƶ currency symbol, escaped so the file's encoding can't garble it
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "You earned " + Colors.RED + "Ƶ" + (int) points + Colors.DARK_GREEN + " for reaching round " + Colors.RED + waveNumber + Colors.DARK_GREEN + "!");

		PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
		if(stats.getHighestRound() < this.waveNumber)
			stats.setHighestRound(this.waveNumber);

		// The record is the total earned in the match, not what was left unspent at the end
		int playerPoints = PointManager.INSTANCE.getPlayersEarned(player);
		if(stats.getMostPoints() < playerPoints)
			stats.setMostPoints(playerPoints);

		if(downedPlayerManager.isDownedPlayer(player))
			downedPlayerManager.removeDownedPlayer(player);
		// Note: no resetPlayer here — the caller (endGame) decides whether the player
		// goes home immediately or is held in post-match spectator mode.
	}

	public void removeSpectator(Player player)
	{
		if(gamePlayers.containsKey(player) && gamePlayers.get(player).isSpectating())
		{
			gamePlayers.remove(player);
			CachedPlayerInfo.restorePlayerInfo(player);
			scoreboard.removePlayer(player);
			GameManager.INSTANCE.refreshVisibility();
		}
	}

	private void resetPlayer(Player player)
	{
		// Leaving (or being sent home): a game-over cutscene still playing for them stops at once
		com.theprogrammingturkey.comz.util.GameOverMessages.stop(player);
		for(PotionEffectType t : Registry.POTION_EFFECT_TYPE)
			player.removePotionEffect(t);

		player.removePotionEffect(PotionEffectType.SPEED);
		// Only swap the game kit for their own things while those are still waiting: a second reset (the match ending
		// right after they left) must not wipe the inventory they already got back.
		if(CachedPlayerInfo.hasSavedInfo(player.getUniqueId()))
		{
			player.getInventory().clear();
			CachedPlayerInfo.restorePlayerInfo(player);
		}
		player.setInvulnerable(false);
		player.setHealth(20);
		player.setWalkSpeed(0.2F);
		scoreboard.removePlayer(player);
		player.updateInventory();
		COMZombies plugin = COMZombies.getPlugin();
		for(Player pl : Bukkit.getOnlinePlayers())
		{
			if(pl == player)
				continue;

			if(gamePlayers.containsKey(pl))
				pl.hidePlayer(plugin, player);
			else
				player.showPlayer(plugin, pl);
		}
		signManager.updateGame();
		com.theprogrammingturkey.comz.game.managers.DoorProximityManager.INSTANCE.clearPlayer(player);
	}

	/**
	 * Causes the game to always be at night time.
	 */
	public void forceNight()
	{
		arena.getWorld().setGameRule(ApiCompat.ADVANCE_TIME, false);
		arena.getWorld().setTime(18000L);
	}

	/**
	 * Sets the players warp location in game.
	 *
	 * @param loc location where the point will be set
	 */
	public void setPlayerTPLocation(Location loc)
	{
		arena.setPlayerTPLocation(loc);
		if(arena.isSetupComplete())
			GameManager.INSTANCE.saveAllGames();
	}

	/** Sets (or with null removes) the second game warp. */
	public void setSecondPlayerTPLocation(Location loc)
	{
		arena.setSecondPlayerTPLocation(loc);
		if(arena.isSetupComplete())
			GameManager.INSTANCE.saveAllGames();
	}

	/** Which game warp each player of this match was given (only with two warps). */
	private final Map<java.util.UUID, Integer> warpSide = new HashMap<>();

	/**
	 * The game warp of a player: with a second warp the players are split between the two when the match starts (a
	 * player joining later goes to the side with fewer players), otherwise everyone uses the one warp.
	 */
	public Location getPlayerWarp(Player player)
	{
		if(!arena.hasSecondPlayerTPLocation())
			return arena.getPlayerTPLocation();
		Integer side = warpSide.get(player.getUniqueId());
		if(side == null)
		{
			int one = 0, two = 0;
			for(Player p : getPlayersInGame())
			{
				Integer s = warpSide.get(p.getUniqueId());
				if(s != null && s == 2)
					two++;
				else if(s != null)
					one++;
			}
			side = two < one ? 2 : 1;
			warpSide.put(player.getUniqueId(), side);
		}
		return side == 2 ? arena.getSecondPlayerTPLocation() : arena.getPlayerTPLocation();
	}

	/** Splits the players between the two game warps (shuffled, alternating, so the sides differ by at most one). */
	private void assignWarps(List<Player> players)
	{
		warpSide.clear();
		if(!arena.hasSecondPlayerTPLocation())
			return;
		List<Player> order = new ArrayList<>(players);
		java.util.Collections.shuffle(order);
		for(int i = 0; i < order.size(); i++)
			warpSide.put(order.get(i).getUniqueId(), i % 2 == 0 ? 1 : 2);
	}

	/**
	 * Sets the spectator warp location
	 *
	 * @param loc location where the warp will be
	 */
	public void setSpectateLocation(Location loc)
	{
		arena.setSpectateLocation(loc);
		if(arena.isSetupComplete())
			GameManager.INSTANCE.saveAllGames();
	}

	/**
	 * Sets the lobby spawn location
	 *
	 * @param loc location where the spawn wll be
	 */
	public void setLobbySpawn(Location loc)
	{
		arena.setLobbyLocation(loc);
		if(arena.isSetupComplete())
			GameManager.INSTANCE.saveAllGames();
	}

	/**
	 * Removes the lobby spawn location. Players will then wait at the game warp.
	 */
	public void removeLobbySpawn()
	{
		arena.setLobbyLocation(null);
		if(arena.isSetupComplete())
			GameManager.INSTANCE.saveAllGames();
	}

	/**
	 * Sets the first point in the arena
	 *
	 * @param loc location that
	 */
	public void addPointOne(Location loc)
	{
		arena.setMin(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
		arena.setWorld(loc.getWorld());
		if(arena.isSetupComplete())
			GameManager.INSTANCE.saveAllGames();
	}

	/**
	 * Sets the Second point in the arena
	 *
	 * @param loc location that
	 * @return if the point was set or not
	 */
	public boolean addPointTwo(Location loc)
	{
		if(!arena.hasMin())
			return false;

		arena.setMax(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
		if(arena.isSetupComplete())
			GameManager.INSTANCE.saveAllGames();
		return true;
	}

	/**
	 * Disables the game
	 */
	public void setDisabled()
	{
		if(isTemplate())
			for(Game copy : GameManager.INSTANCE.getGames())
				if(copy.isInstance() && copy.getTemplateName().equals(getName())) copy.setDisabled();
		endGame();
		status = GameStatus.DISABLED;
	}

	/**
	 * Enables the game
	 */
	public void setEnabled()
	{
		if(isTemplate())
			for(Game copy : GameManager.INSTANCE.getGames())
				if(copy.isInstance() && copy.getTemplateName().equals(getName())) copy.setEnabled();
		resetSpawnLocationBlocks();
		if(status == GameStatus.INGAME)
			return;

		status = GameStatus.WAITING;
		signManager.updateGame();
	}

	/**
	 * Ends the game
	 */
	public void endGame()
	{
		if(this.status == GameStatus.WAITING && gamePlayers.isEmpty())
			return;
		starter.endTimer();
		starter = new AutoStart(this, 60);
		matchGeneration++;
		powerUpManager.clear();
		stopAmbientMusic();

		// A match can end (admin command, reload, remove) while post-match spectators are
		// still lingering; cancel their pending return so nothing runs against a fresh state.
		if(gameOverReturnTask != -1)
		{
			Bukkit.getScheduler().cancelTask(gameOverReturnTask);
			gameOverReturnTask = -1;
		}

		// Only a match that was actually in progress gets the game-over treatment (sound +
		// post-match spectate); admin/reload/remove ends just send players home.
		boolean realGameOver = this.status == GameStatus.INGAME;
		if(realGameOver)
			playRoundMusic("game_over");

		// The final round is the same for everyone and is reset below.
		finalRoundReached = waveNumber;

		this.status = GameStatus.WAITING;

		// Snapshot per-match stats (including each player's live points) BEFORE the loop
		// below calls playerLeaveGame, which clears those totals.
		final Map<UUID, PlayerMatchStats> gameOverBoard =
				realGameOver ? buildGameOverStats() : null;

		// After a real match ends, players still in the arena (playing, downed, or viewing)
		// linger in spectator mode so they hear the ending; everyone else goes home as before.
		List<Player> keepSpectating = new ArrayList<>();
		for(GamePlayer v : gamePlayers.values())
		{
			Player player = v.getPlayer();
			boolean linger = realGameOver && !v.hasLeftGame() && player.isOnline()
					&& (v.isInGame() || v.isSpectating() || v.isDead());
			if(v.isInGame() || v.hasLeftGame() || v.isDead())
			{
				// Record their best (round, points) before their points are cleared: the other way round every match
				// recorded the starting 500, so the points record never moved
				removePlayerActions(player);
				PointManager.INSTANCE.playerLeaveGame(player);
				com.theprogrammingturkey.comz.game.managers.DoorProximityManager.INSTANCE.clearPlayer(player);
			}
			if(linger)
			{
				keepSpectating.add(player);
				// Players with the tactical mod get a cutscene where they went down (still there if they were the last one
				// or were downed; the others died earlier and were moved to the spectate spot)
				Location downAt = v.isDead() ? deathSpots.get(player.getUniqueId()) : player.getLocation();
				if(downAt != null && downAt.getWorld() == player.getWorld())
					com.theprogrammingturkey.comz.util.GameOverMessages.send(player, downAt, finalRoundReached);
			}
			else if(!v.hasLeftGame())
				resetPlayer(player); // players who left were already sent home (with their things) by removePlayer
		}

		deathSpots.clear();
		spawnManager.killAll(false);
		spawnManager.reset();
		for(Door door : doorManager.getDoors())
			door.closeDoor();

		boxManager.resetBoxes();
		perkManager.clearPerks();
		downedPlayerManager.clearDownedPlayers();
		turnOffPower();
		TeleporterLinks.reset(this);
		isFireSale = false;
		boxManager.loadAllBoxes();
		barrierManager.unloadAllBarriers();
		gamePlayers.clear();
		leftAt.clear();
		scoreboard = new GameScoreboard(this);
		instaKill = false;
		doublePoints = false;
		isFireSale = false;
		waveNumber = 0;
		changingRound = false;
		clearArena();
		clearArenaItems();
		PointManager.INSTANCE.clearGamePoints(this);

		if(!keepSpectating.isEmpty())
			// Hold the players in the arena to hear the ending and see the leaderboard;
			// the solo instance (if any) is torn down once they return home.
			beginGameOverSpectate(keepSpectating, gameOverBoard);
		else if(soloInstance)
			// No lingering spectators: dispose the instance now that the world will empty.
			ArenaInstances.INSTANCE.onSoloMatchEnded(this);
		GameManager.INSTANCE.refreshVisibility();

		signManager.updateGame();
	}

	/**
	 * Keeps the players who just finished a match in spectator mode at the arena's spectate
	 * spot so they hear the game-over stinger and see the post-match leaderboard, then
	 * returns them to their pre-match location.
	 */
	private void beginGameOverSpectate(List<Player> players, Map<UUID, PlayerMatchStats> stats)
	{
		gameOverSpectators.clear();
		matchStats.clear();
		if(stats != null)
			matchStats.putAll(stats);
		for(Player player : players)
		{
			if(!player.isOnline())
				continue;
			// The pre-match home is already cached from when they joined; do NOT re-save here
			// or the arena location would become their restore point.
			player.setGameMode(GameMode.SPECTATOR);
			player.teleport(arena.getSpectateLocation());
			gameOverSpectators.add(player);
		}

		// Show the post-match leaderboard sidebar to everyone who is spectating.
		showGameOverStats();

		long delay = ConfigManager.getMainConfig().gameOverSpectateSeconds * 20L;
		if(delay <= 0 || gameOverSpectators.isEmpty())
		{
			returnGameOverSpectators();
			return;
		}
		gameOverReturnTask = COMZombies.scheduleTask(delay, this::returnGameOverSpectators);
	}

	/** Sends lingering game-over spectators back to their pre-match location. */
	private void returnGameOverSpectators()
	{
		gameOverReturnTask = -1;
		for(Player player : gameOverSpectators)
		{
			// Skip anyone who has since joined a new game; they are handled there.
			if(!player.isOnline() || GameManager.INSTANCE.isPlayerInGame(player))
				continue;
			resetPlayer(player);
			if(gamePlayers.containsKey(player))
				gamePlayers.remove(player);
			// The post-match board goes away with them (as it does for /z l)
			player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
		}
		gameOverSpectators.clear();
		matchStats.clear();
		GameManager.INSTANCE.refreshVisibility();
		if(soloInstance)
			// The instance world empties once the player is home; dispose it then.
			ArenaInstances.INSTANCE.onSoloMatchEnded(this);
	}

	/** True while players are still watching the post-match board; the solo world must not be unloaded then. */
	public boolean hasGameOverSpectators()
	{
		return !gameOverSpectators.isEmpty();
	}

	/** True if the player is currently watching the post-match board (not counted as "in a game"). */
	public boolean isGameOverSpectator(Player player)
	{
		return gameOverSpectators.contains(player);
	}

	/** Sends one lingering post-match spectator home immediately, e.g. via {@code /z l}. */
	/**
	 * Stops a player watching this game's ending because they are joining another match right away: unlike
	 * skipGameOverSpectate they are NOT sent home (their saved home and things stay saved for when that match is over).
	 * Without this they were in two games at once: still held in this one's world while playing the other.
	 */
	public void releaseGameOverSpectator(Player player)
	{
		if(!gameOverSpectators.remove(player))
			return;
		com.theprogrammingturkey.comz.util.GameOverMessages.stop(player);
		gamePlayers.remove(player);
		player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
		if(gameOverSpectators.isEmpty() && gameOverReturnTask != -1)
		{
			Bukkit.getScheduler().cancelTask(gameOverReturnTask);
			gameOverReturnTask = -1;
			matchStats.clear();
			if(soloInstance)
				ArenaInstances.INSTANCE.onSoloMatchEnded(this);
		}
	}

	public void skipGameOverSpectate(Player player)
	{
		if(!gameOverSpectators.remove(player))
			return;
		resetPlayer(player);
		if(gamePlayers.containsKey(player))
			gamePlayers.remove(player);
		// Clear their board now that they are leaving; other spectators keep theirs.
		player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
		GameManager.INSTANCE.refreshVisibility();
		// No one left: finish up (solo instance teardown, etc.) without waiting out the timer.
		if(gameOverSpectators.isEmpty() && gameOverReturnTask != -1)
		{
			Bukkit.getScheduler().cancelTask(gameOverReturnTask);
			gameOverReturnTask = -1;
			matchStats.clear();
			if(soloInstance)
				ArenaInstances.INSTANCE.onSoloMatchEnded(this);
		}
	}

	/** Captures each remaining player's per-match stats before endGame clears them. */
	private Map<UUID, PlayerMatchStats> buildGameOverStats()
	{
		Map<UUID, PlayerMatchStats> snapshot = new HashMap<>();
		for(GamePlayer v : gamePlayers.values())
		{
			if(v.hasLeftGame())
				continue;
			Player player = v.getPlayer();
			PlayerMatchStats live = matchStats.get(player.getUniqueId());
			int kills = live != null ? live.getKills() : 0;
			int revives = live != null ? live.getRevives() : 0;
			int deaths = live != null ? live.getDeaths() : 0;
			snapshot.put(player.getUniqueId(),
					new PlayerMatchStats(player.getName(),
							PointManager.INSTANCE.getPlayersPoints(player),
							kills, revives, deaths));
		}
		return snapshot;
	}

	/**
	 * Shows the per-match leaderboard (sorted by kills, then points) as a sidebar to the
	 * lingering spectators, replacing whatever they were viewing.
	 */
	private void showGameOverStats()
	{
		if(matchStats.isEmpty() || gameOverSpectators.isEmpty())
			return;

		List<PlayerMatchStats> ranked = new ArrayList<>(matchStats.values());
		ranked.sort(Comparator
				.comparingInt(PlayerMatchStats::getKills).reversed()
				.thenComparing(PlayerMatchStats::getPoints, Comparator.reverseOrder()));

		Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
		Objective objective = board.registerNewObjective(
				"mz_game_over", Criteria.DUMMY, Colors.legacy(Colors.RED + "Game Over"));
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);

		// Sidebar shows the highest score at the top: the round header sits above the
		// players, and rank 1 gets the highest player score.
		objective.getScore(Colors.RED + "Round " + finalRoundReached).setScore(ranked.size() + 2);
		int score = ranked.size();
		for(PlayerMatchStats stats : ranked)
		{
			String line = Colors.WHITE + stats.getName()
					+ Colors.GRAY + "  " + Colors.RED + stats.getPoints() + " pts"
					+ Colors.GRAY + "  " + Colors.GREEN + stats.getKills() + " kills"
					+ Colors.GRAY + "  " + Colors.YELLOW + stats.getRevives() + " rev"
					+ Colors.GRAY + "  " + Colors.RED + stats.getDeaths() + " deaths";
			objective.getScore(line).setScore(score--);
		}

		for(Player player : gameOverSpectators)
			if(player.isOnline())
				player.setScoreboard(board);
	}

	/** Adds a per-match kill to {@code player}'s leaderboard entry. */
	public void recordKill(Player player)
	{
		PlayerMatchStats stats = matchStats
				.computeIfAbsent(player.getUniqueId(),
						u -> new PlayerMatchStats(player.getName(), 0, 0, 0, 0));
		stats.incKills();
	}

	/** Adds a per-match death to {@code player}'s leaderboard entry. */
	public void recordDeath(Player player)
	{
		PlayerMatchStats stats = matchStats
				.computeIfAbsent(player.getUniqueId(),
						u -> new PlayerMatchStats(player.getName(), 0, 0, 0, 0));
		stats.incDeaths();
	}

	/** Adds a per-match revive to {@code player}'s leaderboard entry. */
	public void recordRevive(Player player)
	{
		PlayerMatchStats stats = matchStats
				.computeIfAbsent(player.getUniqueId(),
						u -> new PlayerMatchStats(player.getName(), 0, 0, 0, 0));
		stats.incRevives();
	}

	public enum GameStatus
	{
		DISABLED, STARTING, WAITING, INGAME
	}

	/**
	 * Sets up the arena when the server loads
	 */
	public boolean loadGame(JsonElement arenaJsonElem)
	{
		if(!arenaJsonElem.isJsonObject())
			return false;
		JsonObject arenaJson = arenaJsonElem.getAsJsonObject();
		JsonObject arenaSaveJson = arenaJson.get("save_data").getAsJsonObject();
		JsonObject arenaSettingsJson = arenaJson.get("settings").getAsJsonObject();

		String worldName = CustomConfig.getString(arenaSaveJson, "world_name", "Undefined");
		World world = Bukkit.getServer().getWorld(worldName);

		if(world == null)
		{
			COMZombies.log.log(Level.SEVERE, "{0} isn't a valid world name for the arena {1}", new Object[]{worldName, arena.getName()});
			return false;
		}

		powerSetup = CustomConfig.getBoolean(arenaSaveJson, "power_setup", false);
		instanceCount = Math.max(0, Math.min(16, CustomConfig.getInt(arenaSettingsJson, "instance_count", 0)));
		templateMode = instanceCount > 0;
		minPlayers = CustomConfig.getInt(arenaSettingsJson, "min_players", 1);
		maxPlayers = CustomConfig.getInt(arenaSettingsJson, "max_players", 8);
		// (replaces the old "teddy_bear_chance", a 1-in-N chance that arenas had at 0, i.e. never)
		teddyBearPercent = Math.max(0, Math.min(100, CustomConfig.getInt(arenaSettingsJson, "teddy_bear_percent", 15)));
		teddyBearSafeUses = Math.max(0, CustomConfig.getInt(arenaSettingsJson, "teddy_bear_safe_uses", 4));
		startingGun = CustomConfig.getString(arenaSettingsJson, "StartingGun", DEFAULT_STARTING_GUN);
		dogRoundEveryX = CustomConfig.getInt(arenaSettingsJson, "dog_round_every_x", 5);
		maxAmmoReplishClip = CustomConfig.getBoolean(arenaSettingsJson, "max_ammo_replenish_clip", false);

		forceNight = CustomConfig.getBoolean(arenaSettingsJson, "force_night", false);
		arena.loadArena(arenaSaveJson, world);
		if(forceNight && !isTemplate())
			forceNight();
		status = GameStatus.WAITING;

		if(arenaSettingsJson.has("powerup_settings"))
			powerUpManager.loadAllPowerUps(arenaSettingsJson.get("powerup_settings").getAsJsonObject());

		if(arenaSaveJson.has("zombie_spawns"))
			spawnManager.loadAllSpawnsToGame(arenaSaveJson.get("zombie_spawns").getAsJsonArray());

		if(arenaSaveJson.has("mystery_boxes"))
			boxManager.loadAllBoxesToGame(arenaSaveJson.get("mystery_boxes").getAsJsonArray(), arenaSettingsJson);

		if(arenaSaveJson.has("barriers"))
			barrierManager.loadAllBarriersToGame(arenaSaveJson.get("barriers").getAsJsonArray());

		if(arenaSaveJson.has("doors"))
			doorManager.loadAllDoorsToGame(arenaSaveJson.get("doors").getAsJsonArray());

		if(arenaSaveJson.has("teleporters"))
			teleporterManager.loadAllTeleportersToGame(arenaSaveJson.get("teleporters").getAsJsonArray());

		if(arenaSaveJson.has("link_regions") && arenaSaveJson.get("link_regions").isJsonObject())
			linkManager.load(arenaSaveJson.getAsJsonObject("link_regions"));

		signManager.updateGame();

		return true;
	}

	public JsonObject saveGame()
	{
		JsonObject gamejson = new JsonObject();
		JsonObject arenaSaveJson = new JsonObject();
		gamejson.add("save_data", arenaSaveJson);
		JsonObject arenaSettingsJson = new JsonObject();
		gamejson.add("settings", arenaSettingsJson);

		arenaSettingsJson.addProperty("min_players", minPlayers);
		arenaSettingsJson.addProperty("instance_count", instanceCount);
		arenaSettingsJson.addProperty("max_players", maxPlayers);
		arenaSettingsJson.remove("teddy_bear_chance");
		arenaSettingsJson.addProperty("teddy_bear_percent", teddyBearPercent);
		arenaSettingsJson.addProperty("teddy_bear_safe_uses", teddyBearSafeUses);
		arenaSettingsJson.addProperty("StartingGun", startingGun);
		arenaSettingsJson.addProperty("dog_round_every_x", dogRoundEveryX);
		arenaSettingsJson.addProperty("max_ammo_replenish_clip", maxAmmoReplishClip);

		arena.saveArena(arenaSaveJson);
		arenaSaveJson.addProperty("power_setup", powerSetup);
		arenaSettingsJson.addProperty("force_night", forceNight);

		arenaSettingsJson.add("powerup_settings", powerUpManager.save());
		arenaSaveJson.add("zombie_spawns", spawnManager.save());
		arenaSettingsJson.addProperty("multiple_mystery_boxes", boxManager.isMultiBox());
		arenaSaveJson.add("mystery_boxes", boxManager.save());
		arenaSaveJson.add("barriers", barrierManager.save());
		arenaSaveJson.add("doors", doorManager.save());
		arenaSaveJson.add("teleporters", teleporterManager.save());
		arenaSaveJson.add("link_regions", linkManager.save());

		return gamejson;
	}

	/**
	 * Creates an unbreakable ItemStack from the passed parameters
	 *
	 * @param material of the ItemStack to make
	 * @return the created stack
	 */
	private ItemStack getUnbreakableItem(Material material)
	{
		ItemStack stack = new ItemStack(material, 1);
		ItemMeta itemMeta = stack.getItemMeta();
		if(itemMeta == null)
			return stack;
		itemMeta.setUnbreakable(true);
		stack.setItemMeta(itemMeta);
		return stack;
	}

	/**
	 * Resolves a configured starting-armor material name to a Material.
	 *
	 * @param materialName the configured material name ("none" for an empty slot)
	 * @param slotName     a human-readable slot name for log messages
	 * @return the Material, or null to leave the slot empty
	 */
	private Material getStartingArmorMaterial(String materialName, String slotName)
	{
		if(materialName == null || materialName.equalsIgnoreCase("none"))
			return null;
		Material material = Material.matchMaterial(materialName);
		if(material == null)
		{
			COMZombies.log.log(Level.WARNING, "Unknown starting armor material '{0}' for {1}; leaving slot empty", new Object[]{materialName, slotName});
			return null;
		}
		return material;
	}

	/**
	 * Sets up the players inventory in game
	 *
	 * @param slot of the item
	 * @param item to be set up
	 * @return the item after set up
	 */
	private ItemStack setItemMeta(int slot, ItemStack item)
	{
		ItemMeta data = item.getItemMeta();
		if(data == null)
			return item;

		List<Component> lore = new ArrayList<>();
		switch(slot)
		{
			case SLOT_KNIFE_MARKER ->
			{
				data.displayName(Colors.legacy("Knife slot"));
				lore.add(Colors.legacy("Holds the player's knife"));
				lore.add(Colors.legacy("Knife only works within 2 blocks!"));
			}
			case SLOT_GUN_1 ->
			{
				data.displayName(Colors.legacy("Gun Slot 1"));
				lore.add(Colors.legacy("Holds 1 Gun"));
			}
			case SLOT_GUN_2 ->
			{
				data.displayName(Colors.legacy("Gun Slot 2"));
				lore.add(Colors.legacy("Holds 1 Gun"));
			}
			case SLOT_GUN_3 ->
			{
				data.displayName(Colors.legacy("Gun Slot 3"));
				lore.add(Colors.legacy("Holds 1 Gun"));
				lore.add(Colors.legacy("Requires Mule Kick to work!"));
			}
			case SLOT_PERK_1 ->
			{
				data.displayName(Colors.legacy("Perk Slot 1"));
				lore.add(Colors.legacy("Holds 1 Perk"));
			}
			case SLOT_PERK_2 ->
			{
				data.displayName(Colors.legacy("Perk Slot 2"));
				lore.add(Colors.legacy("Holds 1 Perk"));
			}
			case SLOT_PERK_3 ->
			{
				data.displayName(Colors.legacy("Perk Slot 3"));
				lore.add(Colors.legacy("Holds 1 Perk"));
			}
			case SLOT_PERK_4 ->
			{
				data.displayName(Colors.legacy("Perk Slot 4"));
				lore.add(Colors.legacy("Holds 1 Perk"));
			}
			case SLOT_GRENADE ->
			{
				data.displayName(Colors.legacy("Grenade Slot"));
				lore.add(Component.empty());
			}
		}
		data.lore(lore);
		item.setItemMeta(data);
		return item;
	}

	public void assignPlayerInventory(Player player)
	{
		player.getInventory().clear();
		ItemStack knife = getUnbreakableItem(Material.END_ROD);
		ItemMeta kMeta = knife.getItemMeta();
		if(kMeta != null)
			kMeta.displayName(Colors.legacy(Colors.RED + "Knife"));
		knife.setItemMeta(kMeta);
		ItemStack ib = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
		// Optional starting armor; on/off via config.gameSettings.startingArmor, materials via startingArmorMaterials.
		if(ConfigManager.getMainConfig().startingArmor)
		{
			ConfigSetup main = ConfigManager.getMainConfig();
			Material helmet = getStartingArmorMaterial(main.startingArmorHelmet, "helmet");
			if(helmet != null)
				player.getInventory().setHelmet(getUnbreakableItem(helmet));
			Material chestplate = getStartingArmorMaterial(main.startingArmorChestplate, "chestplate");
			if(chestplate != null)
				player.getInventory().setChestplate(getUnbreakableItem(chestplate));
			Material leggings = getStartingArmorMaterial(main.startingArmorLeggings, "leggings");
			if(leggings != null)
				player.getInventory().setLeggings(getUnbreakableItem(leggings));
			Material boots = getStartingArmorMaterial(main.startingArmorBoots, "boots");
			if(boots != null)
				player.getInventory().setBoots(getUnbreakableItem(boots));
		}
		player.getInventory().setItem(SLOT_KNIFE, knife);
		//player.getInventory().setItem(8, new ItemStack(Material.MAGMA_CREAM, 4));
		player.getInventory().setItem(SLOT_KNIFE_MARKER, setItemMeta(SLOT_KNIFE_MARKER, ib));
		player.getInventory().setItem(SLOT_GUN_1, setItemMeta(SLOT_GUN_1, ib));
		player.getInventory().setItem(SLOT_GUN_2, setItemMeta(SLOT_GUN_2, ib));
		player.getInventory().setItem(SLOT_GUN_3, setItemMeta(SLOT_GUN_3, ib));
		player.getInventory().setItem(SLOT_PERK_1, setItemMeta(SLOT_PERK_1, ib));
		player.getInventory().setItem(SLOT_PERK_2, setItemMeta(SLOT_PERK_2, ib));
		player.getInventory().setItem(SLOT_PERK_3, setItemMeta(SLOT_PERK_3, ib));
		player.getInventory().setItem(SLOT_PERK_4, setItemMeta(SLOT_PERK_4, ib));
		player.getInventory().setItem(SLOT_GRENADE, setItemMeta(SLOT_GRENADE, ib));
		player.updateInventory();
	}

	public static final List<Class<? extends Entity>> BLACKLISTED_ENTITIES = Arrays.asList(Player.class, Minecart.class, Painting.class, ItemFrame.class);

	/**
	 * Clears the arena
	 */
	public void clearArena()
	{
		if(arena.getWorld() == null)
			return;
		for(Entity entity : arena.getWorld().getEntities())
		{
			if(BLACKLISTED_ENTITIES.contains(entity.getClass()) && arena.containsBlock(entity.getLocation()))
			{
				entity.setTicksLived(Integer.MAX_VALUE);
				entity.remove();
			}
		}
	}

	/**
	 * Clears items out of the arena
	 */
	public void clearArenaItems()
	{
		if(arena.getWorld() == null)
			return;
		List<Entity> entList = arena.getWorld().getEntities();// get all entities in the world

		for(Entity current : entList)
		{
			// loop through the list
			// make sure we are only deleting what we want to delete
			if(current instanceof Item)
				current.remove();
			if(current instanceof Zombie)
				current.remove();
		}
	}

	public void setFireSale(boolean b)
	{
		isFireSale = b;
	}

	public boolean isFireSale()
	{
		return isFireSale;
	}

	public void damageMob(Mob mob, Player player, float damageAmount)
	{
		damageMob(mob, player, damageAmount, false);
	}

	/** A zombie's death: the headshot sound when it died to a headshot, otherwise the death sound (both from the pack). */
	private void playZombieDeath(Mob mob, boolean headshot, Player killer)
	{
		// Read by TacticalMovementPlugin when the zombie is removed (right after this), to play the matching BetterModel
		// death animation for players without the client mod: "headshot" or "body", and who shot it (which way it falls).
		org.bukkit.plugin.Plugin plugin = COMZombies.getPlugin();
		mob.setMetadata("mz_killed", new org.bukkit.metadata.FixedMetadataValue(plugin, headshot ? "headshot" : "body"));
		if(killer != null)
			mob.setMetadata("mz_killer", new org.bukkit.metadata.FixedMetadataValue(plugin, killer.getUniqueId().toString()));
		mob.getWorld().playSound(mob.getLocation(), headshot ? "mz:zombie_headshot" : "mz:zombie_death", org.bukkit.SoundCategory.HOSTILE, 1f, 1f);
	}

	public void damageMob(Mob mob, Player player, float damageAmount, boolean headshot)
	{
		damageMob(mob, player, damageAmount, headshot, false);
	}

	/** Points for the killing blow: knife, headshot or body (COD values by default: 130 / 100 / 60). */
	private int killPoints(boolean headshot, boolean knife)
	{
		ConfigSetup c = ConfigManager.getMainConfig();
		return knife ? c.pointsOnKnifeKill : headshot ? c.pointsOnHeadshotKill : c.pointsOnKill;
	}

	public void damageMob(Mob mob, Player player, float damageAmount, boolean headshot, boolean knife)
	{
		double mobHealth = mob.getHealth() - damageAmount;
		// Blood for players with the tactical mod (health is set directly here, so Minecraft sends no hurt flash for it)
		com.theprogrammingturkey.comz.util.BloodMessages.hit(mob, isInstaKill() || mobHealth < 1);
		// Each branch below plays the appropriate hurt/death sound for the mob type.

		if(isInstaKill())
		{
			if(mob instanceof Zombie)
				playZombieDeath(mob, headshot, player);
			else
				player.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

			powerUpManager.powerUpDrop(mob, player);
			mob.remove();
			PointManager.INSTANCE.addPoints(player, killPoints(headshot, knife) * (isDoublePoints() ? 2 : 1));

			PointManager.INSTANCE.notifyPlayer(player);
			spawnManager.removeEntity(mob);

			if(mob instanceof Zombie)
				zombieKilled(player);

			if(spawnManager.getEntities().isEmpty() && spawnManager.getMobsSpawned() == spawnManager.getMobsToSpawn())
			{
				if(mob instanceof Wolf)
					powerUpManager.dropPowerUp(mob, PowerUp.MAX_AMMO);
				nextWave();
			}
		}
		else if(mobHealth < 1)
		{
			if(mob instanceof Zombie)
				playZombieDeath(mob, headshot, player);
			else
				player.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

			powerUpManager.powerUpDrop(mob, player);
			mob.remove();
			PointManager.INSTANCE.addPoints(player, killPoints(headshot, knife) * (isDoublePoints() ? 2 : 1));

			PointManager.INSTANCE.notifyPlayer(player);
			spawnManager.removeEntity(mob);

			if(mob instanceof Zombie)
				zombieKilled(player);

			if(spawnManager.getEntities().isEmpty() && spawnManager.getMobsSpawned() == spawnManager.getMobsToSpawn())
			{
				if(mob instanceof Wolf)
					powerUpManager.dropPowerUp(mob, PowerUp.MAX_AMMO);
				nextWave();
			}
		}
		else
		{
			mob.setHealth(mobHealth);
			if(mob instanceof Zombie)
				player.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ZOMBIE_HURT, 1f, 1f);
			else
				player.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_HURT, 1f, 1f);

			if(isDoublePoints())
				PointManager.INSTANCE.addPoints(player, ConfigManager.getMainConfig().pointsOnHit * 2);
			else
				PointManager.INSTANCE.addPoints(player, ConfigManager.getMainConfig().pointsOnHit);
			PointManager.INSTANCE.notifyPlayer(player);
		}

		if(debugMode)
		{
			mob.customName(Component.text(String.valueOf(mobHealth)));
			mob.setCustomNameVisible(true);
		}
		else
		{
			mob.setCustomNameVisible(false);
		}
	}

	public float damagePlayer(Player player, float damageAmount)
	{
		if(player.getHealth() - damageAmount < 1)
		{
			playerDowned(player);
			return 0;
		}

		return damageAmount;
	}

	private void playerDowned(Player player)
	{
		if(downedPlayerManager.isDownedPlayer(player))
			return;

		player.setFireTicks(0);

		// The last player down (whether a solo match or a full one) ends the match; the
		// post-match spectate in endGame keeps them in the arena to hear the ending.
		// Only players still in the fight count: teammates who already died do not keep the match alive.
		long active = gamePlayers.values().stream().filter(GamePlayer::isInGame).count();
		if(downedPlayerManager.numDownedPlayers() + 1 >= active)
		{
			// The downed timer never runs for the final player (the match ends first), so
			// record their death here or it would be missing from the leaderboard.
			recordDeath(player);
			endGame();
		}
		else
			downedPlayerManager.setPlayerDowned(player, this);
	}

	public int getTeddyBearPercent()
	{
		return teddyBearPercent;
	}

	public int getTeddyBearSafeUses()
	{
		return teddyBearSafeUses;
	}

	public int getDogRoundEveryX()
	{
		return dogRoundEveryX;
	}

	public boolean doesMaxAmmoReplenishClip()
	{
		return maxAmmoReplishClip;
	}

	public String getStartingGun()
	{
		return startingGun;
	}

	public void zombieKilled(Player player)
	{
		Leaderboard.getPlayerStatFromPlayer(player).incKills();
		recordKill(player);

		if(COMZombies.getPlugin().vault != null)
		{
			try
			{
				COMZombies.getPlugin().vault.addMoney(player, ConfigManager.getMainConfig().KillMoney);
			} catch(NullPointerException e)
			{
				COMZombies.log.log(Level.WARNING, "Vault addMoney failed for {0} while awarding kill money", new Object[]{player.getName()});
			}
		}
	}

	public void updateBarrierDamage(int damage, Collection<Block> blocks)
	{
		// Cracks only go to players in the game, but clearing them goes to everyone in the world. When a game ends,
		// the players have usually already left it, so they would otherwise never be told the cracks are gone.
		List<Player> players = damage < 0 ? arena.getWorld().getPlayers() : getPlayersAndSpectators();
		for(Block block : blocks)
			COMZombies.nmsUtil.playBlockBreakAction(players, damage, block);
	}

	public boolean isPlayerPlaying(Player player)
	{
		return getPlayersInGame().contains(player);
	}

	public boolean isPlayerExited(Player player)
	{
		return gamePlayers.containsKey(player) && gamePlayers.get(player).hasLeftGame();
	}

	public boolean isPlayerDeath(Player player)
	{
		return gamePlayers.containsKey(player) && gamePlayers.get(player).isDead();
	}

	public boolean isPlayerSpectating(Player player)
	{
		return gamePlayers.containsKey(player) && gamePlayers.get(player).isSpectating();
	}

	public List<Player> getPlayersAndSpectators()
	{
		return gamePlayers.values().stream()
				.filter(v -> v.isInGame() || v.isSpectating())
				.map(v -> v.getPlayer())
				.collect(Collectors.toList());
	}

	public List<Player> getDeathPlayers()
	{
		return gamePlayers.values().stream()
				.filter(v -> v.isDead())
				.map(v -> v.getPlayer())
				.collect(Collectors.toList());
	}

	public void sendMessageToPlayers(String message)
	{
		for(Player player : getPlayersInGame())
			player.sendRawMessage(COMZombies.PREFIX + message);
	}

	public boolean gameSetupComplete(Player player)
	{
		if(!arena.areMinAndMaxSet())
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Either P1 or P2 or both are not set!");
			return false;
		}

		if(!arena.areAllLocationsSet())
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "One or multiple of the game warps (gw, sw) are not set! (lw is optional)");
			return false;
		}

		status = GameStatus.DISABLED;
		maxPlayers = 8;
		GameManager.INSTANCE.saveAllGames();
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Arena [" + arena.getName() + "] is setup!");
		return true;
	}

	public void setDebugMode(boolean debugMode)
	{
		this.debugMode = debugMode;
	}

	public boolean getDebugMode()
	{
		return this.debugMode;
	}

	public World getWorld()
	{
		return this.arena.getWorld();
	}

	public String getName()
	{
		return arena.getName();
	}
}
