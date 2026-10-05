package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class ZombiesHelpCommand
{
	private final Player player;
	private int page;

	public ZombiesHelpCommand(CommandManager command, Player player)
	{
		this.player = player;
		page = 1;
	}

	public void playerBaseHelp()
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "-------- ======== " + ChatColor.RED + "[ MinecraftZombies ]" + ChatColor.DARK_GREEN + " ======== --------");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "        /zombies help admin  - Admin help page!");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "        /zombies help user - Displays the user help page!");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "        /zombies help signs - Displays the signs information!");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "        /zombies help info - Displays plugin information!");
	}

	public void playerAdminHelp()
	{
		if(page == 1)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "---------" + ChatColor.RED + " MinecraftZombies Admin Help - Page " + page + " " + ChatColor.DARK_GREEN + "--------");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies createarena [arena]" + ChatColor.GRAY + " - Creates a new arena with a given name.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies removearena [arena]" + ChatColor.GRAY + " - Removes the arena given.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies kick [player] [arena]" + ChatColor.GRAY + " - Kicks the given player from the given arena.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies forcestart [arena]" + ChatColor.GRAY + " - Force starts the given arena.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies help admin 2 - Type this for the next page of admin help!");
		}
		else if(page == 2)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "---------" + ChatColor.RED + " MinecraftZombies Admin Help - Page " + page + " " + ChatColor.DARK_GREEN + "--------");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies editspawns [arena]" + ChatColor.GRAY + " - Enables Zombie spawn editing for the given arena.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies edit [arena]" + ChatColor.GRAY + " - Puts you in arena creation mode for an old arena.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies enable [arena]" + ChatColor.GRAY + " - Enables the given arena.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies help admin 3 - Type this for the next page of admin help!");
		}
		else if(page == 3)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "---------" + ChatColor.RED + " MinecraftZombies Admin Help - Page " + page + " " + ChatColor.DARK_GREEN + "--------");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies disable [arena]" + ChatColor.GRAY + " - Disables the given arena.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies addBarrier [arena]" + ChatColor.GRAY + " - Begins the creation of a barrier.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies removeBarrier [arena]" + ChatColor.GRAY + " - Begins the process to remove a barrier.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies addDoor [arena]" + ChatColor.GRAY + " - Begins the creation of a door.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies removeDoor [arena]" + ChatColor.GRAY + " - Begins the process to remove a door.");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies help admin 4 - Type this for the next page of admin help!");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "No page " + page + "!");
		}
	}

	public void playerUserHelp()
	{
		CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "---------" + ChatColor.RED + " MinecraftZombies User Help - Page " + page + " " + ChatColor.DARK_GREEN + "--------");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies join" + ChatColor.GRAY + " - Puts you in the next available arena.");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies join [arena]" + ChatColor.GRAY + " - Join a specific arena.");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies leave" + ChatColor.GRAY + " - Leave the game you're currently in.");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies listarenas" + ChatColor.GRAY + " - Shows a list of all the games.");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "/zombies perks" + ChatColor.GRAY + " - Shows the list of available perks.");
	}

	public void playerSignHelp()
	{
		if(page == 1)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "----------" + ChatColor.RED + " Sign Help - Page " + page + " " + ChatColor.DARK_GREEN + "----------");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Signs always need [Zombies] on the first line!");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "MysteryBox" + ChatColor.GRAY + " - second line = box |third line (Box Price) | fourth line is empty!");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Wall gun" + ChatColor.GRAY + " - second line = gun | third line = (gun name) | fourth line = (gun price / ammo price)");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Perk" + ChatColor.GRAY + " - second line = perk | third line = (Perk Name) | fourth line is (Perk Price)!");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Pack-A-Punch" + ChatColor.GRAY + " - second line = pack | third line = (Price to Pack-A-Punch) | fourth line is Empty!");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "teleporter" + ChatColor.GRAY + " - second line = teleporter | third line = (teleporter name) | fourth line is (teleporter Price)!");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "No page " + page + "!");
		}
	}

	public void playerInfoHelp()
	{
		if(page == 1)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "----------" + ChatColor.RED + " Plugin Information - Page " + page + " " + ChatColor.DARK_GREEN + "----------");
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "MinecraftZombies" + ChatColor.GRAY + " is a multi-arena zombies plugin inspired by Call of Duty© Zombies, made by " + ChatColor.RED + "TTG24" + ChatColor.GRAY + ". It is based on Call of Minecraft: Zombies, programmed by " + ChatColor.RED + "IModZombies4Fun and TurkeyDev" + ChatColor.GRAY + ".");
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "------------------------------------");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "No page " + page + "!");
		}
	}

	public void commandIssued(String[] args)
	{
		if(args.length < 2)
		{
			playerBaseHelp();
			return;
		}
		if(args[1].equalsIgnoreCase("admin"))
		{
			try
			{
				page = Integer.parseInt(args[2]);
			} catch(Exception e)
			{
				page = 1;
			}
			playerAdminHelp();
		}
		else if(args[1].equalsIgnoreCase("user"))
		{
			try
			{
				page = Integer.parseInt(args[2]);
			} catch(Exception e)
			{
				page = 1;
			}
			playerUserHelp();
		}
		else if(args[1].equalsIgnoreCase("signs") || args[1].equalsIgnoreCase("sign"))
		{
			try
			{
				page = Integer.parseInt(args[2]);
			} catch(Exception e)
			{
				page = 1;
			}
			playerSignHelp();
		}
		else if(args[1].equalsIgnoreCase("info"))
		{
			try
			{
				page = Integer.parseInt(args[2]);
			} catch(Exception e)
			{
				page = 1;
			}
			playerInfoHelp();
		}
		else
		{
			try
			{
				page = Integer.parseInt(args[1]);
			} catch(Exception e)
			{
				playerBaseHelp();
			}
		}
	}
}
