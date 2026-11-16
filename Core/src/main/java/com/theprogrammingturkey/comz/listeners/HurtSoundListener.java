package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.ApiCompat;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plays the hurt sound (mz:hurt, one of four clips) when a player in a game takes a hit that leaves them very low on health.
 * Everyone near hears it. config.gameSettings.hurtSoundPercent is the health left, in percent of their maximum, at or below
 * which it plays (0 turns it off).
 */
public class HurtSoundListener implements Listener
{
	private static final long COOLDOWN_MS = 1500;
	private final Map<UUID, Long> last = new HashMap<>();

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event)
	{
		if(!(event.getEntity() instanceof Player player) || !GameManager.INSTANCE.isPlayerInGame(player))
			return;
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null || game.getStatus() != Game.GameStatus.INGAME || game.downedPlayerManager.isDownedPlayer(player))
			return;
		if(event.getFinalDamage() <= 0)
			return;

		double percent = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.hurtSoundPercent", 30);
		if(percent <= 0)
			return;
		AttributeInstance max = player.getAttribute(ApiCompat.MAX_HEALTH);
		double maxHealth = max != null ? max.getValue() : 20;
		double left = player.getHealth() - event.getFinalDamage();
		if(left <= 0 || left > maxHealth * percent / 100.0)
			return;

		long now = System.currentTimeMillis();
		if(now - last.getOrDefault(player.getUniqueId(), 0L) < COOLDOWN_MS)
			return;
		last.put(player.getUniqueId(), now);
		player.getWorld().playSound(player.getLocation(), "mz:hurt", SoundCategory.PLAYERS, 1f, 1f);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event)
	{
		last.remove(event.getPlayer().getUniqueId());
	}
}
