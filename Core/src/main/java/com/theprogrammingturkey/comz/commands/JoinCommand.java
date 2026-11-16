package com.theprogrammingturkey.comz.commands;

import java.util.Map;

import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.GamePlayer;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class JoinCommand extends SubCommand
{
	public JoinCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			Map<Player, GamePlayer> gamePlayers = GameManager.INSTANCE.getGame(player).gamePlayers;
			if(gamePlayers.get(player).hasLeftGame())
				gamePlayers.remove(player);
			else
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "You must leave your current game first!");
			return true;
		}
		if(GameManager.INSTANCE.getGames().isEmpty())
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "There are no arenas!");
			return true;
		}

		if(args.length == 1)
		{
			// The arena you are standing in, else the nearest one in this world (within 96 blocks): join that one
			Game here = nearbyArena(player);
			if(here != null)
				return onCommand(player, new String[] {args[0], here.getTemplateName()});
			for(Game game : GameManager.INSTANCE.getGames())
			{
				if(game.isTemplate()) continue;
				if(game.isInstance() && com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.hasQueue(game.getTemplateName())) continue;
				if(game.getStatus() != GameStatus.DISABLED && (game.getStatus() != GameStatus.INGAME || game.canLateJoin()))
				{
					if(game.spawnManager.getPoints().isEmpty())
						continue;
					if(game.maxPlayers <= game.getPlayersInGame().size())
						continue;
					if(COMZPermission.JOIN_ARENA.hasPerm(player, game.getTemplateName()))
					{
						com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.cancelQueue(player.getUniqueId());
						game.addPlayer(player);
						return true;
					}
				}
			}
			for(Game template : GameManager.INSTANCE.getGames())
				if(template.isTemplate() && template.getStatus() != GameStatus.DISABLED
						&& COMZPermission.JOIN_ARENA.hasPerm(player, template.getName()))
				{
					com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.enqueue(template, player);
					return true;
				}
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "No arena available!");
		}
		else
		{
			if(GameManager.INSTANCE.isValidArena(args[1]))
			{
				Game game = GameManager.INSTANCE.getGame(args[1]);
				if(game.isTemplate())
				{
					if(game.getStatus() == GameStatus.DISABLED)
					{
						CommandUtil.sendMessageToPlayer(player, "This arena pool is disabled.");
						return true;
					}
					if(!COMZPermission.JOIN_ARENA.hasPerm(player, game.getName()))
					{
						CommandUtil.noPermission(player, "join this game");
						return true;
					}
					Game available = GameManager.INSTANCE.findJoinable(game.getName());
					if(available == null || com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.hasQueue(game.getName()))
					{
						com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.enqueue(game, player);
						return true;
					}
					game = available;
				}
				if(game.getStatus() != GameStatus.DISABLED && (game.getStatus() != GameStatus.INGAME || game.canLateJoin()))
				{
					if(game.spawnManager.getPoints().isEmpty())
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "Arena has no spawn points!");
						return true;
					}
					if(game.maxPlayers <= game.getPlayersInGame().size())
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "Game is full!");
						return true;
					}
					if(COMZPermission.JOIN_ARENA.hasPerm(player, game.getTemplateName()))
					{
						com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.cancelQueue(player.getUniqueId());
						game.addPlayer(player);
						CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "You joined " + game.getName() + "!");
						return true;
					}

					CommandUtil.noPermission(player, "join this game");
					return false;
				}
				else
				{
					if(game.getStatus() == GameStatus.INGAME)
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + game.getName() + " is already in progress!");
						return true;
					}
					else if(game.getStatus() == GameStatus.DISABLED)
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "This arena is disabled!");
						return true;
					}
				}
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "There is no arena called " + Colors.GRAY + args[1]);
				return true;
			}
		}
		return false;
	}

	private static final double NEARBY = 96;

	/** The arena (template, or a plain arena) the player is in, or else the closest one whose box is within NEARBY blocks. */
	private static Game nearbyArena(Player player)
	{
		org.bukkit.Location at = player.getLocation();
		// In an arena copy's world (e.g. watching a match end there): that arena
		if(com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.isInstanceWorld(at.getWorld()))
			for(Game game : GameManager.INSTANCE.getGames())
				if(game.isInstance() && at.getWorld().equals(game.getWorld()))
				{
					Game template = GameManager.INSTANCE.getGame(game.getTemplateName());
					return template != null ? template : game;
				}
		Game best = null;
		double bestDist = NEARBY * NEARBY;
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.isInstance() || game.arena == null || game.arena.getWorld() == null || !game.arena.getWorld().equals(at.getWorld()))
				continue;
			if(game.arena.containsBlock(at))
				return game;
			if(!game.arena.hasMin() || !game.arena.hasMax())
				continue;
			org.bukkit.Location a = game.arena.getMin(), b = game.arena.getMax();
			double dx = gap(at.getX(), a.getBlockX(), b.getBlockX()), dy = gap(at.getY(), a.getBlockY(), b.getBlockY()),
					dz = gap(at.getZ(), a.getBlockZ(), b.getBlockZ());
			double d = dx * dx + dy * dy + dz * dz;
			if(d < bestDist)
			{
				bestDist = d;
				best = game;
			}
		}
		return best;
	}

	/** Distance from v to the block range [min(a,b), max(a,b)+1], 0 inside it. */
	private static double gap(double v, int a, int b)
	{
		double lo = Math.min(a, b), hi = Math.max(a, b) + 1;
		return v < lo ? lo - v : v > hi ? v - hi : 0;
	}
}
