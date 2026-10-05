package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.WeaponType;
import com.theprogrammingturkey.comz.util.ApiCompat;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Crouching while holding a gun zooms in.
 * <p>
 * The client sets its field of view from the movement speed attribute compared to the walk speed. Setting the
 * attribute to 0 while leaving the walk speed alone gives a steady 2x zoom (and stops the player moving).
 * The old way, a negative walk speed, changed both values and the walk speed reaches the client a tick before the
 * attribute does, which showed as a flash of much stronger zoom before settling.
 */
public class ScopeListener implements Listener
{
	private static final float NORMAL_SPEED = 0.2F;

	// Movement speed each zoomed player had before zooming, to put back when un-zooming
	private final Map<Player, Double> savedSpeeds = new HashMap<>();
	// Helmets swapped out for the sniper scope overlay, to put back when un-zooming
	private final Map<Player, ItemStack> savedHelmets = new HashMap<>();

	@EventHandler
	public void onPlayerSneak(final PlayerToggleSneakEvent e)
	{
		Player player = e.getPlayer();
		if(!GameManager.INSTANCE.isPlayerInGame(player))
			return;

		// Always un-zoom when standing up, whatever is held. Checking for a gun first is what left
		// players stuck zoomed after switching to an empty slot.
		if(!e.isSneaking())
		{
			unzoom(player);
			return;
		}

		zoom(player);
	}

	@EventHandler
	public void onSlotChange(PlayerItemHeldEvent e)
	{
		Player player = e.getPlayer();
		if(!GameManager.INSTANCE.isPlayerInGame(player) || !isZoomed(player))
			return;

		unzoom(player);
		// The held slot changes after this event, so check the new item next tick
		COMZombies.scheduleTask(1, () ->
		{
			if(player.isSneaking())
				zoom(player);
		});
	}

	private void zoom(Player player)
	{
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME)
			return;

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(!manager.isHeldItemGun())
			return;

		// Anything else (e.g. downed players crawling) has its own walk speed, so leave it alone
		if(player.getWalkSpeed() != NORMAL_SPEED || isZoomed(player))
			return;

		AttributeInstance speed = player.getAttribute(ApiCompat.MOVEMENT_SPEED);
		if(speed == null)
			return;
		savedSpeeds.put(player, speed.getBaseValue());
		speed.setBaseValue(0);

		GunInstance gun = manager.getGun(player.getInventory().getHeldItemSlot());
		if(gun != null && gun.getType().getWeaponType() == WeaponType.SNIPER_RIFLES && COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.ZoomTexture"))
		{
			savedHelmets.put(player, player.getInventory().getHelmet());
			player.getInventory().setHelmet(new ItemStack(Material.JACK_O_LANTERN, 1));
		}
	}

	private void unzoom(Player player)
	{
		Double savedSpeed = savedSpeeds.remove(player);
		if(savedSpeed == null)
			return;

		// Only put the speed back if nothing else (like being downed) has changed it since zooming
		AttributeInstance speed = player.getAttribute(ApiCompat.MOVEMENT_SPEED);
		if(speed != null && speed.getBaseValue() == 0)
			speed.setBaseValue(savedSpeed);

		if(savedHelmets.containsKey(player))
			player.getInventory().setHelmet(savedHelmets.remove(player));
	}

	private boolean isZoomed(Player player)
	{
		if(!savedSpeeds.containsKey(player))
			return false;

		// Leaving a game while zoomed resets the player's speed without un-zooming, so forget those
		AttributeInstance speed = player.getAttribute(ApiCompat.MOVEMENT_SPEED);
		if(speed == null || speed.getBaseValue() != 0)
		{
			savedSpeeds.remove(player);
			savedHelmets.remove(player);
			return false;
		}
		return true;
	}
}
