package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.managers.GlowManager;
import com.theprogrammingturkey.comz.util.Accessibility;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Accessibility options, for every player (no permission needed), remembered across restarts:
 * <pre>
 *   /z access                         your current settings
 *   /z access repair toggle|hold      barriers: F starts/stops repairing, or repair only while F is held
 *   /z access glow on|off             see the other players of your match glowing through walls
 *   /z access glow color &lt;colour&gt;      the glow's colour
 * </pre>
 */
public class AccessCommand extends SubCommand
{
	public AccessCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		UUID id = player.getUniqueId();
		String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
		String value = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
		switch(what)
		{
			case "repair" -> {
				if(!value.equals("toggle") && !value.equals("hold"))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z access repair <toggle|hold>");
					return true;
				}
				Accessibility.setHoldRepair(id, value.equals("hold"));
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + (value.equals("hold")
						? "Barrier repair: hold F to repair, let go to stop."
						: "Barrier repair: press F to start repairing, F again to stop."));
			}
			case "glow" -> {
				if(value.equals("on") || value.equals("off"))
				{
					if(value.equals("on") && !GlowManager.INSTANCE.isAvailable())
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "Glow isn't available on this server right now.");
						return true;
					}
					Accessibility.setGlow(id, value.equals("on"));
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + (value.equals("on")
							? "Glow on: your teammates glow through walls during a match. /z access glow color <colour> to change it."
							: "Glow off."));
				}
				else if(value.equals("color") || value.equals("colour"))
				{
					String color = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
					if(!Accessibility.COLORS.contains(color))
					{
						CommandUtil.sendMessageToPlayer(player, Colors.RED + "Colours: " + String.join(", ", Accessibility.COLORS));
						return true;
					}
					Accessibility.setColor(id, color);
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Glow colour: " + color.replace('_', ' ')
							+ (Accessibility.get(id).glow() ? "." : ". (Glow is off: /z access glow on)"));
				}
				else
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z access glow <on|off|color <colour>>");
			}
			default -> {
				Accessibility.Settings s = Accessibility.get(id);
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Accessibility settings:");
				player.sendMessage(Colors.GRAY + " Barrier repair: " + Colors.WHITE + (s.holdRepair() ? "hold F" : "toggle with F")
						+ Colors.DARK_GRAY + "  /z access repair <toggle|hold>");
				player.sendMessage(Colors.GRAY + " Teammate glow: " + Colors.WHITE + (s.glow() ? "on" : "off") + Colors.GRAY + ", colour "
						+ Colors.WHITE + s.color().replace('_', ' ') + Colors.DARK_GRAY + "  /z access glow <on|off|color <colour>>");
			}
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		List<String> options = switch(args.length)
		{
			case 2 -> List.of("repair", "glow");
			case 3 -> args[1].equalsIgnoreCase("repair") ? List.of("toggle", "hold")
					: args[1].equalsIgnoreCase("glow") ? List.of("on", "off", "color") : List.of();
			case 4 -> args[1].equalsIgnoreCase("glow") && args[2].toLowerCase(Locale.ROOT).startsWith("colo") ? Accessibility.COLORS : List.of();
			default -> List.of();
		};
		List<String> out = new ArrayList<>();
		String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
		for(String o : options)
			if(o.startsWith(typed))
				out.add(o);
		return out;
	}
}
