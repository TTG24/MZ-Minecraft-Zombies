package com.theprogrammingturkey.comz.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;

/**
 * Links an arena to a WorldGuard region so containment checks use the
 * region (which supports polyhedron and other non-box shapes) instead of
 * the min/max bounding box.
 *
 * <ul>
 *   <li>/zombies setregion &lt;arena&gt; - show the linked region</li>
 *   <li>/zombies setregion &lt;arena&gt; &lt;regionId&gt; - link a region</li>
 *   <li>/zombies setregion &lt;arena&gt; none - clear the link</li>
 * </ul>
 */
public class SetRegionCommand extends SubCommand
{
	public SetRegionCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.SET_REGION.hasPerm(player))
		{
			CommandUtil.noPermission(player, "set the region for this arena");
			return true;
		}

		// /zombies setregion <arena>
		if(args.length == 2)
		{
			Game game = GameManager.INSTANCE.getGame(args[1]);
			if(game == null)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + args[1] + " is not a valid arena!");
				return true;
			}
			String region = game.arena.getWorldGuardRegionId();
			if(region == null)
			{
				CommandUtil.sendMessageToPlayer(player,
						Colors.YELLOW + "Arena " + game.getName() + " is not linked to a WorldGuard region (using the min/max box).");
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player,
						Colors.YELLOW + "Arena " + game.getName() + " is linked to WorldGuard region "
								+ Colors.WHITE + region + Colors.YELLOW + ".");
			}
			return true;
		}

		// /zombies setregion <arena> <regionId|none>
		if(args.length >= 3)
		{
			if(!GameManager.INSTANCE.isValidArena(args[1]))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + args[1] + " is not a valid arena!");
				return true;
			}
			Game game = GameManager.INSTANCE.getGame(args[1]);
			String regionId = args[2];

			if(regionId.equalsIgnoreCase("none") || regionId.equalsIgnoreCase("clear") || regionId.equalsIgnoreCase("off"))
			{
				game.arena.setWorldGuardRegionId(null);
				GameManager.INSTANCE.saveAllGames();
				CommandUtil.sendMessageToPlayer(player,
						Colors.DARK_GREEN + "Cleared the WorldGuard region link for arena "
								+ Colors.RED + game.getName() + Colors.DARK_GREEN + ". Containment now uses the min/max box.");
				return true;
			}

			// Verify the region exists in the arena's world so typos are caught early
			String lookup = lookupWorldGuardRegion(game, regionId);
			if(lookup == null)
			{
				if(!isWorldGuardInstalled())
				{
					CommandUtil.sendMessageToPlayer(player,
							Colors.YELLOW + "WorldGuard is not installed on this server. Region " + regionId
									+ " was saved, but containment will fall back to the min/max box until WorldGuard is present.");
				}
				else
				{
					CommandUtil.sendMessageToPlayer(player,
							Colors.YELLOW + "Could not find WorldGuard region " + Colors.WHITE + regionId
									+ Colors.YELLOW + " in world " + game.getWorld().getName()
									+ Colors.YELLOW + ". Saved anyway - make sure the region exists when the arena is used.");
				}
			}

			game.arena.setWorldGuardRegionId(regionId);
			GameManager.INSTANCE.saveAllGames();
			CommandUtil.sendMessageToPlayer(player,
					Colors.DARK_GREEN + "Linked arena " + Colors.RED + game.getName()
							+ Colors.DARK_GREEN + " to WorldGuard region " + Colors.WHITE + regionId
							+ Colors.DARK_GREEN + ". Spawns, hounds, and player containment now use this region.");
			return true;
		}

		CommandUtil.sendMessageToPlayer(player,
				Colors.RED + "Usage: /zombies setregion <arena> [regionId|none]");
		return true;
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		// <arena>
		if(args.length == 2)
		{
			return GameManager.INSTANCE.getArenas().stream()
					.filter(a -> a.toLowerCase().startsWith(args[1].toLowerCase()))
					.collect(java.util.stream.Collectors.toList());
		}
		// <regionId|none>
		if(args.length == 3)
		{
			Game game = GameManager.INSTANCE.getGame(args[1]);
			List<String> out = new ArrayList<>();
			if(game != null)
				out.addAll(lookupWorldGuardRegionIds(game, args[2]));
			if("none".toLowerCase().startsWith(args[2].toLowerCase()))
				out.add("none");
			return out;
		}
		return null;
	}

	private boolean isWorldGuardInstalled()
	{
		return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
	}

	/**
	 * @return the region ID if it exists in the arena's world, otherwise null.
	 */
	private String lookupWorldGuardRegion(Game game, String regionId)
	{
		List<String> ids = lookupWorldGuardRegionIds(game, regionId);
		return ids.isEmpty() ? null : regionId;
	}

	/**
	 * Returns the WorldGuard region IDs in the arena's world that start with the
	 * supplied prefix. Returns an empty list when WorldGuard is unavailable.
	 */
	private List<String> lookupWorldGuardRegionIds(Game game, String prefix)
	{
		List<String> out = new ArrayList<>();
		if(!isWorldGuardInstalled())
			return out;
		try
		{
			com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
			com.sk89q.worldguard.protection.regions.RegionContainer container = wg.getPlatform().getRegionContainer();
			com.sk89q.worldguard.protection.managers.RegionManager rm =
					container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(game.getWorld()));
			if(rm == null)
				return out;
			String p = prefix.toLowerCase();
			for(String id : rm.getRegions().keySet())
			{
				if(id.toLowerCase().startsWith(p))
					out.add(id);
			}
		}
		catch(Throwable ignored)
		{
			// WorldGuard not fully loaded / API mismatch — no suggestions
		}
		return out;
	}
}
