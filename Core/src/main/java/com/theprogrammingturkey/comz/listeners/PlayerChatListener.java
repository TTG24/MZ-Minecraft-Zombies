package com.theprogrammingturkey.comz.listeners;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import com.theprogrammingturkey.comz.util.Colors;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.actions.BaseAction;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class PlayerChatListener implements Listener
{

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(AsyncChatEvent playerChat)
	{
		COMZombies plugin = COMZombies.getPlugin();
		Player player = playerChat.getPlayer();
		String message = Colors.plain(playerChat.message()).replaceFirst(" ", "").trim();

		if(plugin.activeActions.containsKey(player))
		{
			BaseAction action = plugin.activeActions.get(player);

			if(message.equalsIgnoreCase("cancel"))
			{
				COMZombies.scheduleTask(1, () ->
				{
					plugin.activeActions.remove(player);
					action.cancelAction();
				});
			}
			else
			{
				COMZombies.scheduleTask(1, () -> action.onChatMessage(message));
			}
			playerChat.setCancelled(true);
		}

		if(plugin.isEditingASign.containsKey(player))
		{
			if(message.equalsIgnoreCase("done"))
			{
				Location loc = plugin.isEditingASign.get(player);
				Sign sign = (Sign) loc.getBlock().getState();
				plugin.isEditingASign.remove(player);
				// Build the modern List<Component> form from the sign's current
				// front-side lines so the event behaves identically for downstream listeners.
				org.bukkit.block.sign.SignSide front = sign.getSide(Side.FRONT);
				List<Component> lines = new ArrayList<>();
				for(int i = 0; i < 4; i++)
					lines.add(front.line(i));
				Bukkit.getServer().getPluginManager().callEvent(new SignChangeEvent(sign.getBlock(), player, lines, Side.FRONT));
				CommandUtil.sendMessageToPlayer(player, "You are no longer editing a sign!");
				playerChat.setCancelled(true);
				sign.update();
			}
		}
	}
}
