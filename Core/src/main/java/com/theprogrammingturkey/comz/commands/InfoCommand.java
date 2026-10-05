package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

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
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Please specify an arena!" + ChatColor.GRAY + " Type /z info [arena] (section)");
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
					CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------" + ChatColor.RED + game.getName() + ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "World: " + ChatColor.RED + game.arena.getWorld());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Point One: x:" + ChatColor.RED + game.arena.getMin().getBlockX() + ChatColor.GRAY + ", y:" + ChatColor.RED + game.arena.getMin().getBlockY() + ChatColor.GRAY + ", z:" + ChatColor.RED + game.arena.getMin().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Point Two: x:" + ChatColor.RED + game.arena.getMax().getBlockX() + ChatColor.GRAY + ", y:" + ChatColor.RED + game.arena.getMax().getBlockY() + ChatColor.GRAY + ", z:" + ChatColor.RED + game.arena.getMax().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Player Spawn: x:" + ChatColor.RED + game.arena.getPlayerTPLocation().getBlockX() + ChatColor.GRAY + ", y:" + ChatColor.RED + game.arena.getPlayerTPLocation().getBlockY() + ChatColor.GRAY + ", z:" + ChatColor.RED + game.arena.getPlayerTPLocation().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Lobby Spawn: x:" + ChatColor.RED + game.arena.getLobbyLocation().getBlockX() + ChatColor.GRAY + ", y:" + ChatColor.RED + game.arena.getLobbyLocation().getBlockY() + ChatColor.GRAY + ", z:" + ChatColor.RED + game.arena.getLobbyLocation().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Spectator Spawn: x:" + ChatColor.RED + game.arena.getSpectateLocation().getBlockX() + ChatColor.GRAY + ", y:" + ChatColor.RED + game.arena.getSpectateLocation().getBlockY() + ChatColor.GRAY + ", z:" + ChatColor.RED + game.arena.getSpectateLocation().getBlockZ());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Players: ");
					for(Player p : game.getPlayersInGame())
						CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "  " + p.getName());

					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Mode: " + ChatColor.RED + game.getStatus().toString());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Wave Number: " + ChatColor.RED + game.getWave());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Zombies: " + ChatColor.RED + game.spawnManager.getEntities().size());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Zombies Spawned: " + ChatColor.RED + game.spawnManager.getMobsSpawned());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Zombies To Spawn: " + ChatColor.RED + game.spawnManager.getMobsToSpawn());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Spawn Rate: " + ChatColor.RED + "1 zombie / every " + game.spawnManager.getSpawnInterval() + " second(s)");
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Is double points: " + ChatColor.RED + game.isDoublePoints());
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Is insta-kill: " + ChatColor.RED + game.isInstaKill());
				}
				else if(mode.equalsIgnoreCase("spawns") || mode.equalsIgnoreCase("spawn"))
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------" + ChatColor.RED + "Spawn Points" + ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Total spawns: " + game.spawnManager.getTotalSpawns());
					for(int i = 0; i < game.spawnManager.getTotalSpawns(); i++)
					{
						CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Point " + (i + 1));
						CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "X:" + game.spawnManager.getPoints().get(i).getLocation().getBlockX());
						CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Y:" + game.spawnManager.getPoints().get(i).getLocation().getBlockY());
						CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Z:" + game.spawnManager.getPoints().get(i).getLocation().getBlockZ());
					}
					return true;
				}
				else if(mode.equalsIgnoreCase("doors") || mode.equalsIgnoreCase("door"))
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------" + ChatColor.RED + "Spawn Points" + ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Total doors: " + game.doorManager.getDoors().size());
					for(Door door : game.doorManager.getDoors())
					{
						CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Door " + door.doorID);
						CommandUtil.sendMessageToPlayer(player, "  " + ChatColor.GRAY + "Blocks: " + ChatColor.RED + door.getBlocks().size());
						CommandUtil.sendMessageToPlayer(player, "  " + ChatColor.GRAY + "Signs: " + ChatColor.RED + door.getSignsLocations().size());
						CommandUtil.sendMessageToPlayer(player, "  " + ChatColor.GRAY + "Is Open: " + ChatColor.RED + door.isOpened());
					}
					return true;
				}
				else if(mode.equalsIgnoreCase("zombies") || mode.equalsIgnoreCase("zombie"))
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------" + ChatColor.RED + "Zombies" + ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "----------");
					CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Total Zombies Alive: " + game.spawnManager.getEntities().size());
					for(int i = 1; i <= game.spawnManager.getEntities().size(); i++)
					{
						int acc = i - 1;
						CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Zombie " + i);
						CommandUtil.sendMessageToPlayer(player, "  " + ChatColor.GRAY + "Is Dead: " + ChatColor.RED + game.spawnManager.getEntities().get(acc).isDead());
						CommandUtil.sendMessageToPlayer(player, "  " + ChatColor.GRAY + "Location: ");
						CommandUtil.sendMessageToPlayer(player, "    " + ChatColor.GRAY + "X: " + ChatColor.RED + game.spawnManager.getEntities().get(acc).getLocation().getBlockX());
						CommandUtil.sendMessageToPlayer(player, "    " + ChatColor.GRAY + "Y: " + ChatColor.RED + game.spawnManager.getEntities().get(acc).getLocation().getBlockY());
						CommandUtil.sendMessageToPlayer(player, "    " + ChatColor.GRAY + "Z: " + ChatColor.RED + game.spawnManager.getEntities().get(acc).getLocation().getBlockZ());
						CommandUtil.sendMessageToPlayer(player, "  " + ChatColor.GRAY + "Health: " + ChatColor.RED + (game.spawnManager.getEntities().get(acc)));
					}
				}

				else
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "No section " + mode + "! Type /z info " + game.getName() + " for the basic information about this arena!");
					return true;
				}
			} catch(NullPointerException e)
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "No information found! Manager reloaded.");
				GameManager.INSTANCE.loadAllGames();
			}
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "There is no arena called: " + args[1]);
		}
		return true;
	}
}