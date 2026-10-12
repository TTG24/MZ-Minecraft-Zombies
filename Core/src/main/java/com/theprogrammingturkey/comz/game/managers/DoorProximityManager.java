package com.theprogrammingturkey.comz.game.managers;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.Barrier;
import org.bukkit.block.Block;
import com.theprogrammingturkey.comz.util.CommandUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Shows actionbar tooltips for nearby doors, barriers and machines using the swap-hand key (F).
 * to buy it. Complements the existing door sign — the sign still works, this just removes the need to
 * walk right up to the sign to click it.
 */
public class DoorProximityManager
{
	public static final DoorProximityManager INSTANCE = new DoorProximityManager();

	private int taskId = -1;

	// Nearest door each player is standing in range of
	private final Map<Player, Door> nearDoors = new HashMap<>();
	// The actionbar message last shown per player, used to clear it when leaving range
	private final Map<Player, String> lastActionbar = new HashMap<>();
	private record RepairSession(Barrier barrier, long nextRepairNanos) {}
	private final Map<Player, RepairSession> repairs = new HashMap<>();
	private record NearbyTarget(Door door, Barrier barrier, MachineProximityManager.Target machine, double distanceSquared) {}

	public String getKeyLabel()
	{
		return COMZombies.getPlugin().getConfig().getString("config.interaction.key",
				COMZombies.getPlugin().getConfig().getString("config.doorProximity.key", "F"));
	}

	private NearbyTarget findTarget(Player player)
	{
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME || game.isPlayerSpectating(player)
				|| !player.isValid() || player.isDead())
			return null;
		RepairSession session = repairs.get(player);
		if(session != null && canKeepRepairing(player, session.barrier()))
			return new NearbyTarget(null, session.barrier(), null, 0);
		Door door = getNearDoor(player);
		Barrier barrier = door == null ? getNearBarrier(player) : null;
		NearbyTarget target = null;
		if(door != null)
		{
			List<Location> anchors = new ArrayList<>(door.getSignsLocations());
			anchors.addAll(door.getBlockLocations());
			double distance = Double.POSITIVE_INFINITY;
			for(Location anchor : anchors)
				if(player.getWorld().equals(anchor.getWorld()))
					distance = Math.min(distance, anchor.distanceSquared(player.getLocation()));
			target = new NearbyTarget(door, null, null, distance);
		}
		else if(barrier != null)
			target = new NearbyTarget(null, barrier, null, barrierDistanceSquared(barrier, player));
		MachineProximityManager.Target machine = MachineProximityManager.nearest(game, player);
		if(machine != null && (target == null || machine.distanceSquared() < target.distanceSquared()))
			target = new NearbyTarget(null, null, machine, machine.distanceSquared());
		return target;
	}

	/** Re-evaluates the closest feature at keypress time and runs its existing interaction logic. */
	public boolean interactNearby(Player player)
	{
		if(stopRepairing(player))
			return true;
		NearbyTarget target = findTarget(player);
		if(target == null)
			return false;
		if(target.door() != null)
			buyDoor(target.door(), player);
		else if(target.barrier() != null)
			startRepairing(player, target.barrier());
		else
			MachineProximityManager.interact(target.machine(), GameManager.INSTANCE.getGame(player), player);
		return true;
	}

	private DoorProximityManager()
	{
	}

	/**
	 * Starts the repeating proximity check. Called once on plugin enable.
	 */
	public void start()
	{
		if(taskId != -1)
			return;
		// Check every 4 ticks (200ms) — frequent enough to feel instant, light enough to be cheap
		taskId = COMZombies.scheduleTask(4, 4, this::checkProximity);
	}

	/**
	 * Stops the task and forgets every player. Called on plugin disable.
	 */
	public void stop()
	{
		if(taskId != -1)
		{
			COMZombies.getPlugin().getServer().getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		clearAll();
	}

	/**
	 * Forgets a single player (used when they leave a game so their actionbar is cleared).
	 */
	public void clearPlayer(Player player)
	{
		repairs.remove(player);
		nearDoors.remove(player);
		String last = lastActionbar.remove(player);
		if(last != null && last.isEmpty() == false)
			sendActionbar(player, "");
	}

	private void clearAll()
	{
		repairs.clear();
		for(Player player : lastActionbar.keySet())
			if(player.isOnline())
				sendActionbar(player, "");
		nearDoors.clear();
		lastActionbar.clear();
	}

	private void checkProximity()
	{
		updateRepairs();
		if(!COMZombies.getPlugin().getConfig().getBoolean("config.doorProximity.enabled", true)
				&& !COMZombies.getPlugin().getConfig().getBoolean("config.barrierProximity.enabled", true)
				&& !COMZombies.getPlugin().getConfig().getBoolean("config.interaction.power.enabled", true)
				&& !COMZombies.getPlugin().getConfig().getBoolean("config.interaction.mystery_box.enabled", true)
				&& !COMZombies.getPlugin().getConfig().getBoolean("config.interaction.perk.enabled", true)
				&& !COMZombies.getPlugin().getConfig().getBoolean("config.interaction.pack_a_punch.enabled", true))
		{
			// Feature disabled — clear anything we had shown and stop tracking
			for(Player player : lastActionbar.keySet())
				sendActionbar(player, "");
			nearDoors.clear();
			lastActionbar.clear();
			return;
		}

		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
				continue;

			for(Player player : game.getPlayersInGame())
			{
				if(!player.isValid() || game.isPlayerSpectating(player))
				{
					clearPlayer(player);
					continue;
				}


				RepairSession session = repairs.get(player);
				NearbyTarget target = findTarget(player);
				if(target == null)
				{
					nearDoors.remove(player);
					clearActionbar(player);
					continue;
				}

				if(target.door() != null)
					nearDoors.put(player, target.door());
				else
					nearDoors.remove(player);
				String message = target.door() != null ? tooltip(target.door(), player)
						: (target.barrier() != null ? barrierTooltip(target.barrier(), session != null)
						: MachineProximityManager.tooltip(target.machine(), game, player, getKeyLabel()));
				// Refresh even unchanged text so the client's actionbar does not fade out.
				lastActionbar.put(player, message);
				sendActionbar(player, message);
			}
		}
	}

	private Door nearestDoor(Game game, Player player, double radiusSq)
	{
		double best = radiusSq;
		Door bestDoor = null;

		for(Door door : game.doorManager.getDoors())
		{
			if(door.isOpened())
				continue;
			// Measure from either the sign or any door block.
			List<Location> anchors = new ArrayList<>(door.getSignsLocations());
			anchors.addAll(door.getBlockLocations());

			for(Location anchor : anchors)
			{
				if(anchor.getWorld() == null || anchor.getWorld() != player.getWorld())
					continue;
				double d = anchor.distanceSquared(player.getLocation());
				if(d <= best)
				{
					best = d;
					bestDoor = door;
				}
			}
		}
		return bestDoor;
	}

	/**
	 * Builds the tooltip text for a door based on the player's current situation.
	 * Messages come from config.doorProximity.tooltip and may use {key} and {price} placeholders.
	 * The no_funds message also supports {missing} for the additional points needed.
	 */
	private String tooltip(Door door, Player player)
	{
		String key = getKeyLabel();
		String path = "config.doorProximity.tooltip.";
		int price = door.getCost();

		String priceText = ChatColor.translateAlternateColorCodes('&',
				COMZombies.getPlugin().getConfig().getString(path + "price", "&e{price}"));
		priceText = priceText.replace("{price}", String.valueOf(price));

		if(door.isOpened())
			return format(path + "already_open", "&7Door is already open.", key, priceText);
		if(door.requiresPower() && !gameIsPowered(player))
			return format(path + "no_power", "&cRequires power &7- {price} points", key, priceText);
		int points = PointManager.INSTANCE.getPlayerPoints(player).getPoints();
		if(!door.canOpen(points))
			return format(path + "no_funds", "&cNeed {missing} more points &7- {price} points", key, priceText)
					.replace("{missing}", String.valueOf(price - points));

		return format(path + "open", "&aPress {key} to open &7- {price} points", key, priceText);
	}

	private String format(String path, String def, String key, String priceText)
	{
		String value = COMZombies.getPlugin().getConfig().getString(path, def);
		value = value.replace("{key}", key).replace("{price}", priceText);
		return ChatColor.translateAlternateColorCodes('&', value);
	}

	private boolean gameIsPowered(Player player)
	{
		Game game = GameManager.INSTANCE.getGame(player);
		return game != null && game.isPowered();
	}

	/**
	 * @return the door a player is currently standing in range of, or null
	 */
	public Door getNearDoor(Player player)
	{
		if(!COMZombies.getPlugin().getConfig().getBoolean("config.doorProximity.enabled", true))
			return null;
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME || game.isPlayerSpectating(player))
			return null;
		// Recheck at keypress time instead of trusting the last proximity tick.
		double radius = COMZombies.getPlugin().getConfig().getDouble("config.doorProximity.radius", 3.0);
		Door door = nearestDoor(game, player, radius * radius);
		Barrier barrier = getNearBarrier(player);
		if(door != null && barrier != null)
		{
			List<Location> anchors = new ArrayList<>(door.getSignsLocations());
			anchors.addAll(door.getBlockLocations());
			double distance = Double.POSITIVE_INFINITY;
			for(Location anchor : anchors)
				if(player.getWorld().equals(anchor.getWorld()))
					distance = Math.min(distance, anchor.distanceSquared(player.getLocation()));
			if(barrierDistanceSquared(barrier, player) <= distance)
				return null;
		}
		return door;
	}

	/** Finds barriers from saved blocks, including when all blocks have been broken. */
	public Barrier getNearBarrier(Player player)
	{
		if(!COMZombies.getPlugin().getConfig().getBoolean("config.barrierProximity.enabled", true))
			return null;
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME || game.isPlayerSpectating(player))
			return null;
		double radius = COMZombies.getPlugin().getConfig().getDouble("config.barrierProximity.radius", 3.0);
		double best = radius * radius;
		Barrier nearest = null;
		for(Barrier barrier : game.barrierManager.getBarriers())
		{
			double distance = barrierDistanceSquared(barrier, player);
			if(distance <= best)
			{
				best = distance;
				nearest = barrier;
			}
		}
		return nearest;
	}

	private double barrierDistanceSquared(Barrier barrier, Player player)
	{
		double best = Double.POSITIVE_INFINITY;
		for(Block block : barrier.getBlocks())
			if(player.getWorld().equals(block.getWorld()))
				best = Math.min(best, block.getLocation().distanceSquared(player.getLocation()));
		return best;
	}

	public void startRepairing(Player player, Barrier barrier)
	{
		if(barrier.getStage() > -1 && canKeepRepairing(player, barrier))
			repairs.put(player, new RepairSession(barrier, System.nanoTime() + 1_000_000_000L));
	}

	public boolean stopRepairing(Player player)
	{
		return repairs.remove(player) != null;
	}

	private boolean canKeepRepairing(Player player, Barrier barrier)
	{
		Game game = GameManager.INSTANCE.getGame(player);
		double radius = COMZombies.getPlugin().getConfig().getDouble("config.barrierProximity.radius", 3.0);
		return player.isOnline() && player.isValid() && !player.isDead()
				&& COMZombies.getPlugin().getConfig().getBoolean("config.barrierProximity.enabled", true)
				&& game != null && game == barrier.getGame() && game.getStatus() == Game.GameStatus.INGAME
				&& !game.isPlayerSpectating(player) && game.barrierManager.getBarriers().contains(barrier)
				&& barrierDistanceSquared(barrier, player) <= radius * radius;
	}

	private void updateRepairs()
	{
		long now = System.nanoTime();
		Iterator<Map.Entry<Player, RepairSession>> iterator = repairs.entrySet().iterator();
		while(iterator.hasNext())
		{
			Map.Entry<Player, RepairSession> entry = iterator.next();
			Player player = entry.getKey();
			RepairSession session = entry.getValue();
			if(!canKeepRepairing(player, session.barrier()) || session.barrier().getStage() <= -1)
			{
				iterator.remove();
				continue;
			}
			if(now - session.nextRepairNanos() >= 0)
			{
				if(session.barrier().repair(player))
					iterator.remove();
				else
					entry.setValue(new RepairSession(session.barrier(), now + 1_000_000_000L));
			}
		}
	}

	private String barrierTooltip(Barrier barrier, boolean repairing)
	{
		String path = "config.barrierProximity.tooltip.";
		String state = barrier.getStage() <= -1 ? "repaired" : (repairing ? "repairing" : "repair");
		String fallback = barrier.getStage() <= -1 ? "&aBarrier fully repaired" : (repairing
				? "&aRepairing one stage per second &7- Press {key} to stop"
				: "&aPress {key} to start repairing &7- 1 stage per second");
		String key = getKeyLabel();
		return ChatColor.translateAlternateColorCodes('&', COMZombies.getPlugin().getConfig()
				.getString(path + state, fallback).replace("{key}", key));
	}

	/**
	 * Attempts to buy the given door for the player, mirroring the door sign's behaviour.
	 *
	 * @return true if the door was opened
	 */
	public boolean buyDoor(Door door, Player player)
	{
		if(door.isOpened())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This door is already open!");
			return false;
		}
		if(door.requiresPower() && !gameIsPowered(player))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This door requires power to open!");
			return false;
		}
		int points = PointManager.INSTANCE.getPlayerPoints(player).getPoints();
		if(!door.canOpen(points))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			return false;
		}

		door.openDoor();
		door.playerDoorOpenSound();
		PointManager.INSTANCE.takePoints(player, door.getCost());
		PointManager.INSTANCE.notifyPlayer(player);
		CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "Door opened!");

		// Door is open now — clear the tooltip
		nearDoors.remove(player);
		clearActionbar(player);
		return true;
	}

	private void clearActionbar(Player player)
	{
		String last = lastActionbar.remove(player);
		if(last != null && !last.isEmpty())
			sendActionbar(player, "");
	}

	private void sendActionbar(Player player, String message)
	{
		if(player.isValid())
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
	}
}
