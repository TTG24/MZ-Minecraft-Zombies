package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Debug: /zombies givegun <gun> [player]. Gives a gun from guns.json (any one, Pack-a-Punched ones too) to a player in a
 * game, as if bought from the wall: it goes into the right slot and is switched to. A gun they already have gets full
 * ammo instead. Names are matched ignoring case, spaces and punctuation ("wunderwaffe_dg-2", "WunderwaffeDG2" or
 * "wunderwaffe dg 2"); tab completion offers them with underscores for spaces.
 */
public class GiveGunCommand extends SubCommand
{
	public GiveGunCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
		{
			CommandUtil.noPermission(player, "give guns");
			return true;
		}
		if(args.length < 2)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z givegun <gun> [player]" + Colors.GRAY + " (/z guns lists them)");
			return true;
		}

		// The last word is a player when one by that name is online (and something is left for the gun)
		Player target = player;
		String[] gunWords = Arrays.copyOfRange(args, 1, args.length);
		if(args.length > 2)
		{
			Player named = Bukkit.getPlayerExact(args[args.length - 1]);
			if(named != null)
			{
				target = named;
				gunWords = Arrays.copyOfRange(args, 1, args.length - 1);
			}
		}
		String wanted = key(String.join(" ", gunWords));
		List<BaseGun> guns = WeaponManager.getGuns();
		BaseGun gun = guns.stream().filter(g -> key(g.getName()).equals(wanted)).findFirst().orElse(null);
		if(gun == null)
		{
			// a unique partial match: "wunder" -> Wunderwaffe DG-2
			List<BaseGun> partial = guns.stream().filter(g -> key(g.getName()).startsWith(wanted)).collect(Collectors.toList());
			if(partial.size() == 1)
				gun = partial.get(0);
			else if(partial.size() > 1)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Which one? " + Colors.GRAY
						+ partial.stream().map(BaseGun::getName).collect(Collectors.joining(", ")));
				return true;
			}
		}
		if(gun == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "No gun called '" + String.join(" ", gunWords) + "'. " + Colors.GRAY + "/z guns lists them.");
			return true;
		}
		if(!GameManager.INSTANCE.isPlayerInGame(target))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + target.getName() + " is not in a game!");
			return true;
		}
		Game game = GameManager.INSTANCE.getGame(target);
		PlayerWeaponManager manager = game.getPlayersWeapons(target);
		if(manager == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + target.getName() + " has no weapons yet (has the game started?)");
			return true;
		}

		if(manager.hasGun(gun))
		{
			manager.getGun(gun).maxAmmo();
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + target.getName() + " already had the " + Colors.RED + gun.getName()
					+ Colors.DARK_GREEN + ": ammo filled.");
			return true;
		}
		int slot = manager.getCorrectSlot(gun);
		manager.removeWeapon(manager.getGun(slot));
		manager.addWeapon(gun.getNewInstance(target, slot));
		com.theprogrammingturkey.comz.util.GunAnimMessages.pickedUp(target, slot);
		CommandUtil.sendMessageToPlayer(target, Colors.DARK_GREEN + "You got the " + Colors.RED + gun.getName() + Colors.DARK_GREEN + "!");
		if(target != player)
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Gave " + target.getName() + " the " + Colors.RED + gun.getName() + Colors.DARK_GREEN + ".");
		return true;
	}

	/** A name to match on: lower case, letters and digits only. */
	private static String key(String name)
	{
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
			return List.of();
		// <gun>: its name with underscores for spaces
		if(args.length == 2)
		{
			String typed = key(args[1]);
			return WeaponManager.getGuns().stream().map(g -> g.getName().replace(' ', '_'))
					.filter(n -> key(n).startsWith(typed)).sorted().collect(Collectors.toList());
		}
		// [player]: players currently in a game
		if(args.length == 3)
		{
			return Bukkit.getOnlinePlayers().stream().filter(p -> GameManager.INSTANCE.isPlayerInGame(p)).map(Player::getName)
					.filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
		}
		return List.of();
	}
}
