package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.game.weapons.WeaponType;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.RayTrace;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WeaponListener implements Listener
{
	private final DiveListener dive;
	/** Tick of each player's last left click that was handled (shot), and of their last right click / drop. */
	private final Map<UUID, Integer> lastFireTick = new HashMap<>();
	private final Map<UUID, Integer> lastOtherTick = new HashMap<>();

	/** Players holding the trigger down (the client mod's "tacticalmovement:trigger"): the tick it last said so. */
	private final Map<UUID, Integer> triggerHeld = new HashMap<>();
	/** Shots still to come in a burst. */
	private final Map<UUID, Integer> burstLeft = new HashMap<>();
	private static WeaponListener instance;

	public WeaponListener(DiveListener dive)
	{
		this.dive = dive;
		instance = this;
		// Full auto: while the trigger is held, keep firing (the gun's own fire delay sets the rate); bursts: the rest of
		// the burst, as soon as the gun can fire again
		COMZombies.scheduleTask(1, 1, () ->
		{
			int now = Bukkit.getCurrentTick();
			triggerHeld.entrySet().removeIf(e -> e.getValue() < now - 12);
			for(UUID id : new ArrayList<>(triggerHeld.keySet()))
			{
				Player p = Bukkit.getPlayer(id);
				GunInstance gun = p == null ? null : heldGun(p);
				// (only when it can fire: an empty or reloading gun would click and nag every tick)
				if(gun == null || !"auto".equals(gun.getType().fireMode) || !gun.canFireNow() || gun.clipAmmo <= 0)
					continue;
				lastFireTick.put(id, now);
				shoot(p);
			}
			for(UUID id : new ArrayList<>(burstLeft.keySet()))
			{
				Player p = Bukkit.getPlayer(id);
				GunInstance gun = p == null ? null : heldGun(p);
				if(gun == null || gun.isReloading() || !"burst".equals(gun.getType().fireMode))
				{
					burstLeft.remove(id);
					continue;
				}
				if(!gun.canFireNow())
					continue;
				int left = burstLeft.get(id);
				if(shoot(p) && left > 1)
					burstLeft.put(id, left - 1);
				else
					burstLeft.remove(id);
			}
		});
	}

	/** The client mod: the trigger (attack key) is down (1) or up (0); "down" is repeated while held. */
	public static void onTrigger(Player player, byte[] message)
	{
		if(instance == null)
			return;
		boolean down = message.length > 0 && message[0] != 0;
		if(down)
			instance.triggerHeld.put(player.getUniqueId(), Bukkit.getCurrentTick());
		else
			instance.triggerHeld.remove(player.getUniqueId());
	}

	private static GunInstance heldGun(Player player)
	{
		if(!GameManager.INSTANCE.isPlayerInGame(player))
			return null;
		PlayerWeaponManager gunManager = GameManager.INSTANCE.getGame(player).getPlayersWeapons(player);
		if(gunManager == null || !gunManager.isHeldItemGun())
			return null;
		return gunManager.getGun(player.getInventory().getHeldItemSlot());
	}

	private static String heldFireMode(Player player)
	{
		GunInstance gun = heldGun(player);
		return gun == null ? null : gun.getType().fireMode;
	}

	/**
	 * Guns fire on LEFT click (right click no longer shoots). A left click on an entity does not raise a
	 * PlayerInteractEvent, so that case is handled by onGunAttackEntity below.
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && BlockUtils.isSign(event.getClickedBlock()) && !BlockUtils.isBarrierRepairSign(event.getClickedBlock(), event.getPlayer()))
			return;

		if(!event.getAction().equals(Action.LEFT_CLICK_AIR) && !event.getAction().equals(Action.LEFT_CLICK_BLOCK))
		{
			lastOtherTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
			return;
		}

		if(event.getAction().equals(Action.LEFT_CLICK_BLOCK) && BlockUtils.isBarrierRepairSign(event.getClickedBlock(), event.getPlayer()))
			return;

		fireHeldGun(event.getPlayer());
	}

	/** Left-clicking a mob with a gun fires the gun instead of doing vanilla melee damage. */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onGunAttackEntity(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event)
	{
		Player player = event.getPlayer();
		if(!GameManager.INSTANCE.isPlayerInGame(player))
			return;
		Game game = GameManager.INSTANCE.getGame(player);
		PlayerWeaponManager gunManager = game.getPlayersWeapons(player);
		if(game.getStatus() != GameStatus.INGAME || gunManager == null || !gunManager.isHeldItemGun())
			return;
		event.setCancelled(true);
		fireHeldGun(player);
	}

	/** Suppress the broadcast arm-swing animation while a gun is held (the client skips its own). */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onGunSwingAnimation(org.bukkit.event.player.PlayerAnimationEvent event)
	{
		Player player = event.getPlayer();
		if(!GameManager.INSTANCE.isPlayerInGame(player))
			return;
		PlayerWeaponManager gunManager = GameManager.INSTANCE.getGame(player).getPlayersWeapons(player);
		if(gunManager == null || !gunManager.isHeldItemGun())
			return;
		event.setCancelled(true);
		if(event.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING)
			return;
		// A left click right on a zombie sometimes raises neither the attack nor the click-air event (it is too close for
		// one and counts as hitting an entity for the other), so the gun did not fire. The swing always comes: fire from it
		// when nothing else did this tick, the click was not a right click or a drop, and a zombie is in the crosshair.
		int now = Bukkit.getCurrentTick();
		UUID id = player.getUniqueId();
		if(lastFireTick.getOrDefault(id, -10) >= now - 1 || lastOtherTick.getOrDefault(id, -10) >= now - 1)
			return;
		Game game = GameManager.INSTANCE.getGame(player);
		if(game.getStatus() != GameStatus.INGAME)
			return;
		Location eye = dive.getWeaponOrigin(player);
		org.bukkit.util.RayTraceResult hit = player.getWorld().rayTraceEntities(eye, eye.getDirection(), 6, 0.3,
				e -> e instanceof Mob && game.spawnManager.getEntities().contains(e));
		if(hit != null)
			fireHeldGun(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDropForSwing(org.bukkit.event.player.PlayerDropItemEvent event)
	{
		lastOtherTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onInteractEntityForSwing(org.bukkit.event.player.PlayerInteractEntityEvent event)
	{
		lastOtherTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
	}

	@EventHandler
	public void onQuitForSwing(org.bukkit.event.player.PlayerQuitEvent event)
	{
		lastFireTick.remove(event.getPlayer().getUniqueId());
		lastOtherTick.remove(event.getPlayer().getUniqueId());
	}

	/** A click: one shot, or the start of a burst. */
	private void fireHeldGun(Player player)
	{
		lastFireTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
		if(shoot(player) && "burst".equals(heldFireMode(player)))
		{
			GunInstance gun = heldGun(player);
			burstLeft.put(player.getUniqueId(), gun.getType().burst - 1);
		}
	}

	/** Fires the held gun once if it can; true when it did. */
	private boolean shoot(Player player)
	{
		boolean fired = false;
		if(com.theprogrammingturkey.comz.game.features.PerkDrink.isDrinking(player))
			return false;
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			Game game = GameManager.INSTANCE.getGame(player);
			if(game.getStatus() != GameStatus.INGAME)
				return false;
			// Downed players cannot shoot
			if(game.downedPlayerManager.isDownedPlayer(player))
				return false;

			if(game.getPlayersWeapons(player) != null)
			{
				PlayerWeaponManager gunManager = game.getPlayersWeapons(player);
				if(gunManager.isHeldItemGun())
				{
					GunInstance gun = gunManager.getGun(player.getInventory().getHeldItemSlot());
					if(gun.isReloading())
					{
						player.getLocation().getWorld().playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
					}
					else if(gun.wasShot())
					{
						fired = true;
						int shots = 1;
						if(gun.getType().getWeaponType() == WeaponType.SHOTGUNS)
							shots = 7;

						for(int shot = 0; shot < shots; shot++)
						{
							Location shotOrigin = dive.getWeaponOrigin(player);
							Vector dirVec = shotOrigin.getDirection();

							if(shots > 1)
								dirVec.add(new Vector(COMZombies.rand.nextDouble(0.5) - 0.25, COMZombies.rand.nextDouble(0.5) - 0.25, COMZombies.rand.nextDouble(0.5) - 0.25));

							RayTrace rayTrace = new RayTrace(shotOrigin.toVector(), dirVec);
							double distance = gun.getType().distance;
							List<RayTrace.RayEntityIntersection> hitEnts = rayTrace.getZombieIntersects(player.getWorld(), game.spawnManager.getEntities(), distance, game);

							if(hitEnts.isEmpty())
							{
								com.theprogrammingturkey.comz.util.GunAnimMessages.tracer(player, shotOrigin.toVector(), rayTrace.getPosition(distance),
										gun.getType().particleColor);
								continue;
							}

							List<RayTrace.RayEntityIntersection> toDamage = new ArrayList<>();
							double dist = distance;

							if(gun.getType().multiHit)
							{
								toDamage.addAll(hitEnts);
							}
							else
							{
								RayTrace.RayEntityIntersection closest = hitEnts.get(0);
								dist = player.getLocation().distance(closest.hitEnt.getLocation());
								for(RayTrace.RayEntityIntersection ent : hitEnts)
								{
									double dist2 = ent.hitEnt.getLocation().distance(player.getLocation());
									if(dist2 < dist)
									{
										closest = ent;
										dist = dist2;
									}
								}
								toDamage.add(closest);
							}


							com.theprogrammingturkey.comz.util.GunAnimMessages.tracer(player, shotOrigin.toVector(), rayTrace.getPosition(dist),
									gun.getType().particleColor);

							float damage = (float) gun.getType().damage / shots;

							for(RayTrace.RayEntityIntersection toDamageIntesect : toDamage)
							{
								Entity entToDamage = toDamageIntesect.hitEnt;
								if(entToDamage instanceof Mob)
								{
									Mob mob = (Mob) entToDamage;
									if(gun.getType().getName().equalsIgnoreCase("Zombie BFF"))
									{
										for(int i = 0; i < 30; i++)
										{
											Location loc = mob.getLocation();
											player.getWorld().spawnParticle(Particle.HEART, loc.getX(), loc.getY(), loc.getZ(), 1, COMZombies.rand.nextFloat(), COMZombies.rand.nextFloat(), COMZombies.rand.nextFloat(), 1);
										}
									}
									for(Player pl : game.getPlayersInGame())
										pl.playSound(pl.getLocation(), Sound.BLOCK_LAVA_POP, 1.0F, 0.0F);

									boolean headshot = false;
									double zombieHitLocY = toDamageIntesect.intersection.getY() - entToDamage.getLocation().getY();
									double eyeHeight = ((Mob) entToDamage).getEyeHeight();
									boolean crawler = com.theprogrammingturkey.comz.game.Crawlers.isCrawler(mob);
									if(crawler ? com.theprogrammingturkey.comz.game.Crawlers.isHeadshot(mob, toDamageIntesect.intersection)
											: zombieHitLocY > eyeHeight - (entToDamage.getHeight() - eyeHeight))
									{
										headshot = true;
										damage *= 1.5f;
										for(int i = 0; i < 20; i++)
											player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, entToDamage.getLocation().getX(), entToDamage.getLocation().getY() + eyeHeight, entToDamage.getLocation().getZ(), 0, COMZombies.rand.nextDouble(2d) - 1d, COMZombies.rand.nextDouble(2d) - 1d, COMZombies.rand.nextDouble(2d) - 1d, 1);
									}

									com.theprogrammingturkey.comz.util.BloodMessages.nextHit(toDamageIntesect.intersection.clone(), dirVec.clone(),
											headshot ? com.theprogrammingturkey.comz.util.BloodMessages.HEADSHOT : com.theprogrammingturkey.comz.util.BloodMessages.SHOT);
									game.damageMob(mob, player, damage, headshot);
								}
							}
						}
					}
				}
			}
		}
		return fired;
	}

	/**
	 * Reloads the gun the player is holding. Triggered by the R key: the Tactical Movement client mod sends a
	 * "tacticalmovement:reload" plugin message (registered in COMZombies). Left click no longer reloads.
	 */
	public static void reloadHeldGun(Player player)
	{
		if(!GameManager.INSTANCE.isPlayerInGame(player) || com.theprogrammingturkey.comz.game.features.PerkDrink.isDrinking(player))
			return;
		Game game = GameManager.INSTANCE.getGame(player);
		if(game.getStatus() != GameStatus.INGAME)
			return;

		PlayerWeaponManager gunManager = game.getPlayersWeapons(player);
		if(gunManager != null && gunManager.isHeldItemGun())
		{
			GunInstance gun = gunManager.getGun(player.getInventory().getHeldItemSlot());
			gun.reload();
			gun.updateWeapon();
		}
	}

	/**
	 * Throws one grenade from the player's grenade stack (wherever it is in the inventory: the mod's G key and Q both throw
	 * without switching to it). Does nothing when they have none, are downed, or the match is not running.
	 */
	public static void throwGrenade(Player player)
	{
		if(!GameManager.INSTANCE.isPlayerInGame(player) || com.theprogrammingturkey.comz.game.features.PerkDrink.isDrinking(player))
			return;
		Game game = GameManager.INSTANCE.getGame(player);
		if(game.getStatus() != GameStatus.INGAME || game.downedPlayerManager.isDownedPlayer(player))
			return;
		var inv = player.getInventory();
		for(int i = 0; i < inv.getStorageContents().length; i++)
		{
			ItemStack stack = inv.getItem(i);
			if(stack != null && stack.getType() == Material.SLIME_BALL && stack.getAmount() > 0)
			{
				throwGrenade(game, player, stack);
				inv.setItem(i, stack.getAmount() > 0 ? stack : null);
				return;
			}
		}
		// No grenades left: a monkey bomb, if there is one
		for(int i = 0; i < inv.getStorageContents().length; i++)
		{
			ItemStack stack = inv.getItem(i);
			if(stack != null && stack.getType() == Material.MAGMA_CREAM && stack.getAmount() > 0)
			{
				throwMonkey(game, player, stack);
				inv.setItem(i, stack.getAmount() > 0 ? stack : null);
				return;
			}
		}
	}

	/** A thrown grenade / monkey bomb's item, with its model (so it looks right in flight). */
	private static ItemStack modelled(Material material, String model)
	{
		ItemStack stack = new ItemStack(material);
		org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
		meta.setItemModel(new org.bukkit.NamespacedKey("mzguns", model));
		stack.setItemMeta(meta);
		return stack;
	}

	private static void throwGrenade(Game game, Player player, ItemStack handStack)
	{
		// Only goes off in the match it was thrown in (the timers used to carry on into the next one)
		final long match = game.getMatchGeneration();
		final Item item = player.getWorld().dropItemNaturally(player.getEyeLocation(), modelled(Material.SLIME_BALL, "frag_grenade"));
		handStack.setAmount(handStack.getAmount() - 1);
		player.getWorld().playSound(player.getLocation(), "mzguns:grenade.pin", org.bukkit.SoundCategory.PLAYERS, 1f, 1f);
		item.setVelocity(player.getLocation().getDirection().multiply(1));
		item.setPickupDelay(1000);

		int smoke = COMZombies.scheduleTask(0, 5, () ->
		{
			if(item.isValid())
				item.getWorld().spawnParticle(Particle.SMOKE, item.getLocation().clone(), 0, COMZombies.rand.nextDouble() - 0.5, 0.5, COMZombies.rand.nextDouble() - 0.5, 0.05);
		});
		COMZombies.scheduleTask(100, () ->
		{
			Bukkit.getScheduler().cancelTask(smoke);
			boolean live = game.getStatus() == GameStatus.INGAME && game.getMatchGeneration() == match && item.isValid();
			if(live)
			{
				Location loc = item.getLocation();
				item.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), 0.0F, false, false);
				List<Mob> ents = game.spawnManager.getEntities();
				for(int i = ents.size() - 1; i >= 0; i--)
				{
					Mob mob = ents.get(i);
					if(!mob.getWorld().equals(item.getWorld()))
						continue;
					float dist = (float) mob.getLocation().distance(item.getLocation());
					if(dist < 5)
					{
						float damage = 50f / Math.max(0.05f, dist * dist * dist);
						double before = mob.getHealth();
						// Gore flies away from the blast
						org.bukkit.util.Vector center = mob.getLocation().add(0, mob.getHeight() * 0.5, 0).toVector();
						org.bukkit.util.Vector away = center.clone().subtract(item.getLocation().toVector());
						com.theprogrammingturkey.comz.util.BloodMessages.nextHit(center,
								away.lengthSquared() > 1.0E-4 ? away.normalize() : new org.bukkit.util.Vector(0, 1, 0),
								com.theprogrammingturkey.comz.util.BloodMessages.OTHER);
						game.damageMob(mob, player, damage);
						// Survived the blast: it may have lost its legs
						com.theprogrammingturkey.comz.game.Crawlers.afterBlast(mob, damage, before);
					}
				}
			}
			item.remove();
		});
	}

	@EventHandler
	public void onGrenade(PlayerInteractEvent event)
	{
		if(!event.getAction().equals(Action.RIGHT_CLICK_AIR))
			return;

		if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && BlockUtils.isSign(event.getClickedBlock().getType()))
			return;

		final Player player = event.getPlayer();
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			Game game = GameManager.INSTANCE.getGame(player);
			if(game.getStatus() != GameStatus.INGAME)
				return;
			// Downed players cannot throw grenades or monkey bombs
			if(game.downedPlayerManager.isDownedPlayer(player))
				return;
			// Only goes off in the match it was thrown in (the timers used to carry on into the next one)
			final long match = game.getMatchGeneration();

			ItemStack handStack = player.getInventory().getItemInMainHand();
			if(com.theprogrammingturkey.comz.game.features.PerkDrink.isDrinking(player))
				return;
			if(handStack.getType().equals(Material.SLIME_BALL))
				throwGrenade(game, player, handStack);
			else if(handStack.getType().equals(Material.MAGMA_CREAM))
				throwMonkey(game, player, handStack);
		}
	}

	private static void throwMonkey(Game game, Player player, ItemStack handStack)
	{
		final long match = game.getMatchGeneration();
		{
			{
				final Item item = player.getWorld().dropItemNaturally(player.getEyeLocation(), modelled(Material.MAGMA_CREAM, "monkey_bomb"));
				handStack.setAmount(handStack.getAmount() - 1);
				item.setVelocity(player.getLocation().getDirection().multiply(1));
				item.setPickupDelay(1000);

				ArmorStand attackEnt = (ArmorStand) player.getWorld().spawnEntity(item.getLocation().clone(), EntityType.ARMOR_STAND);
				attackEnt.setVisible(false);
				attackEnt.setGravity(false);
				attackEnt.setAI(false);
				item.addPassenger(attackEnt);

				for(Mob e : game.spawnManager.getEntities())
					e.setTarget(attackEnt);

				int ticker = COMZombies.scheduleTask(0, 5, () ->
				{
					if(game.getStatus() != GameStatus.INGAME || game.getMatchGeneration() != match || !item.isValid())
						return;
					item.getWorld().spawnParticle(Particle.SMOKE, item.getLocation().clone(), 0, COMZombies.rand.nextDouble() - 0.5, 0.5, COMZombies.rand.nextDouble() - 0.5, 0.05);
					// the monkey's cymbals
					item.getWorld().playSound(item.getLocation(), "mzguns:monkey.cymbal", org.bukkit.SoundCategory.PLAYERS, 1.2f,
							0.9f + COMZombies.rand.nextFloat() * 0.2f);
					// Zombies already after the bomb are left alone (re-targeting every mob every time was wasted work)
					for(Mob e : game.spawnManager.getEntities())
						if(e.getTarget() != attackEnt)
							e.setTarget(attackEnt);
				});

				COMZombies.scheduleTask(140, () ->
				{
					Bukkit.getScheduler().cancelTask(ticker);
					if(game.getStatus() == GameStatus.INGAME && game.getMatchGeneration() == match && item.isValid())
					{
						Location loc = item.getLocation();
						item.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), 0.0F, false, false);
						List<Mob> ents = game.spawnManager.getEntities();
						for(int i = ents.size() - 1; i >= 0; i--)
						{
							Mob mob = ents.get(i);
							if(!mob.getWorld().equals(item.getWorld()))
								continue;
							float dist = (float) mob.getLocation().distance(item.getLocation());
							if(dist < 5)
								game.damageMob(mob, player, 50f / Math.max(0.05f, dist * dist * dist));
						}
					}
					item.remove();
					attackEnt.remove();
				});
			}
		}
	}
}


