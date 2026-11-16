package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.actions.DoorEditAction;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /zombies editdoor <arena> [doorId]: edit a door's blocks, room spawns, price and power in place. Defaults to the nearest door. */
public class EditDoorCommand extends SubCommand
{
	public EditDoorCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.ADD_DOOR.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit a door");
			return true;
		}
		if(COMZombies.getPlugin().activeActions.containsKey(player))
		{
			CommandUtil.sendMessageToPlayer(player, "You are currently performing another action and cannot edit a door right now!");
			return true;
		}
		if(args.length < 2)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z editdoor <arena> [doorId] (or stand near the door). /z doorspawns <arena> shows which door is which.");
			return true;
		}
		Game game = GameManager.INSTANCE.getGame(args[1]);
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "That is not a valid arena!");
			return true;
		}
		if(game.getStatus() != GameStatus.DISABLED && game.getStatus() != GameStatus.WAITING)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Doors can only be edited while the arena is not in a game.");
			return true;
		}

		Door target = null;
		if(args.length > 2)
		{
			for(Door d : game.doorManager.getDoors())
				if(d.doorID.equalsIgnoreCase(args[2]))
					target = d;
		}
		else
		{
			double best = Double.MAX_VALUE;
			for(Door d : game.doorManager.getDoors())
			{
				List<Location> anchors = new ArrayList<>(d.getSignsLocations());
				d.getBlocks().forEach(b -> anchors.add(b.getLocation()));
				for(Location loc : anchors)
				{
					if(loc.getWorld() == null || !loc.getWorld().equals(player.getWorld()))
						continue;
					double dist = loc.distanceSquared(player.getLocation());
					if(dist < best)
					{
						best = dist;
						target = d;
					}
				}
			}
		}
		if(target == null)
		{
			StringBuilder ids = new StringBuilder();
			for(Door d : game.doorManager.getDoors())
				ids.append(ids.length() == 0 ? "" : ", ").append(d.doorID);
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Door not found. Doors in " + args[1] + ": " + (ids.length() == 0 ? "none" : ids));
			return true;
		}

		COMZombies.getPlugin().activeActions.put(player, new DoorEditAction(player, game, target));
		return true;
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		if(args.length == 2)
		{
			return GameManager.INSTANCE.getArenas().stream()
					.filter(a -> a.toLowerCase().startsWith(args[1].toLowerCase())).collect(java.util.stream.Collectors.toList());
		}
		if(args.length == 3)
		{
			Game game = GameManager.INSTANCE.getGame(args[1]);
			List<String> ids = new ArrayList<>();
			if(game != null)
				for(Door d : game.doorManager.getDoors())
					if(d.doorID.toLowerCase().startsWith(args[2].toLowerCase()))
						ids.add(d.doorID);
			return ids;
		}
		return List.of();
	}
}
