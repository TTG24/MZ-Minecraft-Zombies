package com.theprogrammingturkey.comz.commands;

import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;

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
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "-------- ======== " + Colors.RED + "[ MinecraftZombies ]" + Colors.DARK_GREEN + " ======== --------");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "        /zombies help admin  - Admin help page!");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "        /zombies help user - Displays the user help page!");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "        /zombies help signs - Displays the signs information!");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "        /zombies help info - Displays plugin information!");
	}

	public void playerAdminHelp()
	{
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "---------" + Colors.RED + " MinecraftZombies Admin Help - Page " + page + " " + Colors.DARK_GREEN + "--------");
		switch(page)
		{
			case 1 ->
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies createarena [arena]" + Colors.GRAY + " - Creates a new arena with a given name.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies instances [arena] [0-16]" + Colors.GRAY + " - Sets the number of map copies; apply with restart/reload.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies removearena [arena]" + Colors.GRAY + " - Removes the arena given.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies kick [player] [arena]" + Colors.GRAY + " - Kicks the given player from the given arena.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies forcestart [arena]" + Colors.GRAY + " - Force starts the given arena.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies help admin 2 - Type this for the next page of admin help!");
			}
			case 2 ->
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies editspawns [arena]" + Colors.GRAY + " - Enables Zombie spawn editing for the given arena.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies edit [arena]" + Colors.GRAY + " - Puts you in arena creation mode for an old arena.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies enable [arena]" + Colors.GRAY + " - Enables the given arena.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies help admin 3 - Type this for the next page of admin help!");
			}
			case 3 ->
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies disable [arena]" + Colors.GRAY + " - Disables the given arena.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies addBarrier [arena]" + Colors.GRAY + " - Begins the creation of a barrier.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies removeBarrier [arena]" + Colors.GRAY + " - Begins the process to remove a barrier.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies addDoor [arena]" + Colors.GRAY + " - Begins the creation of a door.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies removeDoor [arena]" + Colors.GRAY + " - Begins the process to remove a door.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies setregion [arena] [region|none]" + Colors.GRAY + " - Links an arena to a WorldGuard region for custom containment.");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies help admin 4 - Type this for the next page of admin help!");
			}
			default -> CommandUtil.sendMessageToPlayer(player, Colors.RED + "No page " + page + "!");
		}
	}

	public void playerUserHelp()
	{
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "---------" + Colors.RED + " MinecraftZombies User Help - Page " + page + " " + Colors.DARK_GREEN + "--------");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies join" + Colors.GRAY + " - Puts you in the next available arena.");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies join [arena]" + Colors.GRAY + " - Join a specific arena.");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies solo [arena]" + Colors.GRAY + " - Start a private one-player copy of an arena (auto-removed when you leave).");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies leave" + Colors.GRAY + " - Leave the game you're currently in.");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies listarenas" + Colors.GRAY + " - Shows a list of all the games.");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "/zombies perks" + Colors.GRAY + " - Shows the list of available perks.");
	}

	public void playerSignHelp()
	{
		if(page == 1)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "----------" + Colors.RED + " Sign Help - Page " + page + " " + Colors.DARK_GREEN + "----------");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Signs always need [Zombies] on the first line!");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "MysteryBox" + Colors.GRAY + " - second line = box |third line (Box Price) | fourth line is empty!");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Wall gun" + Colors.GRAY + " - second line = gun | third line = (gun name) | fourth line = (gun price / ammo price)");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Perk" + Colors.GRAY + " - second line = perk | third line = (Perk Name) | fourth line is (Perk Price)!");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Pack-A-Punch" + Colors.GRAY + " - second line = pack | third line = (Price to Pack-A-Punch) | fourth line is Empty!");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "teleporter" + Colors.GRAY + " - second line = teleporter | third line = (teleporter name) | fourth line is (teleporter Price)!");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "No page " + page + "!");
		}
	}

	public void playerInfoHelp()
	{
		if(page == 1)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "----------" + Colors.RED + " Plugin Information - Page " + page + " " + Colors.DARK_GREEN + "----------");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "MinecraftZombies" + Colors.GRAY + " is a multi-arena zombies plugin inspired by Call of Duty© Zombies, made by " + Colors.RED + "TTG24" + Colors.GRAY + ". It is based on Call of Minecraft: Zombies, programmed by " + Colors.RED + "IModZombies4Fun and TurkeyDev" + Colors.GRAY + ".");
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "------------------------------------");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "No page " + page + "!");
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
			page = parsePage(args, 2);
			playerAdminHelp();
		}
		else if(args[1].equalsIgnoreCase("user"))
		{
			page = parsePage(args, 2);
			playerUserHelp();
		}
		else if(args[1].equalsIgnoreCase("signs") || args[1].equalsIgnoreCase("sign"))
		{
			page = parsePage(args, 2);
			playerSignHelp();
		}
		else if(args[1].equalsIgnoreCase("info"))
		{
			page = parsePage(args, 2);
			playerInfoHelp();
		}
		else
		{
			try
			{
				page = Integer.parseInt(args[1]);
			}
			catch(NumberFormatException e)
			{
				playerBaseHelp();
			}
		}
	}

	/** Parses a help page number from the given argument index, defaulting to 1 when missing or invalid. */
	private int parsePage(String[] args, int index)
	{
		if(index >= args.length)
			return 1;
		try
		{
			return Integer.parseInt(args[index]);
		}
		catch(NumberFormatException e)
		{
			return 1;
		}
	}
}
