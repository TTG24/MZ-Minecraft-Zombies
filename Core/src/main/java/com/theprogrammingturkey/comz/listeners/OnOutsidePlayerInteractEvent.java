package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;

public class OnOutsidePlayerInteractEvent implements Listener
{
	@EventHandler
	public void onOusidePlayerItemPickUp(EntityPickupItemEvent e)
	{
		if(!(e.getEntity() instanceof Player))
			return;
		Player player = (Player) e.getEntity();
		Game game = GameManager.INSTANCE.getGame(player.getLocation());
		if(game == null || game.getStatus() == null)
			return;

		if(game.getStatus() != GameStatus.INGAME)
			return;

		if(!GameManager.INSTANCE.isPlayerInGame(player) && GameManager.INSTANCE.isLocationInGame(player.getLocation()))
			e.setCancelled(true);

		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			if(!game.powerUpManager.getCurrentPowerUps().contains(e.getItem()))
			{
				// A stray item is never picked up into the locked game inventory
				e.setCancelled(true);
				e.getItem().remove();
				return;
			}
			e.setCancelled(true);
			e.getItem().remove();
		}
	}

	@EventHandler
	public void itemDrop(PlayerDropItemEvent event)
	{
		Player player = event.getPlayer();
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			event.setCancelled(true);
			// Players without the client mod can't send R or G (the server never sees those keys), so for them Q reloads the
			// held gun, or throws a grenade while holding the grenades. Mod players use R and G; Q does nothing for them.
			// Next tick, once the cancelled drop has put the item back in the hand.
			if(!player.getListeningPluginChannels().contains(com.theprogrammingturkey.comz.game.ZombieRise.CHANNEL))
			{
				boolean grenade = event.getItemDrop().getItemStack().getType() == org.bukkit.Material.SLIME_BALL;
				org.bukkit.Bukkit.getScheduler().runTask(com.theprogrammingturkey.comz.COMZombies.getPlugin(), () ->
				{
					if(grenade)
						com.theprogrammingturkey.comz.listeners.WeaponListener.throwGrenade(player);
					else
						com.theprogrammingturkey.comz.listeners.WeaponListener.reloadHeldGun(player);
				});
			}
		}
		Location loc = player.getLocation();
		if(GameManager.INSTANCE.isLocationInGame(loc))
		{
			if(GameManager.INSTANCE.getGame(loc).getStatus() != GameStatus.INGAME)
				return;

			event.setCancelled(true);
			if(!GameManager.INSTANCE.isPlayerInGame(player))
				player.sendMessage(Colors.RED + "Do not drop items in this arena!");
		}
	}
}
