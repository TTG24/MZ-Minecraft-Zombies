package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.DownedPlayer;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

public class PlayerListener implements Listener
{
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		for(Player pl : Bukkit.getOnlinePlayers())
		{
			if(GameManager.INSTANCE.isPlayerInGame(pl))
				pl.hidePlayer(COMZombies.getPlugin(), player);
			else
				pl.showPlayer(COMZombies.getPlugin(), player);
			player.showPlayer(COMZombies.getPlugin(), pl);
		}

		// A player who quit mid-game, crashed, or was online during a server stop can still be standing in an arena world
		// with their game inventory. Unless they are genuinely still in a game, put their things back and send them home.
		Bukkit.getScheduler().runTaskLater(COMZombies.getPlugin(), () -> sendStuckPlayerHome(player), 2L);

		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null)
			return;
		if(game.wasDisconnected(player))
		{
			long left = game.rejoinMillisLeft(player);
			if(left > 0)
				CommandUtil.sendClickableMessageToPlayer(player, Colors.GRAY + "Your match in " + Colors.RED + game.getName() + Colors.GRAY
						+ " is still going. Rejoin within " + Colors.RED + com.theprogrammingturkey.comz.game.Game.formatMillis(left)
						+ Colors.GRAY + " with /z rejoin or ", "CLICK HERE", "/z rejoin");
			else if(game.getStatus() == com.theprogrammingturkey.comz.game.Game.GameStatus.INGAME)
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Your match in " + Colors.RED + game.getName() + Colors.GRAY
						+ " is still going, but it has been more than 5 minutes since you left, so you can't rejoin it.");
		}
	}

	private void sendStuckPlayerHome(Player player)
	{
		if(!player.isOnline() || GameManager.INSTANCE.isPlayerInGame(player))
			return;
		if(player.isInvulnerable() && player.getGameMode() == GameMode.SURVIVAL)
			player.setInvulnerable(false); // stuck from being downed when the match ended or they quit
		if(com.theprogrammingturkey.comz.game.CachedPlayerInfo.hasSavedInfo(player.getUniqueId()))
			com.theprogrammingturkey.comz.game.CachedPlayerInfo.restorePlayerInfoNow(player);
		if(com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.isInstanceWorld(player.getWorld()))
			player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event)
	{
		com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.cancelQueue(event.getPlayer().getUniqueId());
		Player player = event.getPlayer();
		com.theprogrammingturkey.comz.game.CachedPlayerInfo.setQuitting(player.getUniqueId(), true);
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			Game game = GameManager.INSTANCE.getGame(player);
			game.removePlayer(player);
			player.removePotionEffect(PotionEffectType.BLINDNESS);
			player.removePotionEffect(PotionEffectType.SLOWNESS);
		}
		// Also covers post-match spectators (no longer in the game's player map): whatever state they are in, they leave
		// the arena immediately, so the location saved with their player data is outside it.
		if(com.theprogrammingturkey.comz.game.CachedPlayerInfo.hasSavedInfo(player.getUniqueId()))
			com.theprogrammingturkey.comz.game.CachedPlayerInfo.restorePlayerInfoNow(player);
		if(com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.isInstanceWorld(player.getWorld()))
			player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
		com.theprogrammingturkey.comz.game.CachedPlayerInfo.setQuitting(player.getUniqueId(), false);
		for(Player pl : Bukkit.getOnlinePlayers())
		{
			player.showPlayer(COMZombies.getPlugin(), pl);
			pl.showPlayer(COMZombies.getPlugin(), player);
		}
	}

	/**
	 * Checks if the player is leaving the arena and takes care of his action.
	 */
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		Game game = GameManager.INSTANCE.getGame(player);
		Location toLoc = event.getTo();
		// getGame also finds a game the player has already LEFT (it is kept for /z rejoin). Such a player is no longer
		// bound to the arena, or their way home would be undone by the "do not leave the arena" teleport below.
		if(game == null || toLoc == null || !GameManager.INSTANCE.isPlayerInGame(player))
			return;

		if(!game.arena.containsBlock(toLoc))
		{
			if(game.getStatus() == Game.GameStatus.INGAME)
			{
				player.teleport(game.getPlayerWarp(player));
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Please do not leave the arena!");
			}
		}


		// Reviving move check
		Location change = toLoc.clone().subtract(event.getFrom().clone());
		if(player.getGameMode() != GameMode.SPECTATOR && game.downedPlayerManager.isDownedPlayer(player) && change.getY() > 0.6)
			event.setCancelled(true);

		float DEAD_ZONE = 0.001f;

		// Reviving no longer needs the reviver to stand still: DownedPlayer watches the distance instead.


		//Barriers move check
		for(Barrier barrier : game.barrierManager.getBarriers())
		{
			for(Block b : barrier.getBlocks())
			{
				if(toLoc.getBlockX() == b.getX() && toLoc.getBlockY() == b.getY() && toLoc.getBlockZ() == b.getZ())
				{
					// Already standing in this block (pushed or dropped into it): let them walk out instead of trapping them.
					Location from = event.getFrom();
					if(from.getBlockX() == b.getX() && from.getBlockY() == b.getY() && from.getBlockZ() == b.getZ())
						continue;
					// A window of bars/panes/fences/walls that is still up stops players by its own (thin) collision: let them
					// stand right up against it instead of being held off the whole block. (Broken = air: blocked as before.)
					if(isThinWindow(b))
						continue;
					event.setCancelled(true);
					return;
				}
			}
		}
	}

	/** Iron bars, glass panes, fences or walls: thin blocks that block players by themselves. Same list as BlockBlocker's. */
	public static boolean isThinWindow(Block b)
	{
		if(b.getType().isAir() || b.isPassable())
			return false;
		String n = b.getType().name();
		return n.endsWith("BARS") || n.endsWith("GLASS_PANE") || n.endsWith("FENCE") || n.endsWith("_WALL");
	}

	/** Players in a fall long enough for PHD Flopper (it goes off when they land). */
	private final java.util.Set<java.util.UUID> phdFalling = new java.util.HashSet<>();

	@EventHandler
	public void phdQuit(org.bukkit.event.player.PlayerQuitEvent event)
	{
		phdFalling.remove(event.getPlayer().getUniqueId());
		// An admin who logs off mid-setup: the setup is cancelled (its particle loop and half-made arena used to stay)
		com.theprogrammingturkey.comz.COMZombies plugin = com.theprogrammingturkey.comz.COMZombies.getPlugin();
		com.theprogrammingturkey.comz.game.actions.BaseAction action = plugin.activeActions.remove(event.getPlayer());
		if(action != null)
			action.cancelAction();
		plugin.isEditingASign.remove(event.getPlayer());
		com.theprogrammingturkey.comz.listeners.OnBlockInteractEvent.forget(event.getPlayer());
		// Points belong to a match: a player who logs off is no longer in it
		if(!GameManager.INSTANCE.isPlayerInGame(event.getPlayer()))
			com.theprogrammingturkey.comz.economy.PointManager.INSTANCE.unloadPlayer(event.getPlayer());
	}

	@EventHandler
	public void OnPlayerVelocityEvent(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		if(!GameManager.INSTANCE.isPlayerInGame(player))
		{
			phdFalling.remove(player.getUniqueId());
			return;
		}
		{
			player.setFoodLevel(20);
			// PHD Flopper goes off once, when a fall of more than 2 blocks lands (it used to go off on every move of the fall)
			if(!player.isOnGround())
			{
				if(player.getFallDistance() > 2)
					phdFalling.add(player.getUniqueId());
				return;
			}
			if(phdFalling.remove(player.getUniqueId()))
			{
				Game game = GameManager.INSTANCE.getGame(player);
				if(game.perkManager.getPlayersPerks(player).contains(PerkType.PHD_FLOPPER))
				{
					double pHealth = player.getHealth();
					Location loc = player.getLocation();
					loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
					for(int i = 0; i < 30; i++)
					{
						float x = COMZombies.rand.nextFloat(2f);
						float y = COMZombies.rand.nextFloat(2f);
						float z = COMZombies.rand.nextFloat(2f);
						player.getWorld().spawnParticle(Particle.LAVA, loc.getX(), loc.getY(), loc.getZ(), 1, x, y, z, 1);
						player.getWorld().spawnParticle(Particle.FIREWORK, loc.getX(), loc.getY(), loc.getZ(), 1, x, y, z, 1);
					}

					// Only this match's zombies
					for(Entity e : player.getNearbyEntities(5, 5, 5))
						if(e instanceof Mob mob && game.spawnManager.isEntitySpawned(mob))
							game.damageMob(mob, player, 12);

					player.setHealth(pHealth);
				}
			}
		}
	}

	@EventHandler
	public void ProjectileHit(EntityDamageEvent event)
	{
		if(event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
		{
			if(event.getEntity() instanceof Player)
			{
				Player player = (Player) event.getEntity();
				if(GameManager.INSTANCE.isPlayerInGame(player))
					event.setCancelled(true);
			}
		}
		else if(event.getCause() == EntityDamageEvent.DamageCause.FALL)
		{
			if(event.getEntity() instanceof Player)
			{
				Player player = (Player) event.getEntity();
				if(GameManager.INSTANCE.isPlayerInGame(player))
					if(GameManager.INSTANCE.getGame(player).perkManager.hasPerk(player, PerkType.PHD_FLOPPER))
						event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void playerExp(PlayerExpChangeEvent event)
	{
		Player player = event.getPlayer();
		if(GameManager.INSTANCE.isPlayerInGame(player))
			player.setExp(0);
	}

	/**
	 * Pressing F (swap hands) next to a downed teammate starts reviving them. The reviver may keep moving, but must stay
	 * within the revive range; DownedPlayer cancels the revive if they drift out of it.
	 */
	@EventHandler
	public void reviveWithF(org.bukkit.event.player.PlayerSwapHandItemsEvent event)
	{
		Player reviver = event.getPlayer();
		if(!GameManager.INSTANCE.isPlayerInGame(reviver))
			return;
		Game game = GameManager.INSTANCE.getGame(reviver);
		if(game == null || game.downedPlayerManager.isDownedPlayer(reviver) || !game.getPlayersInGame().contains(reviver))
			return;

		double range = ConfigManager.getMainConfig().reviveRange;
		DownedPlayer best = null;
		double bestDistance = Double.MAX_VALUE;
		for(org.bukkit.entity.Entity nearby : reviver.getNearbyEntities(range, range, range))
		{
			if(!(nearby instanceof Player other) || !game.isPlayerPlaying(other))
				continue;
			DownedPlayer downed = game.downedPlayerManager.getDownedPlayer(other);
			if(downed == null || downed.isBeingRevived() || !other.getWorld().equals(reviver.getWorld()))
				continue;
			double distance = reviver.getLocation().distance(other.getLocation());
			if(distance <= range && distance < bestDistance)
			{
				best = downed;
				bestDistance = distance;
			}
		}
		if(best == null)
			return;

		Player downedPlayer = best.getPlayer();
		reviver.sendMessage(Colors.GRAY + "You are reviving " + downedPlayer.getName() + ". Stay close!");
		downedPlayer.sendMessage(Colors.GRAY + "You are being revived by " + reviver.getName() + "!");
		best.startRevive(reviver);
		event.setCancelled(true);
	}
}
