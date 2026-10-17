package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.PowerUp;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class PowerUpDropListener implements Listener
{
	@EventHandler
	private void onPowerUpPickup(EntityPickupItemEvent event)
	{
		if(event.getEntity() instanceof Player player)
		{
			final Item eItem = event.getItem();

			final Game game = GameManager.INSTANCE.getGame(player);
			if(game == null)
				return;

			if(!GameManager.INSTANCE.isPlayerInGame(player))
			{
				if(game.powerUpManager.getCurrentPowerUps().contains(event.getItem()))
					event.setCancelled(true);
				return;
			}
			else if(!game.powerUpManager.getCurrentPowerUps().contains(event.getItem()))
			{
				event.setCancelled(true);
				return;
			}

			event.getItem().remove();
			event.setCancelled(true);

			game.powerUpManager.getCurrentPowerUps().remove(event.getItem());

			ItemStack item = event.getItem().getItemStack();
			PowerUp powerUp = PowerUp.getPowerUpForMaterial(item.getType());

			if(powerUp != PowerUp.NONE)
			{
				player.getInventory().remove(item);
				notifyAll(game, powerUp);
			}

			int duration = -1;
			switch(powerUp)
			{
				case MAX_AMMO ->
				{
					for(Player pl : game.getPlayersInGame())
					{
						PlayerWeaponManager manager = game.getPlayersWeapons(pl);
						manager.maxAmmo();
					}
				}
				case INSTA_KILL ->
				{
					if(!game.isInstaKill())
					{
						game.setInstaKill(true);
						duration = ConfigManager.getMainConfig().instaKillTimer * 20;
						game.scheduleMatchTask(duration, () -> game.setInstaKill(false));
					}
				}
				case CARPENTER ->
				{
					for(Barrier barrier : game.barrierManager.getBarriers())
						barrier.repairFull();
				}
				case NUKE ->
				{
					for(Player pl : game.getPlayersInGame())
					{
						if(game.isDoublePoints())
							PointManager.INSTANCE.addPoints(player, 800);
						else
							PointManager.INSTANCE.addPoints(player, 400);
						PointManager.INSTANCE.notifyPlayer(pl);
					}
					game.spawnManager.nuke();
				}
				case DOUBLE_POINTS ->
				{
					if(!game.isDoublePoints())
					{
						game.setDoublePoints(true);
						duration = ConfigManager.getMainConfig().doublePointsTimer * 20;
						game.scheduleMatchTask(duration, () -> game.setDoublePoints(false));
					}
				}
				case FIRE_SALE ->
				{
					if(!game.isFireSale())
					{
						game.setFireSale(true);
						game.boxManager.FireSale();
						duration = ConfigManager.getMainConfig().fireSaleTimer * 20;
						game.scheduleMatchTask(duration, () ->
						{
							game.setFireSale(false);
							game.boxManager.FireSale();
						});
					}
				}
				default ->
				{
					player.updateInventory();
					COMZombies.scheduleTask(5, () -> player.getInventory().removeItem(eItem.getItemStack()));
				}
			}

			if(duration != -1)
				for(Player pl : game.getPlayersInGame())
					powerUpDisplayTimer(pl, powerUp, duration);
		}
		else if(GameManager.INSTANCE.isEntityInGame(event.getEntity()))
		{
			event.setCancelled(true);
		}
	}

	public void powerUpDisplayTimer(Player player, PowerUp powerUp, int duration)
	{
		if(!GameManager.INSTANCE.isPlayerInGame(player))
			return;
		// Translate the legacy ChatColor string to an Adventure component; the String
		// overload of sendActionBar is deprecated on Paper in favour of the Component one.
		player.sendActionBar(LegacyComponentSerializer.legacySection()
				.deserialize(Colors.RED + powerUp.getDisplay() + ": " + (duration / 20)));
		COMZombies.scheduleTask(20, () ->
		{
			if(duration - 20 > 0)
				powerUpDisplayTimer(player, powerUp, duration - 20);
		});
	}

	public void notifyAll(Game game, PowerUp powerUp)
	{
		for(Player pl : game.getPlayersInGame())
		{
			pl.sendMessage(Colors.RED + powerUp.getDisplay() + "!");
			pl.playSound(pl.getLocation(), powerUp.getSound(), 1, 1);
		}
	}
}
