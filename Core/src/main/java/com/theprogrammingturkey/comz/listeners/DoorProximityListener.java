package com.theprogrammingturkey.comz.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.managers.DoorProximityManager;
import com.theprogrammingturkey.comz.util.CommandUtil;

/**
 * Lets a player right-click a door block to buy it, instead of having to click the door sign.
 * The sign itself still works as before. Nearby purchases with the swap-hand key are handled by
 * OnInventoryChangeEvent; this listener retains direct block-click purchases.
 */
public class DoorProximityListener implements Listener
{
	/**
	 * Right-click a door block to buy it. Cancels the event so the click isn't also consumed by a
	 * gun or another plugin.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onRightClickBlock(PlayerInteractEvent event)
	{
		if(event.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;

		Player player = event.getPlayer();
		Block clicked = event.getClickedBlock();
		if(clicked == null)
			return;

		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME)
			return;

		Door door = game.doorManager.getDoorFromBlock(clicked.getLocation());
		if(door == null || door.isOpened())
			return;

		event.setCancelled(true);
		try
		{
			DoorProximityManager.INSTANCE.buyDoor(door, player);
		}
		catch(Throwable t)
		{
			COMZombies.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Failed to buy door via block click for " + player.getName(), t);
			CommandUtil.sendMessageToPlayer(player, "&cAn internal error occurred while buying the door.");
		}
	}

	/**
	 * Clear the player's proximity state so their actionbar is cleared when they leave.
	 */
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event)
	{
		DoorProximityManager.INSTANCE.clearPlayer(event.getPlayer());
	}
}
