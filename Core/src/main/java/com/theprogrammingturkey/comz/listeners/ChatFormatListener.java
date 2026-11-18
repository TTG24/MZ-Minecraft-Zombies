package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Themed player chat instead of vanilla "&lt;name&gt; message":
 * <pre>☠ [Arena R12] [Rank] Name » message</pre>
 * The arena tag only shows while the player is in a match (red while downed, gray while spectating). The rank is the
 * LuckPerms prefix (through Vault), if there is one. Only the look changes: chat still goes to everyone as before.
 */
public class ChatFormatListener implements Listener
{
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onChat(AsyncChatEvent event)
	{
		Player player = event.getPlayer();
		// Worked out once per message (not per viewer)
		Component head = Component.text("☠ ", NamedTextColor.DARK_RED).append(gameTag(player)).append(prefix(player));
		event.renderer((source, displayName, message, viewer) -> head
				.append(displayName.colorIfAbsent(NamedTextColor.WHITE))
				.append(Component.text(" » ", NamedTextColor.DARK_GRAY))
				.append(message.colorIfAbsent(NamedTextColor.GRAY)));
	}

	private static Component gameTag(Player player)
	{
		try
		{
			return gameTagUnsafe(player);
		} catch(RuntimeException e)
		{
			return Component.empty(); // the game changed mid-read (chat is off the main thread)
		}
	}

	private static Component gameTagUnsafe(Player player)
	{
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null)
			return Component.empty();
		boolean live = game.getStatus() == Game.GameStatus.INGAME;
		NamedTextColor color = game.isPlayerSpectating(player) ? NamedTextColor.GRAY
				: live && game.isPlayerDowned(player) ? NamedTextColor.RED : NamedTextColor.DARK_RED;
		String text = game.getTemplateName() + (live ? " R" + game.getWave() : "");
		return Component.text("[", NamedTextColor.DARK_GRAY).append(Component.text(text, color))
				.append(Component.text("] ", NamedTextColor.DARK_GRAY));
	}

	private static Component prefix(Player player)
	{
		try
		{
			if(Bukkit.getPluginManager().getPlugin("Vault") == null)
				return Component.empty();
			RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> rsp = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
			String p = rsp == null ? null : rsp.getProvider().getPlayerPrefix(player);
			if(p == null || p.isBlank())
				return Component.empty();
			return LEGACY.deserialize(p.replace('§', '&').stripTrailing()).decoration(TextDecoration.BOLD, TextDecoration.State.NOT_SET)
					.append(Component.text(" "));
		} catch(Throwable t)
		{
			return Component.empty();
		}
	}
}
