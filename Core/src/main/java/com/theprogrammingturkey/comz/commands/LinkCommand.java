package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.actions.LinkRegionAction;
import com.theprogrammingturkey.comz.game.managers.LinkManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Der Riese teleporter region commands (the arena can be left out when standing in it):
 * <ul>
 * <li>/z linkadd [arena] &lt;name&gt; - select a link region (a teleporter pad); again with the same name to redo it</li>
 * <li>/z mainlink [arena] - select the mainframe region</li>
 * <li>/z linkremove [arena] &lt;name|mainframe&gt;</li>
 * <li>/z links [arena] - list them and outline them (toggle)</li>
 * <li>/z linkdest [arena] &lt;link&gt; &lt;otherLink|mainframe&gt; - where a link teleports to (other links need config linksToLinks)</li>
 * </ul>
 */
public class LinkCommand extends SubCommand
{
	public enum Mode
	{
		ADD, MAINFRAME, REMOVE, SHOW, DESTINATION
	}

	private static final Set<UUID> SHOWING = new HashSet<>();
	private final Mode mode;

	public LinkCommand(COMZPermission permission, Mode mode)
	{
		super(permission);
		this.mode = mode;
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit teleporter links");
			return true;
		}
		if(mode == Mode.SHOW && SHOWING.remove(player.getUniqueId()))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Link outlines turned off.");
			return true;
		}
		Game game = args.length > 1 ? GameManager.INSTANCE.getGame(args[1]) : null;
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + usage() + " (the arena can be left out while standing in it)");
			return true;
		}
		COMZombies plugin = COMZombies.getPlugin();
		switch(mode)
		{
			case ADD, MAINFRAME ->
			{
				if(plugin.activeActions.containsKey(player))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "You are currently performing another action! Finish it first (type done or cancel).");
					return true;
				}
				String name = LinkManager.MAINFRAME;
				if(mode == Mode.ADD)
				{
					if(args.length < 3 || !args[2].matches("[A-Za-z0-9_-]{1,24}"))
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + usage() + Colors.GRAY + " (name: letters, numbers, - or _)");
						return true;
					}
					name = args[2].toLowerCase();
					if(name.equals(LinkManager.MAINFRAME))
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "Use /z mainlink for the mainframe.");
						return true;
					}
				}
				plugin.activeActions.put(player, new LinkRegionAction(player, game, name, mode == Mode.MAINFRAME));
			}
			case REMOVE ->
			{
				if(args.length < 3)
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + usage());
					return true;
				}
				if(args[2].equalsIgnoreCase(LinkManager.MAINFRAME))
				{
					if(game.linkManager.getMainframe() == null)
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "This arena has no mainframe.");
					else
					{
						game.linkManager.removeMainframe();
						GameManager.INSTANCE.saveAllGames();
						CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Mainframe removed.");
					}
				}
				else if(game.linkManager.removeLink(args[2]))
				{
					GameManager.INSTANCE.saveAllGames();
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Link " + args[2] + " removed.");
				}
				else
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "No link called " + args[2] + "! See /z links.");
			}
			case DESTINATION ->
			{
				if(args.length < 4)
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + usage());
					return true;
				}
				LinkManager.LinkRegion link = game.linkManager.getLink(args[2]);
				if(link == null)
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "No link called " + args[2] + "! See /z links.");
					return true;
				}
				if(args[3].equalsIgnoreCase(LinkManager.MAINFRAME))
				{
					link.destination = null;
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Link " + link.name + " now teleports to the mainframe.");
				}
				else
				{
					if(!plugin.getConfig().getBoolean("config.gameSettings.linksToLinks", false))
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "Links can only go to the mainframe. Set config.gameSettings.linksToLinks to true to allow links to other links.");
						return true;
					}
					LinkManager.LinkRegion other = game.linkManager.getLink(args[3]);
					if(other == null || other == link)
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + (other == link ? "A link cannot go to itself!" : "No link called " + args[3] + "!"));
						return true;
					}
					link.destination = other.name;
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Link " + link.name + " now teleports to link " + other.name + ".");
				}
				GameManager.INSTANCE.saveAllGames();
			}
			case SHOW ->
			{
				List<LinkManager.LinkRegion> links = game.linkManager.getLinks();
				LinkManager.LinkRegion main = game.linkManager.getMainframe();
				boolean linksToLinks = plugin.getConfig().getBoolean("config.gameSettings.linksToLinks", false);
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + links.size() + " link(s) in " + game.getName() + ", mainframe "
						+ (main == null ? Colors.RED + "not set (/z mainlink)" : Colors.DARK_GREEN + "set") + Colors.GRAY + ". "
						+ Colors.AQUA + "Cyan" + Colors.GRAY + " = link, " + Colors.LIGHT_PURPLE + "pink" + Colors.GRAY + " = mainframe. Run it again to turn the outlines off.");
				for(LinkManager.LinkRegion r : links)
					player.sendMessage(Colors.GRAY + " - " + r.name + ": " + (r.maxX - r.minX + 1) + "x" + (r.maxY - r.minY + 1) + "x" + (r.maxZ - r.minZ + 1)
							+ " at " + r.minX + ", " + r.minY + ", " + r.minZ + " -> "
							+ connection(game, r, linksToLinks));
				SHOWING.add(player.getUniqueId());
				loop(player, game);
			}
		}
		return true;
	}

	private String usage()
	{
		return switch(mode)
		{
			case ADD -> "Usage: /z linkadd [arena] <name>";
			case MAINFRAME -> "Usage: /z mainlink [arena]";
			case REMOVE -> "Usage: /z linkremove [arena] <name|mainframe>";
			case SHOW -> "Usage: /z links [arena]";
			case DESTINATION -> "Usage: /z linkdest [arena] <link> <otherLink|mainframe>";
		};
	}

	private static void loop(Player player, Game game)
	{
		if(!SHOWING.contains(player.getUniqueId()) || !player.isOnline())
		{
			SHOWING.remove(player.getUniqueId());
			return;
		}
		drawLinks(player, game);
		COMZombies.scheduleTask(10, () -> loop(player, game));
	}

	/** Turns this outline off for the player (used by /z showall to turn every outline off). */
	public static boolean stopShowing(UUID id)
	{
		return SHOWING.remove(id);
	}

	/** One frame of the link outlines: every link (cyan), a line to where each teleports, and the mainframe (pink). */
	public static void drawLinks(Player player, Game game)
	{
		for(LinkManager.LinkRegion r : game.linkManager.getLinks())
		{
			LinkRegionAction.drawBox(player, game.getWorld(), r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ, LinkRegionAction.LINK, 2);
			// A line to where it teleports, only when that is set up properly (the mainframe exists, or the chosen link
			// exists and linksToLinks is on)
			LinkManager.LinkRegion to = com.theprogrammingturkey.comz.game.TeleporterLinks.destination(game, r);
			if(to != null && to != r)
				com.theprogrammingturkey.comz.util.Highlight.line(player, centre(game, r), centre(game, to),
						to.name.equals(LinkManager.MAINFRAME) ? CONNECTED : LINK_TO_LINK);
		}
		LinkManager.LinkRegion main = game.linkManager.getMainframe();
		if(main != null)
			LinkRegionAction.drawBox(player, game.getWorld(), main.minX, main.minY, main.minZ, main.maxX, main.maxY, main.maxZ, LinkRegionAction.MAINFRAME, 2);
	}

	private static final org.bukkit.Color CONNECTED = org.bukkit.Color.fromRGB(60, 255, 60);
	private static final org.bukkit.Color LINK_TO_LINK = org.bukkit.Color.fromRGB(255, 220, 0);

	/** Where a link goes, and whether that works (green) or not (red, with why). */
	private static String connection(Game game, LinkManager.LinkRegion r, boolean linksToLinks)
	{
		if(r.destination != null && !linksToLinks)
			return (game.linkManager.getMainframe() == null ? Colors.RED + "mainframe (not set! /z mainlink)" : Colors.GREEN + "mainframe")
					+ Colors.GRAY + " (link " + r.destination + " is ignored: linksToLinks is off)";
		if(r.destination != null)
			return game.linkManager.getLink(r.destination) != null ? Colors.YELLOW + "link " + r.destination
					: Colors.RED + "link " + r.destination + " (missing!)";
		return game.linkManager.getMainframe() == null ? Colors.RED + "mainframe (not set! /z mainlink)" : Colors.GREEN + "mainframe";
	}

	/** The middle of a region, at its bottom + 1 so the line runs at body height. */
	private static org.bukkit.Location centre(Game game, LinkManager.LinkRegion r)
	{
		return new org.bukkit.Location(game.getWorld(), (r.minX + r.maxX + 1) / 2.0, r.minY + 1.2, (r.minZ + r.maxZ + 1) / 2.0);
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		if(args.length == 2)
			return GameManager.INSTANCE.getArenas().stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
		Game game = GameManager.INSTANCE.getGame(args[1]);
		if(game == null)
			return new ArrayList<>();
		List<String> names = new ArrayList<>();
		if((mode == Mode.REMOVE || mode == Mode.DESTINATION) && args.length == 3)
		{
			game.linkManager.getLinks().forEach(r -> names.add(r.name));
			if(mode == Mode.REMOVE)
				names.add(LinkManager.MAINFRAME);
		}
		else if(mode == Mode.DESTINATION && args.length == 4)
		{
			names.add(LinkManager.MAINFRAME);
			game.linkManager.getLinks().forEach(r -> names.add(r.name));
		}
		return names.stream().filter(s -> s.startsWith(args[args.length - 1].toLowerCase())).collect(Collectors.toList());
	}
}
