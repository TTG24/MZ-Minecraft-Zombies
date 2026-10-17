package com.theprogrammingturkey.comz.game.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PowerUp;

public class PowerUpManager
{
	private final List<Entity> currentPowerUps = new ArrayList<>();

	private int dropChance = 0;
	private final Map<PowerUp, Boolean> powerups = new HashMap<>();
	private final Map<Entity, Integer> powerupTasks = new HashMap<>();
	private final Map<Entity, Entity> displays = new HashMap<>();

	public void clear()
	{
		powerupTasks.forEach((item, task) ->
		{
			Bukkit.getScheduler().cancelTask(task);
			currentPowerUps.remove(item);
			item.remove();
		});
		displays.values().forEach(display -> display.remove());
		displays.clear();
		powerupTasks.clear();
	}

	/**
	 * The power-up items currently dropped in this game. Per-game, so instances
	 * do not share dropped items.
	 */
	public List<Entity> getCurrentPowerUps()
	{
		return currentPowerUps;
	}


	public void loadAllPowerUps(JsonObject powerUpSettings)
	{
		dropChance = CustomConfig.getInt(powerUpSettings, "drop_percentage", 3);

		JsonObject powerUpsJson = powerUpSettings.get("powerups").getAsJsonObject();
		for(PowerUp powerUp : PowerUp.values())
			if(powerUp != PowerUp.NONE)
				powerups.put(powerUp, CustomConfig.getBoolean(powerUpsJson, powerUp.name().toLowerCase(), true));
	}

	public JsonObject save()
	{
		JsonObject saveJson = new JsonObject();
		saveJson.addProperty("drop_percentage", dropChance);

		JsonObject powerUpsJson = new JsonObject();
		for(PowerUp powerUp : PowerUp.values())
			if(powerUp != PowerUp.NONE)
				powerUpsJson.addProperty(powerUp.name().toLowerCase(), powerups.get(powerUp));

		saveJson.add("powerups", powerUpsJson);

		return saveJson;
	}

	/**
	 * Drops a given itemstack on the ground at the given location.
	 *
	 * @param mob   to get location from
	 * @param stack to drop on the ground
	 */
	private void dropItem(Entity mob, ItemStack stack)
	{
		Location loc = mob.getLocation();
		Entity droppedItem = loc.getWorld().dropItem(loc, stack);
		droppedItem.setVelocity(new Vector());
		ArmorStand namePlate = (ArmorStand) mob.getWorld().spawnEntity(droppedItem.getLocation().clone().add(0, -1.7, 0), EntityType.ARMOR_STAND);
		namePlate.setVisible(false);
		namePlate.setGravity(false);
		namePlate.setAI(false);
		namePlate.customName(Colors.legacy("30"));
		namePlate.setCustomNameVisible(true);
		displays.put(droppedItem, namePlate);
		currentPowerUps.add(droppedItem);
		int id = COMZombies.scheduleTask(0, 20, new Runnable()
		{
			int time = 30;

			@Override
			public void run()
			{
				if(!currentPowerUps.contains(droppedItem))
				{
					namePlate.remove();
					Bukkit.getScheduler().cancelTask(powerupTasks.remove(droppedItem));
					displays.remove(droppedItem);
					return;
				}

				time--;
				namePlate.customName(Colors.legacy(String.valueOf(time)));
				if(time == 0)
				{
					namePlate.remove();
					droppedItem.remove();
					currentPowerUps.remove(droppedItem);
					Bukkit.getScheduler().cancelTask(powerupTasks.remove(droppedItem));
					displays.remove(droppedItem);
				}
			}
		});
		powerupTasks.put(droppedItem, id);
	}

	public void powerUpDrop(Entity mob, Entity entPlayer)
	{
		if(!(entPlayer instanceof Player) || !GameManager.INSTANCE.isEntityInGame(mob) || !GameManager.INSTANCE.isEntityInGame(entPlayer))
			return;

		Game game = GameManager.INSTANCE.getGame(mob.getLocation());
		if(game == null || game.getStatus() != Game.GameStatus.INGAME)
			return;

		int chance = COMZombies.rand.nextInt(100);
		if(chance < dropChance)
		{
			List<PowerUp> availableRewards = powerups.keySet().stream().filter(powerUp ->
			{
				if(powerUp == PowerUp.FIRE_SALE && game.boxManager.isMultiBox())
					return false;
				return powerups.get(powerUp);
			}).collect(Collectors.toList());

			if(availableRewards.isEmpty())
				return;

			this.dropPowerUp(mob, availableRewards.get(COMZombies.rand.nextInt(availableRewards.size())));
		}
	}

	public void dropPowerUp(Entity mob, PowerUp powerUp)
	{
		dropItem(mob, new ItemStack(powerUp.getMaterial(), 1));
	}
}
