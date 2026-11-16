package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Arena;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The arena inspector: right-click while holding a breeze rod (admins with zombies.editarena) and it tells you what arena
 * feature you are pointing at, up to 40 blocks away and through air: a barrier, zombie spawn, door, mystery box, game sign,
 * teleporter, or the player spawn / spectator / lobby warp, with its details, and outlines it for a few seconds. The click
 * does nothing else (it won't buy from a sign or open a door).
 */
public class InspectToolListener implements Listener
{
	public static final Material TOOL = Material.BREEZE_ROD;
	private static final double REACH = 40;
	private static final Color PICKED = Color.fromRGB(255, 255, 255);

	/** One click arrives as several events: report once. */
	private final Map<UUID, Long> lastClick = new HashMap<>();

	/** Something set up in an arena, and the block cells it occupies. */
	private record Feature(Game game, String what, List<String> details, List<Location> cells) {}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onClick(PlayerInteractEvent event)
	{
		if(event.getHand() != EquipmentSlot.HAND)
			return;
		Action action = event.getAction();
		if(action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)
			return;
		Player player = event.getPlayer();
		if(player.getInventory().getItemInMainHand().getType() != TOOL || !COMZPermission.EDIT_ARENA.hasPerm(player))
			return;
		event.setCancelled(true);
		long now = System.currentTimeMillis();
		Long last = lastClick.put(player.getUniqueId(), now);
		if(last != null && now - last < 300)
			return;
		inspect(player);
	}

	private void inspect(Player player)
	{
		World world = player.getWorld();
		Map<String, Feature> index = new HashMap<>();
		for(Game game : GameManager.INSTANCE.getGames())
			if(game.arena != null && game.arena.getWorld() != null && game.arena.getWorld().equals(world))
				collect(game, index);
		if(index.isEmpty())
		{
			player.sendMessage(Colors.RED + "[Inspect] No arena set up in this world.");
			return;
		}
		// Walk the line of sight through the air (spawns and warps are air), stopping after the first solid block
		Location eye = player.getEyeLocation();
		Vector dir = eye.getDirection().normalize();
		Feature found = null;
		String lastKey = null;
		for(double d = 0; d <= REACH && found == null; d += 0.2)
		{
			Location at = eye.clone().add(dir.clone().multiply(d));
			String key = key(at);
			if(key.equals(lastKey))
				continue;
			lastKey = key;
			found = index.get(key);
			if(found == null && at.getBlock().getType().isSolid())
			{
				// A solid block that's nothing: say so (so you know what the click stopped at)
				Block b = at.getBlock();
				player.sendMessage(Colors.GRAY + "[Inspect] " + Colors.WHITE + pretty(b.getType()) + Colors.GRAY + " at " + b.getX() + ", " + b.getY() + ", "
						+ b.getZ() + " - not part of any arena setup.");
				return;
			}
		}
		if(found == null)
		{
			player.sendMessage(Colors.GRAY + "[Inspect] Nothing within " + (int) REACH + " blocks.");
			return;
		}
		player.sendMessage(Colors.GOLD + "[Inspect] " + Colors.YELLOW + found.what() + Colors.GRAY + " (arena " + found.game().getName() + ")");
		for(String line : found.details())
			player.sendMessage(Colors.GRAY + "   " + line);
		flash(player, found.cells(), 0);
	}

	/** Outlines what was picked for ~3 seconds. */
	private void flash(Player player, List<Location> cells, int ticks)
	{
		if(ticks > 60 || !player.isOnline())
			return;
		for(Location c : cells)
			Highlight.box(player, c, PICKED, 3);
		COMZombies.scheduleTask(10, () -> flash(player, cells, ticks + 10));
	}

	private void collect(Game game, Map<String, Feature> index)
	{
		Arena a = game.arena;
		// Warps first (they're air cells, the others can overwrite where they overlap with something more specific)
		if(a.hasLobbyLocation())
			warp(game, index, "Lobby", a.getLobbyLocation(), "Where players wait before the game starts.");
		if(a.hasSpectateLocation())
			warp(game, index, "Spectator spawn", a.getSpectateLocation(), "Where spectators and dead players go.");
		if(a.hasSecondPlayerTPLocation())
			warp(game, index, "Player spawn 2", a.getSecondPlayerTPLocation(), "The second spot players start at.");
		if(a.hasPlayerTPLocation())
			warp(game, index, "Player spawn", a.getPlayerTPLocation(), "Where players start the game.");

		for(Map.Entry<String, Location> t : game.teleporterManager.getTeleporters().entrySet())
			if(t.getValue() != null)
				put(index, new Feature(game, "Teleporter '" + t.getKey() + "'", List.of("At " + xyz(t.getValue()) + "."), List.of(t.getValue())));

		for(SpawnPoint sp : game.spawnManager.getPoints())
		{
			List<String> d = new ArrayList<>();
			d.add("Zombie spawn at " + xyz(sp.getLocation()) + ".");
			d.add("Always spawn: " + (sp.isAlwaysActive() ? Colors.AQUA + "ON" : Colors.RED + "OFF") + Colors.GRAY + " (only when a door to its room opens if off).");
			for(Door door : game.doorManager.getDoors())
				if(door.getSpawnsInRoomDoorLeadsTo().contains(sp))
					d.add("Unlocked by door '" + door.doorID + "'.");
			for(Barrier b : game.barrierManager.getBarriers())
				if(b.getSpawnPoints().contains(sp))
					d.add("Comes in through barrier '" + b.getID() + "'.");
			Location l = sp.getLocation();
			put(index, new Feature(game, "Zombie spawn '" + sp.getID() + "'", d, List.of(l, l.clone().add(0, 1, 0))));
		}

		for(Barrier b : game.barrierManager.getBarriers())
		{
			List<String> d = new ArrayList<>();
			int stage = b.getStage();
			d.add(b.getBlocks().size() + " block(s), " + (stage >= 5 ? "broken" : stage < 0 ? "intact" : "damaged " + stage + "/5") + ".");
			d.add("Repair reward: " + b.effectiveReward() + " points.");
			List<String> spawns = new ArrayList<>();
			for(SpawnPoint sp : b.getSpawnPoints())
				spawns.add(sp.getID());
			d.add("Zombies come through from spawn(s): " + (spawns.isEmpty() ? "none linked" : String.join(", ", spawns)) + ".");
			List<Location> cells = new ArrayList<>();
			for(Block block : b.getBlocks())
				cells.add(block.getLocation());
			Feature f = new Feature(game, "Barrier '" + b.getID() + "'", d, cells);
			for(Location c : cells)
				index.put(key(c), f);
			if(b.getRepairLoc() != null)
				index.put(key(b.getRepairLoc()), f);
		}

		for(Door door : game.doorManager.getDoors())
		{
			List<String> d = new ArrayList<>();
			d.add("Costs " + door.getCost() + " points" + (door.requiresPower() ? ", needs the power on" : "") + ".");
			if(door.opensWithPower())
				d.add("Opens by itself when the power is turned on.");
			if(door.opensWhenLinked())
				d.add("Opens when its teleporter is linked.");
			d.add(door.getBlocks().size() + " block(s), " + door.getSignsLocations().size() + " sign(s), currently " + (door.isOpened() ? "open" : "closed") + ".");
			List<String> spawns = new ArrayList<>();
			for(SpawnPoint sp : door.getSpawnsInRoomDoorLeadsTo())
				spawns.add(sp.getID());
			d.add("Unlocks spawn(s): " + (spawns.isEmpty() ? "none" : String.join(", ", spawns)) + ".");
			List<Location> cells = new ArrayList<>();
			for(Block block : door.getBlocks())
				cells.add(block.getLocation());
			cells.addAll(door.getSignsLocations());
			Feature f = new Feature(game, "Door '" + door.doorID + "'", d, cells);
			for(Location c : cells)
				index.put(key(c), f);
		}

		for(RandomBox box : game.boxManager.getBoxes())
		{
			List<Location> cells = new ArrayList<>();
			cells.add(box.getLocation());
			if(box.isChestBox())
			{
				Block other = BlockUtils.doubleChestPartner(box.getLocation().getBlock());
				if(other != null)
					cells.add(other.getLocation());
			}
			boolean current = game.boxManager.getCurrentbox() == box;
			Feature f = new Feature(game, "Mystery box '" + box.getId() + "'", List.of((box.isChestBox() ? "Chest" : "Sign") + " box, costs " + box.getCost() + " points.",
					game.getStatus() == Game.GameStatus.INGAME ? (current ? "This is the active box right now." : "Not the active box right now.") : "At " + xyz(box.getLocation()) + "."),
					cells);
			for(Location c : cells)
				index.put(key(c), f);
		}

		for(Location sign : game.signManager.gameSigns)
		{
			if(index.containsKey(key(sign)))
				continue; // already a door/box sign
			List<String> d = new ArrayList<>();
			BlockState state = sign.getBlock().getState();
			if(state instanceof Sign s)
			{
				@SuppressWarnings("deprecation")
				String[] lines = s.getLines();
				List<String> text = new ArrayList<>();
				for(String line : lines)
					if(line != null && !line.isBlank())
						text.add(line);
				d.add("Says: " + (text.isEmpty() ? "(blank)" : String.join(" | ", text)));
			}
			else
				d.add("Hidden game sign (works by the interact key).");
			d.add("At " + xyz(sign) + ".");
			put(index, new Feature(game, "Game sign", d, List.of(sign)));
		}
	}

	private static void warp(Game game, Map<String, Feature> index, String name, Location at, String what)
	{
		put(index, new Feature(game, name, List.of(what, "At " + xyz(at) + "."), List.of(at, at.clone().add(0, 1, 0))));
	}

	private static void put(Map<String, Feature> index, Feature f)
	{
		for(Location c : f.cells())
			index.put(key(c), f);
	}

	private static String key(Location l)
	{
		return l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
	}

	private static String xyz(Location l)
	{
		return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
	}

	private static String pretty(Material m)
	{
		String s = m.name().toLowerCase().replace('_', ' ');
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
