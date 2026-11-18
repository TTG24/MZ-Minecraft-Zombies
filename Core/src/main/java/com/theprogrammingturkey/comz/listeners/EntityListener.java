package com.theprogrammingturkey.comz.listeners;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Painting;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.ArenaInstances;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.ApiCompat;

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

	@EventHandler(ignoreCancelled = true)
	public void hunger(org.bukkit.event.entity.FoodLevelChangeEvent event)
	{
		if(event.getEntity() instanceof org.bukkit.entity.Player player && GameManager.INSTANCE.isPlayerInGame(player)
				&& event.getFoodLevel() < player.getFoodLevel()
				&& com.theprogrammingturkey.comz.COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.disableHunger", true))
		{
			event.setCancelled(true);
			player.setFoodLevel(20);
			player.setSaturation(20f);
		}
	}

	@EventHandler
	public void spawn(CreatureSpawnEvent event)
	{
		Entity entity = event.getEntity();
		if(!event.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.CUSTOM))
			if(GameManager.INSTANCE.isLocationInGame(entity.getLocation()))
				event.setCancelled(true);
	}

	/** The game's own zombies/hellhounds always spawn, even where WorldGuard (or similar) denies mob spawning. */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void ownSpawn(CreatureSpawnEvent event)
	{
		if(com.theprogrammingturkey.comz.spawning.RoundSpawner.spawningOwn && event.isCancelled())
			event.setCancelled(false);
	}

	/** The blocks of the barrier the zombie is at (it may hit players through those), or null. */
	private static java.util.Collection<org.bukkit.block.Block> barrierBlocksOf(Game game, org.bukkit.entity.Mob mob)
	{
		for(com.theprogrammingturkey.comz.game.features.Barrier b : game.barrierManager.getBarriers())
			if(b.isBeside(mob))
				return b.getBlocks();
		return null;
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
				// A vanilla zombie attack through or over a fence, wall, slab... or a BlockBlocker barrier does not land
				if(!com.theprogrammingturkey.comz.util.PeacefulArenaMobs.dealingCheckedHit && damager instanceof org.bukkit.entity.Mob attacker
						&& !com.theprogrammingturkey.comz.game.ZombiePathing.canHit(attacker, player, barrierBlocksOf(game, attacker)))
				{
					e.setCancelled(true);
					return;
				}

				float damage = (float) ConfigManager.getMainConfig().zombieDamage;

				if(game.perkManager.getPlayersPerks(player).contains(PerkType.JUGGERNOG))
					damage = damage / (float) ConfigManager.getMainConfig().juggernogHealth;

				damage = game.damagePlayer(player, damage);
				e.setDamage(damage);
				if(damager instanceof org.bukkit.entity.Mob attacker)
				{
					Game own = GameManager.INSTANCE.getGame(attacker);
					if(own != null)
						own.spawnManager.markActive(attacker);
				}
				if(damage > 0 && damager instanceof org.bukkit.entity.Zombie)
				{
					// The zombie's attack growl where it stands, and the sound of the hit on the player.
					damager.getWorld().playSound(damager.getLocation(), "mz:zombie_attack", org.bukkit.SoundCategory.HOSTILE, 1f, 1f);
					player.getWorld().playSound(player.getLocation(), "mz:zombie_hit_player", org.bukkit.SoundCategory.HOSTILE, 1f, 1f);
				}

				//heal system
				resetHealingTimer(player);

				if(damage == 0)
					e.setCancelled(true);
			}
		}
		else if(e.getEntity() instanceof Mob mob)
		{
			Game game = GameManager.INSTANCE.getGame(mob);

			// Another plugin (e.g. the melee cooldown in TacticalMovementPlugin) already cancelled this hit.
			if(e.isCancelled())
				return;

			if(game == null)
				return;

			if(e.getDamager() instanceof Player player)
			{
				if(e.getCause().equals(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK))
				{
					e.setCancelled(true);
					return;
				}

				if(player.getInventory().getItemInMainHand().getType().equals(Material.END_ROD))
				{
					if(game.getPlayersInGame().contains(player))
					{
						double dist = mob.getLocation().distance(player.getLocation());
						AttributeInstance maxHealth = mob.getAttribute(ApiCompat.MAX_HEALTH);
						if(dist <= ConfigManager.getMainConfig().meleeRange && maxHealth != null)
						{
							// The slash lands on the zombie's side facing the player, at chest height
							org.bukkit.util.Vector look = player.getEyeLocation().getDirection();
							org.bukkit.util.Vector toPlayer = player.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
							if(toPlayer.lengthSquared() > 1.0E-4)
								toPlayer.normalize().multiply(0.3);
							org.bukkit.util.Vector cut = mob.getLocation().toVector().add(toPlayer).add(new org.bukkit.util.Vector(0,
									Math.max(0.3, Math.min(mob.getHeight() * 0.85, player.getEyeLocation().getY() - mob.getLocation().getY() - 0.3)), 0));
							com.theprogrammingturkey.comz.util.BloodMessages.nextHit(cut, look,
									com.theprogrammingturkey.comz.util.BloodMessages.SLASH);
							game.damageMob(mob, player, (float) (maxHealth.getValue() / game.getWave()), false, true);
							// The hit really landed (in reach): play the slash, and tell other plugins so they do not
							// also play the "swing" sound for this attack.
							player.getWorld().playSound(player.getLocation(), "mz:knife_slash", org.bukkit.SoundCategory.PLAYERS, 1.0F, 1.0F);
							player.setMetadata("mz_melee_hit", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), Bukkit.getCurrentTick()));
						}
					}
				}
			}
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void protectInstanceDecorations(EntityDamageEvent e)
	{
		// Decorative entities copied from the arena template (armor stands, paintings,
		// item frames) should persist for the life of an instance world.
		if(isInstanceDecoration(e.getEntity()))
			e.setCancelled(true);
	}

	@EventHandler
	public void interactInstanceDecorations(PlayerInteractEntityEvent e)
	{
		// Also block right-click interactions (renaming armor stands, swapping armor,
		// rotating item frames, ...), not just damage.
		if(isInstanceDecoration(e.getRightClicked()))
			e.setCancelled(true);
	}

	private boolean isInstanceDecoration(Entity entity)
	{
		if(!(entity instanceof ArmorStand || entity instanceof Painting || entity instanceof ItemFrame))
			return false;
		return ArenaInstances.INSTANCE.isInstanceWorld(entity.getWorld());
	}

	@EventHandler
	public void damgeEvent(EntityDamageEvent e)
	{
		if(!GameManager.INSTANCE.isEntityInGame(e.getEntity()))
			return;

		// Zombies in a game never take fire or lava damage (sunlight, fire, lava, magma blocks), and stop burning
		if(!(e.getEntity() instanceof Player)
				&& (org.bukkit.tag.DamageTypeTags.IS_FIRE.isTagged(e.getDamageSource().getDamageType())
				|| e.getCause() == EntityDamageEvent.DamageCause.FIRE || e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
				|| e.getCause() == EntityDamageEvent.DamageCause.LAVA))
		{
			e.setCancelled(true);
			e.getEntity().setFireTicks(0);
			return;
		}

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
				else if(!e.isCancelled())
					// Any damage that lands (falls included, not only zombie hits) starts the regen countdown again.
					resetHealingTimer(player);
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

	/**
	 * Safety net: a player in a match, or watching its ending, never really dies. A vanilla death sends them to the main
	 * world's spawn (an instance world has no respawn point) still in spectator mode, with the game-over board up and the
	 * ending cut short. Runs last: whatever damage is still going to land and would kill downs them instead (or, once the
	 * match is over, is simply cancelled).
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void neverDieInGame(EntityDamageEvent e)
	{
		if(!(e.getEntity() instanceof Player player))
			return;
		if(GameManager.INSTANCE.getGameOverSpectatingGame(player) != null)
		{
			e.setCancelled(true);
			return;
		}
		if(!GameManager.INSTANCE.isPlayerInGame(player))
			return;
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || e.getFinalDamage() < player.getHealth() + player.getAbsorptionAmount())
			return;
		e.setCancelled(true);
		if(game.getStatus() == Game.GameStatus.INGAME && !game.downedPlayerManager.isDownedPlayer(player))
			game.damagePlayer(player, Float.MAX_VALUE); // goes down (or, the last one, ends the match) instead of dying
	}

	/** If a player still dies anyway (e.g. /kill), they come back inside the arena they were playing, not the main world. */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void respawnInArena(org.bukkit.event.player.PlayerRespawnEvent e)
	{
		Player player = e.getPlayer();
		Game game = GameManager.INSTANCE.getGameOverSpectatingGame(player);
		if(game == null && GameManager.INSTANCE.isPlayerInGame(player))
			game = GameManager.INSTANCE.getGame(player);
		if(game != null && game.arena.getSpectateLocation() != null && game.arena.getSpectateLocation().getWorld() != null)
			e.setRespawnLocation(game.arena.getSpectateLocation());
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
