package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.managers.LinkManager;
import com.theprogrammingturkey.comz.game.signs.SignRegistry;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Der Riese style teleporter linking. A "pad" is either a teleporter sign with "pad" on line 2, or a link region made with
 * /z linkadd. A pad starts unlinked: activating it (standing in the region, or using the sign; the power has to be on when
 * the arena uses power) gives config.gameSettings.teleporterLinkTime seconds to reach the mainframe (the region made with
 * /z mainlink, or a mainframe sign) to link it. Once every pad is linked, doors made with LINK open (the way to
 * Pack-a-Punch). Linked pads then teleport. Everything resets when the game ends.
 */
public final class TeleporterLinks
{
	private static final class State
	{
		final Set<String> linked = new HashSet<>();
		String pending;
		long pendingUntil;
		int pendingTask = -1;
		int timerTask = -1;
		boolean doorsOpened;
	}

	private static final Map<Game, State> STATES = new HashMap<>();
	/** The link region each player stood in last tick, so entering one is only acted on once. */
	private static final Map<UUID, String> LAST_REGION = new HashMap<>();
	private static final Map<String, Long> REGION_COOLDOWNS = new HashMap<>();

	private TeleporterLinks()
	{
	}

	private static State state(Game game)
	{
		return STATES.computeIfAbsent(game, g -> new State());
	}

	/** The key of a pad sign. */
	public static String key(Location loc)
	{
		return "sign:" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
	}

	/** The key of a link region. */
	public static String regionKey(String name)
	{
		return "link:" + name.toLowerCase();
	}

	/** Every pad of the game: pad signs and link regions. */
	public static List<String> pads(Game game)
	{
		List<String> pads = new ArrayList<>();
		for(Map.Entry<Location, SignRegistry.Entry> e : SignRegistry.INSTANCE.getAll().entrySet())
			if(e.getValue().type.equals("teleporter") && "true".equals(e.getValue().data.get("link"))
					&& GameManager.INSTANCE.getGame(e.getKey()) == game)
				pads.add(key(e.getKey()));
		for(LinkManager.LinkRegion r : game.linkManager.getLinks())
			pads.add(regionKey(r.name));
		return pads;
	}

	public static boolean isLinked(Game game, String key)
	{
		return state(game).linked.contains(key);
	}

	public static boolean isLinked(Game game, Location padSign)
	{
		return isLinked(game, key(padSign));
	}

	public static int linkedCount(Game game)
	{
		return state(game).linked.size();
	}

	/** The pad waiting to be linked at the mainframe, or null. */
	public static String pending(Game game)
	{
		State s = state(game);
		if(s.pending != null && System.currentTimeMillis() > s.pendingUntil)
			s.pending = null;
		return s.pending;
	}

	public static boolean isPending(Game game, String key)
	{
		return key.equals(pending(game));
	}

	public static boolean isPending(Game game, Location padSign)
	{
		return isPending(game, key(padSign));
	}

	public static int secondsLeft(Game game)
	{
		return (int) Math.max(0, (state(game).pendingUntil - System.currentTimeMillis() + 999) / 1000);
	}

	public static boolean allLinked(Game game)
	{
		List<String> pads = pads(game);
		return !pads.isEmpty() && state(game).linked.containsAll(pads);
	}

	public static void activate(Game game, Player player, Location padSign)
	{
		activate(game, player, key(padSign), padSign);
	}

	/** A player activated an unlinked pad: start the countdown to link it at the mainframe. */
	public static void activate(Game game, Player player, String key, Location soundAt)
	{
		State s = state(game);
		String current = pending(game);
		if(current != null)
		{
			send(player, current.equals(key) ? Colors.YELLOW + "This teleporter is waiting to be linked! Get to the mainframe (" + secondsLeft(game) + "s)."
					: Colors.RED + "Another teleporter is already waiting to be linked at the mainframe!");
			return;
		}
		int seconds = Math.max(5, COMZombies.getPlugin().getConfig().getInt("config.gameSettings.teleporterLinkTime", 30));
		s.pending = key;
		s.pendingUntil = System.currentTimeMillis() + seconds * 1000L;
		if(s.pendingTask != -1)
			Bukkit.getScheduler().cancelTask(s.pendingTask);
		s.pendingTask = COMZombies.scheduleTask(seconds * 20L, () ->
		{
			s.pendingTask = -1;
			if(key.equals(s.pending))
			{
				s.pending = null;
				broadcast(game, Colors.RED + "The teleporter was not linked in time! Activate it again.");
			}
		});
		startTimer(game, s);
		soundAt.getWorld().playSound(soundAt, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
		broadcast(game, Colors.AQUA + player.getName() + " activated a teleporter! " + Colors.YELLOW + "Link it at the mainframe within " + seconds + " seconds.");
	}

	/** Counts the link time down on the sidebar every second until the pad is linked or the time runs out. */
	private static void startTimer(Game game, State s)
	{
		stopTimer(game, s);
		s.timerTask = COMZombies.scheduleTask(0, 20, () ->
		{
			if(pending(game) == null || game.scoreboard == null)
			{
				stopTimer(game, s);
				return;
			}
			game.scoreboard.setLinkTimer(secondsLeft(game));
		});
	}

	private static void stopTimer(Game game, State s)
	{
		if(s.timerTask != -1)
		{
			Bukkit.getScheduler().cancelTask(s.timerTask);
			s.timerTask = -1;
		}
		if(game.scoreboard != null)
			game.scoreboard.setLinkTimer(-1);
	}

	/** The mainframe sign was used. */
	public static void useMainframe(Game game, Player player, Location mainframe)
	{
		if(pads(game).isEmpty())
		{
			send(player, Colors.RED + "This arena has no teleporter pads to link.");
			return;
		}
		if(allLinked(game))
		{
			send(player, Colors.GREEN + "Every teleporter is linked.");
			return;
		}
		if(pending(game) == null)
		{
			send(player, Colors.YELLOW + "Activate a teleporter first, then link it here.");
			return;
		}
		link(game, mainframe);
	}

	/** Links the pending pad. */
	private static void link(Game game, Location soundAt)
	{
		State s = state(game);
		String current = pending(game);
		if(current == null)
			return;
		s.pending = null;
		if(s.pendingTask != -1)
		{
			Bukkit.getScheduler().cancelTask(s.pendingTask);
			s.pendingTask = -1;
		}
		s.linked.add(current);
		stopTimer(game, s);
		List<String> pads = pads(game);
		soundAt.getWorld().playSound(soundAt, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f);
		int done = 0;
		for(String p : pads)
			if(s.linked.contains(p))
				done++;
		broadcast(game, Colors.GREEN + "Teleporter linked! (" + done + "/" + pads.size() + ")");
		if(done >= pads.size())
			finish(game);
	}

	private static void finish(Game game)
	{
		State s = state(game);
		if(s.doorsOpened)
			return;
		s.doorsOpened = true;
		int opened = 0;
		for(Door door : game.doorManager.getDoors())
			if(door.opensWhenLinked() && !door.isOpened())
			{
				door.openDoor();
				door.playerDoorOpenSound();
				opened++;
			}
		for(Player p : game.getPlayersInGame())
			p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
		broadcast(game, Colors.GOLD + "" + Colors.BOLD + "All teleporters linked!" + (opened > 0 ? Colors.GOLD + " The way to Pack-a-Punch is open." : ""));
	}

	// ------------------------------------------------------------------ link regions

	public static void start(Plugin plugin)
	{
		Bukkit.getScheduler().runTaskTimer(plugin, TeleporterLinks::tick, 40L, 10L);
	}

	private static void tick()
	{
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.getStatus() != Game.GameStatus.INGAME || game.linkManager.isEmpty() || !autoActivate())
				continue;
			for(Player player : game.getPlayersInGame())
			{
				Location at = player.getLocation();
				LinkManager.LinkRegion region = game.linkManager.linkAt(at);
				String now = region == null ? null : region.name.toLowerCase();
				String before = LAST_REGION.put(player.getUniqueId(), now);
				if(region != null && !now.equals(before))
					enteredLink(game, player, region);
				if(game.linkManager.inMainframe(at) && pending(game) != null && !allLinked(game))
					link(game, at);
			}
		}
	}

	/** config.gameSettings.linkAutoActivate: standing in a link / the mainframe is enough, no key press needed. */
	public static boolean autoActivate()
	{
		return COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.linkAutoActivate", false);
	}

	/** The interaction key in a link region: activates it when unlinked, teleports when linked. */
	public static void useLink(Game game, Player player, LinkManager.LinkRegion region)
	{
		if(isLinked(game, regionKey(region.name)))
			teleport(game, player, region);
		else
			enteredLink(game, player, region);
	}

	/** The interaction key in the mainframe region: links the teleporter that is waiting. */
	public static void useMainframeRegion(Game game, Player player)
	{
		if(pads(game).isEmpty())
		{
			send(player, Colors.RED + "This arena has no teleporter pads to link.");
			return;
		}
		if(allLinked(game))
		{
			send(player, Colors.GREEN + "Every teleporter is linked.");
			return;
		}
		if(pending(game) == null)
		{
			send(player, Colors.YELLOW + "Activate a teleporter first, then link it here.");
			return;
		}
		link(game, player.getLocation());
	}

	private static void enteredLink(Game game, Player player, LinkManager.LinkRegion region)
	{
		String key = regionKey(region.name);
		if(isLinked(game, key))
		{
			send(player, Colors.AQUA + "Teleporter linked. " + Colors.GRAY + "Press " + com.theprogrammingturkey.comz.game.managers.DoorProximityManager.INSTANCE.getKeyLabel()
					+ " to teleport to " + destinationName(game, region) + ".");
			return;
		}
		if(game.linkManager.getMainframe() == null)
		{
			send(player, Colors.RED + "This arena has no mainframe yet (/z mainlink).");
			return;
		}
		if(game.hasPower() && !game.isPowered())
		{
			send(player, Colors.RED + "This teleporter needs the power on!");
			PerkType.noPower(player);
			return;
		}
		activate(game, player, key, region.arrival(game.getWorld()));
	}

	/** Where a linked region sends players: the mainframe, or with config linksToLinks its chosen link. */
	public static LinkManager.LinkRegion destination(Game game, LinkManager.LinkRegion region)
	{
		boolean linksToLinks = COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.linksToLinks", false);
		if(linksToLinks && region.destination != null)
		{
			LinkManager.LinkRegion other = game.linkManager.getLink(region.destination);
			if(other != null)
				return other;
		}
		return game.linkManager.getMainframe();
	}

	public static String destinationName(Game game, LinkManager.LinkRegion region)
	{
		LinkManager.LinkRegion d = destination(game, region);
		return d == null ? "nowhere" : d.name.equals(LinkManager.MAINFRAME) ? "the mainframe" : d.name;
	}

	public static int teleportCost()
	{
		return COMZombies.getPlugin().getConfig().getInt("config.gameSettings.linkTeleportCost", 1500);
	}

	public static long cooldownLeft(Game game, LinkManager.LinkRegion region)
	{
		Long until = REGION_COOLDOWNS.get(game.getWorld().getName() + ":" + region.name.toLowerCase());
		return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
	}

	/** F inside a linked region: the player pays, and everyone in the game standing in the region is teleported. */
	public static void teleport(Game game, Player player, LinkManager.LinkRegion region)
	{
		if(!isLinked(game, regionKey(region.name)))
		{
			send(player, Colors.RED + "This teleporter is not linked to the mainframe yet!");
			return;
		}
		if(game.hasPower() && !game.isPowered())
		{
			send(player, Colors.RED + "You must turn on the power first!");
			return;
		}
		long wait = cooldownLeft(game, region);
		if(wait > 0)
		{
			send(player, Colors.RED + "The teleporter is cooling down (" + wait + "s).");
			return;
		}
		LinkManager.LinkRegion to = destination(game, region);
		if(to == null)
		{
			send(player, Colors.RED + "This teleporter has nowhere to go (no mainframe).");
			return;
		}
		int cost = teleportCost();
		if(!PointManager.INSTANCE.canBuy(player, cost))
		{
			send(player, Colors.RED + "You don't have enough points!");
			return;
		}
		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);

		int seconds = COMZombies.getPlugin().getConfig().getInt("config.gameSettings.teleporterCooldown", 0);
		if(seconds > 0)
			REGION_COOLDOWNS.put(game.getWorld().getName() + ":" + region.name.toLowerCase(), System.currentTimeMillis() + seconds * 1000L);

		Location arrival = to.arrival(game.getWorld());
		for(Player p : game.getPlayersInGame())
		{
			if(!region.contains(p.getLocation()))
				continue;
			Location from = p.getLocation();
			from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.5);
			from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
			p.teleport(arrival);
			// Arriving in a region must not activate it right away
			LAST_REGION.put(p.getUniqueId(), game.linkManager.linkAt(arrival) == null ? null : game.linkManager.linkAt(arrival).name.toLowerCase());
			p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 1));
			p.getWorld().playSound(arrival, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
		}
	}

	public static void reset(Game game)
	{
		State s = STATES.remove(game);
		if(s != null && s.pendingTask != -1)
			Bukkit.getScheduler().cancelTask(s.pendingTask);
		if(s != null)
			stopTimer(game, s);
		String prefix = game.getWorld() == null ? null : game.getWorld().getName() + ":";
		if(prefix != null)
			REGION_COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
	}

	private static void broadcast(Game game, String message)
	{
		for(Player p : game.getPlayersInGame())
			send(p, message);
	}

	private static void send(Player player, String message)
	{
		CommandUtil.sendMessageToPlayer(player, message);
	}
}
