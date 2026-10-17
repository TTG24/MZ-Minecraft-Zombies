package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Sound;
import com.theprogrammingturkey.comz.util.ApiCompat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class EntityListener implements Listener
{
	private final Map<Player, Integer> healTimers = new HashMap<>();

	@EventHandler(ignoreCancelled = true)
	public void onEntityTarget(EntityTargetEvent event)
	{
		// Zombies held back at a barrier are walked to it, so don't let their AI go after players
		if(!(event.getTarget() instanceof Player))
			return;
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.barrierManager.isHeldBack(event.getEntity()))
			{
				event.setCancelled(true);
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void entityCombustEvent(EntityCombustEvent event)
	{
		if(GameManager.INSTANCE.isEntityInGame(event.getEntity()))
			event.setCancelled(true);
	}

	@EventHandler
	public void spawn(CreatureSpawnEvent event)
	{
		Entity entity = event.getEntity();
		if(!event.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.CUSTOM))
			if(GameManager.INSTANCE.isLocationInGame(entity.getLocation()))
				event.setCancelled(true);
	}

	@EventHandler
	public void damage(EntityDamageByEntityEvent e)
	{
		if(e.getEntity() instanceof Player player && GameManager.INSTANCE.isPlayerInGame(player))
		{
			if(e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)
			{
				Entity damager = e.getDamager();
				if(damager instanceof Player || !GameManager.INSTANCE.isEntityInGame(damager))
				{
					e.setCancelled(true);
					return;
				}

				Game game = GameManager.INSTANCE.getGame(player);
				if(game.downedPlayerManager.isDownedPlayer(player))
				{
					e.setCancelled(true);
					return;
				}

				float damage = (float) ConfigManager.getMainConfig().zombieDamage;

				if(game.perkManager.getPlayersPerks(player).contains(PerkType.JUGGERNOG))
					damage = damage / (float) ConfigManager.getMainConfig().juggernogHealth;

				damage = game.damagePlayer(player, damage);
				e.setDamage(damage);

				//heal system
				resetHealingTimer(player);

				if(damage == 0)
					e.setCancelled(true);
			}
		}
		else if(e.getEntity() instanceof Mob mob)
		{
			Game game = GameManager.INSTANCE.getGame(mob);

			if(game == null)
				return;

			if(e.getDamager() instanceof Player player)
			{
				if(e.getCause().equals(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK))
				{
					e.setCancelled(true);
					return;
				}

				if(player.getInventory().getItemInMainHand().getType().equals(Material.IRON_SWORD))
				{
					if(game.getPlayersInGame().contains(player))
					{
						double dist = mob.getLocation().distance(player.getLocation());
						AttributeInstance maxHealth = mob.getAttribute(ApiCompat.MAX_HEALTH);
						if(dist <= ConfigManager.getMainConfig().meleeRange && maxHealth != null)
							game.damageMob(mob, player, (float) (maxHealth.getValue() / game.getWave()));
					}
				}
			}
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void damgeEvent(EntityDamageEvent e)
	{
		if(!GameManager.INSTANCE.isEntityInGame(e.getEntity()))
			return;

		if(e.getEntity() instanceof Player player)
		{
			Game game = GameManager.INSTANCE.getGame(player);

			if(game.downedPlayerManager.isDownedPlayer(player))
				e.setCancelled(true);

			if(game.getStatus() == Game.GameStatus.STARTING)
				e.setCancelled(true);

			if(game.getStatus() == Game.GameStatus.INGAME)
			{
				float damage = game.damagePlayer(player, (float) e.getDamage());
				e.setDamage(damage);
				if(damage == 0)
					e.setCancelled(true);
			}

			player.getLocation().getWorld().playSound(player.getLocation().add(0, 1, 0), Sound.BLOCK_STONE_STEP, 1f, 1f);
		}
		else if(e.getCause().equals(EntityDamageEvent.DamageCause.LAVA) && e.getEntity() instanceof Mob)
		{
			Mob m = (Mob) e.getEntity();
			Game game = GameManager.INSTANCE.getGame(m);
			m.setFireTicks(0);
			m.teleport(game.arena.getPlayerTPLocation());
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onHealthRegen(EntityRegainHealthEvent e)
	{
		Entity ent = e.getEntity();
		if(ent instanceof Player && GameManager.INSTANCE.isPlayerInGame((Player) ent))
			e.setCancelled(true);
	}

	private void startHealingTimer(Player player)
	{
		if(!healTimers.containsKey(player))
		{
			BukkitRunnable healingTask = new BukkitRunnable()
			{
				@Override
				public void run()
				{
					int currentHealth = (int) player.getHealth();
					if(currentHealth < 20)
						player.setHealth(Math.min(currentHealth + 1, 20));
					else
						stopHealingTimer(player);
				}
			};
			//every tick
			int taskId = healingTask.runTaskTimer(COMZombies.getPlugin(), Math.round(20 * ConfigManager.getMainConfig().healTime), 1).getTaskId();
			healTimers.put(player, taskId);
		}
	}

	private void stopHealingTimer(Player player)
	{
		if(healTimers.containsKey(player))
		{
			Bukkit.getScheduler().cancelTask(healTimers.get(player));
			healTimers.remove(player);
		}
	}

	private void resetHealingTimer(Player player)
	{
		stopHealingTimer(player);
		startHealingTimer(player);
	}
}
