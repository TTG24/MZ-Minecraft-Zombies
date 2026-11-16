package com.theprogrammingturkey.comz.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which players have loaded a server resource pack. A custom font (mz:small) only exists for them: for anyone else
 * the client draws it as empty boxes, so the small prompt font is only used for these players.
 */
public class ResourcePackListener implements Listener
{
	private static final Set<UUID> LOADED = ConcurrentHashMap.newKeySet();

	public static boolean hasPack(Player player)
	{
		// Packs applied while joining (before the world loads) are reported by TacticalMovementPlugin with this metadata
		return LOADED.contains(player.getUniqueId()) || player.hasMetadata("tactical_pack_loaded");
	}

	@EventHandler
	public void onStatus(PlayerResourcePackStatusEvent event)
	{
		switch(event.getStatus())
		{
			case SUCCESSFULLY_LOADED -> LOADED.add(event.getPlayer().getUniqueId());
			case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> LOADED.remove(event.getPlayer().getUniqueId());
			default ->
			{
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event)
	{
		LOADED.remove(event.getPlayer().getUniqueId());
	}
}
