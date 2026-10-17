package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.entity.Player;

public class CommandUtil
{
	/**
	 * @param player  to send the message to
	 * @param message to be sent to the player
	 */
	public static void sendMessageToPlayer(Player player, String message)
	{
		player.sendMessage(COMZombies.PREFIX + message);
	}

	/**
	 * @param player  to send the message to
	 * @param message to be sent to the player
	 */
	public static void sendClickableMessageToPlayer(Player player, String message, String clickMessage, String command)
	{
	Component prefix = LegacyComponentSerializer.legacySection().deserialize(COMZombies.PREFIX + message);
	Component click = Component.text(clickMessage)
		.color(NamedTextColor.RED)
		.hoverEvent(HoverEvent.showText(Component.text("Click to run command")))
		.clickEvent(ClickEvent.runCommand(command));
	player.sendMessage(prefix.append(click));
	}

	/**
	 * @param player for the no permission message to be sent to
	 */
	public static void noPermission(Player player, String action)
	{
		player.sendMessage(COMZombies.PREFIX + Colors.RED + "You do not have permission to " + action + "!");
	}
}
