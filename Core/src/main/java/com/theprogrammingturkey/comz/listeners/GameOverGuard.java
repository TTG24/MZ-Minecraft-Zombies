package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.CachedPlayerInfo;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.logging.Level;

/**
 * Keeps players who are watching a match's ending (the game-over cutscene and board) inside that match's world until the
 * game sends them home itself. Anything else moving them out of it (another plugin, a vanilla respawn...) is stopped, and
 * logged with where it came from, along with any death of a player in a match.
 */
public class GameOverGuard implements Listener
{
	/** Set while the game itself sends a player home, so that teleport goes through. */
	public static boolean sendingHome;

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event)
	{
		Player player = event.getPlayer();
		Game game = GameManager.INSTANCE.getGameOverSpectatingGame(player);
		if(game == null || sendingHome || CachedPlayerInfo.isQuitting(player.getUniqueId()) || !COMZombies.getPlugin().isEnabled())
			return;
		World arena = game.getWorld();
		Location to = event.getTo();
		if(arena == null || to == null || to.getWorld() == null || to.getWorld().equals(arena))
			return;
		event.setCancelled(true);
		COMZombies.log.log(Level.WARNING, "[GameOverGuard] Stopped " + player.getName() + " being moved out of " + game.getName()
				+ " during its game-over (cause " + event.getCause() + ", to " + to.getWorld().getName() + "). Called from:", new Throwable("teleport"));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event)
	{
		Player player = event.getPlayer();
		boolean watching = GameManager.INSTANCE.getGameOverSpectatingGame(player) != null;
		if(watching || GameManager.INSTANCE.isPlayerInGame(player))
		{
			var last = player.getLastDamageCause();
			COMZombies.log.log(Level.WARNING, "[GameOverGuard] " + player.getName() + " died " + (watching ? "during a game-over" : "in a match")
					+ " (last damage: " + (last == null ? "unknown" : last.getCause() + " " + last.getFinalDamage()) + ", health before "
					+ player.getHealth() + ")");
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent event)
	{
		Game game = GameManager.INSTANCE.getGameOverSpectatingGame(event.getPlayer());
		if(game != null || GameManager.INSTANCE.isPlayerInGame(event.getPlayer()))
			COMZombies.log.log(Level.WARNING, "[GameOverGuard] " + event.getPlayer().getName() + " respawned at " + event.getRespawnLocation().getWorld().getName()
					+ " " + event.getRespawnLocation().toVector());
	}
}
