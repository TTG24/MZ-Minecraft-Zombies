package com.theprogrammingturkey.comz.commands;

import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class InfoCommand extends SubCommand
{
	public InfoCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.INFO.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view this game's information");
			return true;
		}

		if(args.length == 1)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Please specify an arena!" + Colors.GRAY + " Type /z info [arena] (section)");
			return true;
		}
		else if(GameManager.INSTANCE.isValidArena(args[1]))
		{
			String mode = "info";
			Game game = GameManager.INSTANCE.getGame(args[1]);
			if(args.length >= 3)
				mode = args[2];

			try
			{
				if(mode.equalsIgnoreCase("info"))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------" + Colors.RED + game.getName() + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "World: " + Colors.RED + game.arena.getWorld());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Point One: x:" + Colors.RED + game.arena.getMin().getBlockX() + Colors.GRAY + ", y:" + Colors.RED + game.arena.getMin().getBlockY() + Colors.GRAY + ", z:" + Colors.RED + game.arena.getMin().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Point Two: x:" + Colors.RED + game.arena.getMax().getBlockX() + Colors.GRAY + ", y:" + Colors.RED + game.arena.getMax().getBlockY() + Colors.GRAY + ", z:" + Colors.RED + game.arena.getMax().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Player Spawn: x:" + Colors.RED + game.arena.getPlayerTPLocation().getBlockX() + Colors.GRAY + ", y:" + Colors.RED + game.arena.getPlayerTPLocation().getBlockY() + Colors.GRAY + ", z:" + Colors.RED + game.arena.getPlayerTPLocation().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Lobby Spawn: " + (game.arena.hasLobbyLocation() ? "" : "(using game spawn) ") + "x:" + Colors.RED + game.arena.getLobbyLocation().getBlockX() + Colors.GRAY + ", y:" + Colors.RED + game.arena.getLobbyLocation().getBlockY() + Colors.GRAY + ", z:" + Colors.RED + game.arena.getLobbyLocation().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Spectator Spawn: x:" + Colors.RED + game.arena.getSpectateLocation().getBlockX() + Colors.GRAY + ", y:" + Colors.RED + game.arena.getSpectateLocation().getBlockY() + Colors.GRAY + ", z:" + Colors.RED + game.arena.getSpectateLocation().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Players: ");
					for(Player p : game.getPlayersInGame())
						CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "  " + p.getName());

					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mode: " + Colors.RED + game.getStatus().toString());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Wave Number: " + Colors.RED + game.getWave());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Zombies: " + Colors.RED + game.spawnManager.getEntities().size());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Zombies Spawned: " + Colors.RED + game.spawnManager.getMobsSpawned());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Zombies To Spawn: " + Colors.RED + game.spawnManager.getMobsToSpawn());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Spawn Rate: " + Colors.RED + "1 zombie / every " + game.spawnManager.getSpawnInterval() + " second(s)");
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Is double points: " + Colors.RED + game.isDoublePoints());
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Is insta-kill: " + Colors.RED + game.isInstaKill());
				}
				else if(mode.equalsIgnoreCase("spawns") || mode.equalsIgnoreCase("spawn"))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------" + Colors.RED + "Spawn Points" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Total spawns: " + game.spawnManager.getTotalSpawns());
					for(int i = 0; i < game.spawnManager.getTotalSpawns(); i++)
					{
						CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Point " + (i + 1));
						CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "X:" + game.spawnManager.getPoints().get(i).getLocation().getBlockX());
						CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Y:" + game.spawnManager.getPoints().get(i).getLocation().getBlockY());
						CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Z:" + game.spawnManager.getPoints().get(i).getLocation().getBlockZ());
					}
					return true;
				}
				else if(mode.equalsIgnoreCase("doors") || mode.equalsIgnoreCase("door"))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------" + Colors.RED + "Spawn Points" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Total doors: " + game.doorManager.getDoors().size());
					for(Door door : game.doorManager.getDoors())
					{
						CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Door " + door.doorID);
						CommandUtil.sendMessageToPlayer(player, "  " + Colors.GRAY + "Blocks: " + Colors.RED + door.getBlocks().size());
						CommandUtil.sendMessageToPlayer(player, "  " + Colors.GRAY + "Signs: " + Colors.RED + door.getSignsLocations().size());
						CommandUtil.sendMessageToPlayer(player, "  " + Colors.GRAY + "Is Open: " + Colors.RED + door.isOpened());
					}
					return true;
				}
				else if(mode.equalsIgnoreCase("zombies") || mode.equalsIgnoreCase("zombie"))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------" + Colors.RED + "Zombies" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Total Zombies Alive: " + game.spawnManager.getEntities().size());
					for(int i = 1; i <= game.spawnManager.getEntities().size(); i++)
					{
						int acc = i - 1;
						CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Zombie " + i);
						CommandUtil.sendMessageToPlayer(player, "  " + Colors.GRAY + "Is Dead: " + Colors.RED + game.spawnManager.getEntities().get(acc).isDead());
						CommandUtil.sendMessageToPlayer(player, "  " + Colors.GRAY + "Location: ");
						CommandUtil.sendMessageToPlayer(player, "    " + Colors.GRAY + "X: " + Colors.RED + game.spawnManager.getEntities().get(acc).getLocation().getBlockX());
						CommandUtil.sendMessageToPlayer(player, "    " + Colors.GRAY + "Y: " + Colors.RED + game.spawnManager.getEntities().get(acc).getLocation().getBlockY());
						CommandUtil.sendMessageToPlayer(player, "    " + Colors.GRAY + "Z: " + Colors.RED + game.spawnManager.getEntities().get(acc).getLocation().getBlockZ());
						CommandUtil.sendMessageToPlayer(player, "  " + Colors.GRAY + "Health: " + Colors.RED + (game.spawnManager.getEntities().get(acc)));
					}
				}

				else
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "No section " + mode + "! Type /z info " + game.getName() + " for the basic information about this arena!");
					return true;
				}
			} catch(NullPointerException e)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "No information found! Manager reloaded.");
				GameManager.INSTANCE.loadAllGames();
			}
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "There is no arena called: " + args[1]);
		}
		return true;
	}
}