package com.theprogrammingturkey.comz.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.destroystokyo.paper.event.entity.EntityJumpEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.Colors;

/**
 * Sprint stamina for active matches.
 *
 * <p>Per player the mechanic is a small state machine:
 * <ul>
 *   <li><b>Ready / sprinting</b> — a 0..1 stamina bar drains at a rate that empties it in
 *       {@code staminaMaxSeconds} of continuous sprinting. The bar is drawn as the
 *       <i>saturation</i> overlay on the hunger bar.</li>
 *   <li><b>Exhausted</b> — the moment the bar empties the player is forced out of sprint and
 *       locked out for {@code staminaRegenSeconds}; every sprint attempt during this window
 *       (including just holding the sprint key) is cancelled. The saturation overlay shows the
 *       recovery filling back up, and once full the player can sprint again.</li>
 * </ul>
 *
 * <p>Knobs (all in {@code config.gameSettings}):
 * <ul>
 *   <li>{@code staminaEnabled} — master on/off.</li>
 *   <li>{@code staminaMaxSeconds} — full-bar sprint duration before exhaustion (default 5).</li>
 *   <li>{@code staminaRegenSeconds} — exhaustion lockout length before sprinting is allowed again (default 3).</li>
 * </ul>
 *
 * <p>Only applies to players in a game whose status is {@code INGAME}.
 */
public class StaminaListener implements Listener
{
	/**
	 * Per-player stamina state.
	 */
	private static final class State
	{
		/** 0..1 stamina remaining while ready/sprinting. */
		double stamina = 1.0;
		/** True while the post-exhaustion lockout is active. */
		boolean exhausted = false;
		/** Ticks left in the current lockout. */
		int exhaustedTicksRemaining = 0;
		/** Total ticks the current lockout should last (set when exhaustion begins). */
		int exhaustedTicksTotal = 1;
	}

	private final Map<UUID, State> states = new HashMap<>();
	/**
	 * Throttle for the "out of stamina" chat reminder, ms since epoch per player.
	 */
	private final Map<UUID, Long> lastMsg = new HashMap<>();
	private static final long MSG_THROTTLE_MS = 1000L;

	public StaminaListener()
	{
		// 20 Hz tick so the drain and the saturation overlay both look smooth.
		COMZombies.scheduleTask(1, 1, this::tick);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e)
	{
		states.remove(e.getPlayer().getUniqueId());
		lastMsg.remove(e.getPlayer().getUniqueId());
	}

	/**
	 * Whether the given player is currently in the post-exhaustion sprint lockout.
	 *
	 * @param p the player to check.
	 * @return true if the player is exhausted and locked out of sprinting.
	 */
	public boolean isExhausted(Player p)
	{
		State st = states.get(p.getUniqueId());
		return st != null && st.exhausted;
	}

	/**
	 * Advances every active player's stamina state machine and pushes the bar to the
	 * saturation overlay each tick.
	 */
	private void tick()
	{
		if(!ConfigManager.getMainConfig().staminaEnabled)
		{
			states.clear();
			return;
		}
		double maxSeconds = Math.max(0.5, ConfigManager.getMainConfig().staminaMaxSeconds);
		double regenSeconds = Math.max(0.5, ConfigManager.getMainConfig().staminaRegenSeconds);
		int regenTicks = Math.max(1, (int) (regenSeconds * 20.0));
		double perTickDrain = 0.05 / maxSeconds; // 1 tick = 0.05 s

		for(Player p : COMZombies.getPlugin().getServer().getOnlinePlayers())
		{
			if(!p.isOnline() || !inActiveGame(p))
				continue;
			State st = states.computeIfAbsent(p.getUniqueId(), k -> new State());

			if(st.exhausted)
			{
				st.exhaustedTicksRemaining--;
				// While the sprint key is held the client re-sends sprint=true in every
				// movement packet and moves at sprint speed regardless of server state, so
				// server-side setSprinting(false) alone can't stop it. The Slowness effect
				// (applied at exhaustion) physically caps the client's speed for the window.
				if(p.isSprinting())
					p.setSprinting(false);
				// The jump event only stops the server from applying velocity; the client
				// already applied its own local-prediction velocity before we could cancel.
				// Every tick, zero any positive Y velocity so a mid-lockout jump can't
				// carry the player airborne. Downward velocity is left alone so they can
				// still land naturally if they were already airborne when exhaustion hit.
				Vector vel = p.getVelocity();
				if(vel.getY() > 0)
					p.setVelocity(new Vector(vel.getX(), 0.0, vel.getZ()));
				if(st.exhaustedTicksRemaining <= 0)
				{
					st.exhausted = false;
					st.stamina = 1.0;
					// Lockout over: drop the Slowness and re-enable sprint.
					p.removePotionEffect(PotionEffectType.SLOWNESS);
					p.setSprinting(true);
				}
			}
			else if(p.isSprinting())
			{
				st.stamina -= perTickDrain;
				if(st.stamina <= 0)
				{
					st.stamina = 0;
					st.exhausted = true;
					st.exhaustedTicksTotal = regenTicks;
					st.exhaustedTicksRemaining = regenTicks;
					p.setSprinting(false);
					// Physically slow the client for the whole lockout so holding the
					// sprint key can't keep up sprint speed. Ambient + no particles/icon
					// keeps it subtle; it's still visible as the "out of stamina" tell.
					int dur = Math.max(regenTicks, 1);
					p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 4, true, false, false));
					notifyOutOfStamina(p);
				}
			}

			// Draw the bar as saturation (0..20). While exhausted, show the recovery
			// filling back up; otherwise show the current stamina.
			double fraction;
			if(st.exhausted)
				fraction = 1.0 - (Math.max(0, st.exhaustedTicksRemaining) / (double) st.exhaustedTicksTotal);
			else
				fraction = st.stamina;
			p.setSaturation((float) (Math.max(0, Math.min(1, fraction)) * 20.0));
		}
	}

	/**
	 * While a player is exhausted, block any sprint attempt (this is what stops the
	 * "just keep holding the sprint key" bypass) and nudge them once per second.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onToggleSprint(PlayerToggleSprintEvent e)
	{
		if(!e.isSprinting())
			return;
		if(!ConfigManager.getMainConfig().staminaEnabled)
			return;
		Player p = e.getPlayer();
		if(!inActiveGame(p))
			return;
		State st = states.get(p.getUniqueId());
		if(st == null || !st.exhausted)
			return;
		e.setCancelled(true);
		p.setSprinting(false);
		long now = System.currentTimeMillis();
		Long last = lastMsg.get(p.getUniqueId());
		if(last == null || now - last >= MSG_THROTTLE_MS)
		{
			p.sendMessage(Colors.YELLOW + "Out of stamina — wait for the bar to refill!");
			lastMsg.put(p.getUniqueId(), now);
		}
	}

	/**
	 * Slowness only applies on the ground; a player who jumps during the lockout can clear
	 * the Slowness window in mid-air. Cancel the jump while exhausted so the lockout stays
	 * in effect (modern Paper removed the JUMP_RESISTANCE potion effect, so the event is
	 * the client-proof way to stop the jump).
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onJump(EntityJumpEvent e)
	{
		if(!ConfigManager.getMainConfig().staminaEnabled)
			return;
		if(!(e.getEntity() instanceof Player p))
			return;
		if(!inActiveGame(p))
			return;
		State st = states.get(p.getUniqueId());
		if(st == null || !st.exhausted)
			return;
		e.setCancelled(true);
		// Also suppress the server-side sprint flag so the jump can't carry a sprint speed.
		if(p.isSprinting())
			p.setSprinting(false);
	}

	private void notifyOutOfStamina(Player p)
	{
		long now = System.currentTimeMillis();
		Long last = lastMsg.get(p.getUniqueId());
		if(last == null || now - last >= MSG_THROTTLE_MS)
		{
			p.sendMessage(Colors.YELLOW + "Out of stamina — wait for the bar to refill!");
			lastMsg.put(p.getUniqueId(), now);
		}
	}

	/**
	 * True if {@code p} is a live (non-spectator) player in a game that is currently INGAME.
	 */
	private boolean inActiveGame(Player p)
	{
		for(Game g : GameManager.INSTANCE.getGames())
			if(g.getStatus() == Game.GameStatus.INGAME && g.isPlayerPlaying(p))
				return true;
		return false;
	}
}
