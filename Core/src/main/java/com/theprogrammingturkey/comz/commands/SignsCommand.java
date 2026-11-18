package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.signs.SignStyles;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /zombies signs [arena] hide|show <category|all>    removes or puts back the signs of a category (they still work with the
 *                                                    interact key near them)
 * /zombies signs [arena] wood <category|all> <wood>  changes the sign block of a category to that wood
 * /zombies signs [arena] list                        what is hidden and which woods are set
 * /zombies perksigns [arena] hide|show|wood <wood>   the same for perk signs only
 */
public class SignsCommand extends SubCommand
{
	private final String fixedCategory;

	public SignsCommand(COMZPermission permission, String fixedCategory)
	{
		super(permission);
		this.fixedCategory = fixedCategory;
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit arenas");
			return true;
		}
		Game game = args.length > 1 ? GameManager.INSTANCE.getGame(args[1]) : null;
		String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
		if(game == null)
		{
			usage(player);
			return true;
		}
		if(action.equals("list"))
		{
			list(player, game);
			return true;
		}
		if(!action.equals("hide") && !action.equals("show") && !action.equals("wood"))
		{
			usage(player);
			return true;
		}
		int next = 3;
		String category = fixedCategory;
		if(category == null)
			category = args.length > next ? args[next++].toLowerCase(Locale.ROOT) : "";
		if(!category.equals("all") && !SignStyles.CATEGORIES.contains(category))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Category must be one of: all, " + String.join(", ", SignStyles.CATEGORIES)
					+ ". (Ammo crate, kit, join and spectate signs only work by clicking them, so they can't be hidden.)");
			return true;
		}
		List<String> categories = category.equals("all") ? SignStyles.CATEGORIES : List.of(category);

		if(action.equals("wood"))
		{
			String wood = args.length > next ? args[next].toLowerCase(Locale.ROOT) : "";
			if(!SignStyles.WOODS.contains(wood))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Wood must be one of: " + String.join(", ", SignStyles.WOODS));
				return true;
			}
			int n = 0;
			for(String c : categories)
				n += SignStyles.setWood(game, c, wood);
			CommandUtil.sendMessageToPlayer(player, Colors.GREEN + "Changed " + n + " sign(s) (" + category + ") in " + game.getName()
					+ " to " + wood + ". New ones will be " + wood + " too.");
			return true;
		}

		boolean hide = action.equals("hide");
		int n = 0;
		for(String c : categories)
			n += SignStyles.setHidden(game, c, hide);
		CommandUtil.sendMessageToPlayer(player, Colors.GREEN + (hide ? "Hid " : "Put back ") + n + " sign(s) (" + category + ") in "
				+ game.getName() + (hide ? ". They still work with the interact key, and new ones disappear when finished." : "."));
		return true;
	}

	private void list(Player player, Game game)
	{
		CommandUtil.sendMessageToPlayer(player, Colors.GOLD + "Signs in " + game.getName() + ":");
		for(String c : SignStyles.CATEGORIES)
		{
			String wood = SignStyles.wood(game, c);
			player.sendMessage(Colors.GRAY + " " + c + ": " + SignStyles.locations(game, c).size() + " sign(s)"
					+ (SignStyles.isHidden(game, c) ? Colors.YELLOW + " hidden" : "") + (wood != null ? Colors.AQUA + " " + wood : ""));
		}
	}

	private void usage(Player player)
	{
		if(fixedCategory != null)
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z perksigns <arena> hide|show, /z perksigns <arena> wood <wood>");
		else
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z signs <arena> hide|show <category|all>, "
					+ "/z signs <arena> wood <category|all> <wood>, /z signs <arena> list (the arena can be left out when standing in it)");
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		if(args.length == 3)
			return fixedCategory != null ? List.of("hide", "show", "wood") : List.of("hide", "show", "wood", "list");
		if(fixedCategory != null)
			return args.length == 4 && args[2].equalsIgnoreCase("wood") ? SignStyles.WOODS : null;
		if(args.length == 4 && !args[2].equalsIgnoreCase("list"))
		{
			List<String> out = new ArrayList<>(SignStyles.CATEGORIES);
			out.add(0, "all");
			return out;
		}
		if(args.length == 5 && args[2].equalsIgnoreCase("wood"))
			return SignStyles.WOODS;
		return null;
	}
}
