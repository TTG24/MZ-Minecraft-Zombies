package com.theprogrammingturkey.comz.game.managers;

import java.util.Map;
import java.util.Set;

import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.game.signs.GameSigns;
import com.theprogrammingturkey.comz.game.signs.SignRegistry;

/** Uses the existing machine purchase logic for nearby swap-hand interactions. */
public class MachineProximityManager
{
	private static final Set<String> SIGN_TYPES = Set.of("power", "perk", "pack_a_punch");

	public record Target(String type, Location location, Map<String, String> data, RandomBox box, double distanceSquared) {}

	public static Target nearest(Game game, Player player)
	{
		Target nearest = null;
		for(Map.Entry<Location, SignRegistry.Entry> sign : SignRegistry.INSTANCE.getAll().entrySet())
		{
			String type = sign.getValue().type;
			if(!SIGN_TYPES.contains(type) || GameManager.INSTANCE.getGame(sign.getKey()) != game)
				continue;
			Target candidate = candidate(type, sign.getKey(), sign.getValue().data, null, player);
			if(candidate != null && (nearest == null || candidate.distanceSquared() < nearest.distanceSquared()))
				nearest = candidate;
		}
		for(RandomBox box : game.boxManager.getBoxes())
		{
			// Dormant box locations must not sell rolls; fire sales activate every box.
			if(!game.boxManager.isMultiBox() && !game.isFireSale() && game.boxManager.getCurrentbox() != box
					&& !box.canPickWeapon(player))
				continue;
			Target candidate = candidate("mystery_box", box.getLocation(), Map.of(), box, player);
			if(candidate != null && (nearest == null || candidate.distanceSquared() < nearest.distanceSquared()))
				nearest = candidate;
		}
		return nearest;
	}

	private static Target candidate(String type, Location location, Map<String, String> data, RandomBox box, Player player)
	{
		String path = "config.interaction." + type + ".";
		if(!COMZombies.getPlugin().getConfig().getBoolean(path + "enabled", true)
				|| location == null || !player.getWorld().equals(location.getWorld()))
			return null;
		double radius = COMZombies.getPlugin().getConfig().getDouble(path + "radius", 3.0);
		double distance = location.distanceSquared(player.getLocation());
		return distance <= radius * radius ? new Target(type, location, data, box, distance) : null;
	}

	public static void interact(Target target, Game game, Player player)
	{
		GameSigns.fromType(target.type()).onInteract(game, player, target.location(), target.data());
	}

	public static String tooltip(Target target, Game game, Player player, String key)
	{
		String state = "use";
		String fallback;
		int cost = price(target);
		int points = PointManager.INSTANCE.getPlayersPoints(player);
		String name = target.data().getOrDefault("perk", "").replace('_', ' ');
		if(target.type().equals("power"))
		{
			state = !game.hasPower() ? "unavailable" : (game.isPowered() ? "already_on" : "use");
			fallback = !game.hasPower() ? "&cPower is disabled in this arena" : (game.isPowered()
					? "&aPower is already on" : "&aPress {key} to turn on power");
		}
		else if(target.type().equals("mystery_box"))
		{
			if(target.box().canPickWeapon(player))
			{
				state = "pickup";
				fallback = "&aPress {key} to take your weapon";
			}
			else if(!target.box().canActivate())
			{
				state = "busy";
				fallback = "&eMystery box is busy";
			}
			else
				fallback = "&aPress {key} to use mystery box &7- {price} points";
		}
		else if(target.type().equals("perk"))
		{
			PerkType perk = PerkType.getPerkType(target.data().get("perk"));
			if(perk == null)
			{
				state = "unavailable";
				fallback = "&cPerk machine is unavailable";
			}
			else if(game.perkManager.hasPerk(player, perk))
			{
				state = "owned";
				fallback = "&aYou already have {perk}";
			}
			else if(game.perkManager.getPlayersPerks(player).size() >= ConfigManager.getMainConfig().maxPerks)
			{
				state = "limit";
				fallback = "&cPerk limit reached";
			}
			else
				fallback = "&aPress {key} to buy {perk} &7- {price} points";
		}
		else
		{
			PlayerWeaponManager weapons = game.getPlayersWeapons(player);
			if(weapons == null || !weapons.isHeldItemGun())
			{
				state = "no_weapon";
				fallback = "&eHold a gun to Pack-a-Punch &7- {price} points";
			}
			else if(weapons.getGun(player.getInventory().getHeldItemSlot()).isPackOfPunched())
			{
				state = "upgraded";
				fallback = "&aThis gun is already Pack-a-Punched";
			}
			else
				fallback = "&aPress {key} to Pack-a-Punch &7- {price} points";
		}
		if((target.type().equals("perk") || target.type().equals("pack_a_punch")) && game.hasPower() && !game.isPowered())
		{
			state = "no_power";
			fallback = "&cRequires power &7- {price} points";
		}
		else if(state.equals("use") && !target.type().equals("power") && points < cost)
		{
			state = "no_funds";
			fallback = "&cNeed {missing} more points &7- {price} points";
		}
		String message = COMZombies.getPlugin().getConfig().getString("config.interaction." + target.type() + ".tooltip." + state, fallback);
		return Colors.translateAlternateColorCodes('&', message.replace("{key}", key)
				.replace("{price}", String.valueOf(cost)).replace("{missing}", String.valueOf(Math.max(0, cost - points)))
				.replace("{perk}", name));
	}

	private static int price(Target target)
	{
		if(target.box() != null)
			return target.box().getCost();
		try
		{
			return Integer.parseInt(target.data().getOrDefault("price", "0"));
		}
		catch(NumberFormatException e)
		{
			return 0;
		}
	}
}
