package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /zombies doorspawns <arena> [doorId]: lists the spawn points a door unlocks and highlights them with particles (only
 * the player sees them, for about 20 seconds). Without a door id it uses the door nearest to the player. Spawns marked
 * always active are shown in green, the rest in red.
 */
public class DoorSpawnsCommand extends SubCommand
{
	private static final int SECONDS = 20;

	public DoorSpawnsCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ZOMBIE_SPAWNS.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view door spawns");
			return true;
		}
		if(args.length < 2)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z doorspawns <arena> [doorId] (or stand near the door)");
			return true;
		}
		Game game = GameManager.INSTANCE.getGame(args[1]);
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + args[1] + " is not a valid arena! Type /z la for a list of arenas!");
			return true;
		}

		Door door = null;
		if(args.length > 2)
		{
			for(Door d : game.doorManager.getDoors())
				if(d.doorID.equalsIgnoreCase(args[2]))
					door = d;
		}
		else
		{
			double best = Double.MAX_VALUE;
			for(Door d : game.doorManager.getDoors())
			{
				List<Location> anchors = new ArrayList<>(d.getSignsLocations());
				anchors.addAll(d.getBlockLocations());
				for(Location loc : anchors)
				{
					if(loc.getWorld() == null || !loc.getWorld().equals(player.getWorld()))
						continue;
					double dist = loc.distanceSquared(player.getLocation());
					if(dist < best)
					{
						best = dist;
						door = d;
					}
				}
			}
		}
		if(door == null)
		{
			StringBuilder ids = new StringBuilder();
			for(Door d : game.doorManager.getDoors())
				ids.append(ids.length() == 0 ? "" : ", ").append(d.doorID);
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Door not found. Doors in " + args[1] + ": " + (ids.length() == 0 ? "none" : ids));
			return true;
		}

		List<SpawnPoint> spawns = new ArrayList<>(door.getSpawnsInRoomDoorLeadsTo());
		if(spawns.isEmpty())
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Door " + door.doorID + " has no spawn points.");
			return true;
		}

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Door " + door.doorID + " unlocks " + spawns.size() + " spawn(s) . Highlighted for " + SECONDS + "s: the door in cyan, spawns in red (green = always active):");
		for(SpawnPoint sp : spawns)
		{
			Location l = sp.getLocation();
			player.sendMessage(Colors.GRAY + " - " + sp.getID() + " at " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + (sp.isAlwaysActive() ? " (always active)" : ""));
		}
		highlight(player, door, spawns, SECONDS * 2);
		return true;
	}

	private static void highlight(Player player, Door door, List<SpawnPoint> spawns, int ticksLeft)
	{
		if(ticksLeft <= 0 || !player.isOnline())
			return;
		// The selected door (cyan), then the spawns it unlocks (red, or green when always active)
		Highlight.blocks(player, door.getBlocks(), Highlight.DOOR);
		for(Location sign : door.getSignsLocations())
			Highlight.box(player, sign, Highlight.DOOR);
		for(SpawnPoint sp : spawns)
			Highlight.box(player, sp.getLocation(), sp.isAlwaysActive() ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		COMZombies.scheduleTask(10, () -> highlight(player, door, spawns, ticksLeft - 1));
	}
}
