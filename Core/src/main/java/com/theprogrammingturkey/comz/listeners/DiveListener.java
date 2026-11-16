package com.theprogrammingturkey.comz.listeners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;

/**
 * Dolphin dive for active matches, modeled on the COD "dolphin dive":
 * <b>sneak + jump</b> thrusts the player forward in the direction they're facing
 * with a strong horizontal burst, then locks them into a low, committed posture
 * (forced crouch) for a short window — the "prone" slide — after which they
 * recover to normal. It is cooldown-gated and only applies on the ground.
 *
 * <p>Knobs (all in {@code config.gameSettings}, read live each dive):
 * <ul>
 *   <li>{@code diveEnabled} — master on/off (default true).</li>
 *   <li>{@code diveCooldownSeconds} — minimum wait between dives (default 2).</li>
 *   <li>{@code diveLungeStrength} — thrust velocity in blocks/tick (0.4 is a solid
 *       burst, 0.5 a long one, default 0.4).</li>
 *   <li>{@code diveDurationSeconds} — how long the player stays in the low "prone"
 *       posture after the thrust (default 1).</li>
 * </ul>
 *
 * <p>The dive is suppressed while the player is out of stamina (see
 * {@link StaminaListener#isExhausted(Player)}), so it can't be chained to skip the
 * sprint lockout, and it does not apply to spectators.
 */
public class DiveListener implements Listener
{
	/**
	 * Plugin logger, used to trace why a dive does (or does not) fire.
	 */
	private final java.util.logging.Logger log = COMZombies.getPlugin().getLogger();

	/**
	 * The stamina listener, consulted so an exhausted player can't dive.
	 */
	private final StaminaListener stamina;
	/**
	 * ms-since-epoch of the last dive per player, enforcing the cooldown.
	 */
	private final Map<UUID, Long> lastDive = new HashMap<>();
	/**
	 * ms-since-epoch at which each player began holding sneak (null/absent when not
	 * sneaking). Used to require a sustained crouch before a dive fires, so a
	 * transient or coincidental sneak can't turn a normal jump into a dive.
	 */
	private final Map<UUID, Long> sneakSince = new HashMap<>();
	/**
	 * Players currently holding their prone (dive) pose. The pose is <b>held</b> until
	 * the player taps sneak to stand back up; the tick task keeps these players in a
	 * low crawl (forced crouch, capped movement, no sprint).
	 */
	private final Set<UUID> pronePlayers = new HashSet<>();
	/**
	 * Players currently holding the deep, animated CROUCH stance (a distinct, lighter
	 * posture than the flat dive-prone). Entered via {@link #enterCrouch} (e.g. the
	 * {@code /zombies crouch} command), released via {@link #exitCrouch}. The tick
	 * task keeps them in a capped movement while they hold it.
	 */
	private final Set<UUID> crouchPlayers = new HashSet<>();
	/**
	 * The pre-prone base movement speed per player, saved so it can be restored when
	 * the player stands back up.
	 */
	private final Map<UUID, Double> preProneSpeed = new HashMap<>();
	/**
	 * How long (ms) a player must have been holding sneak before a jump counts as a
	 * dive. Short enough to feel instant, long enough to reject stray one-tick sneaks.
	 */
	private static final long SNEAK_HOLD_MS = 150L;
	/**
	 * Base movement-speed applied to prone players (a slow crawl). In 26.3 this is the
	 * {@code MOVEMENT_SPEED} attribute base value; a player's default is ~0.1, so
	 * 0.04 is a deliberate belly crawl. The player's previous base value is saved and restored
	 * when they stand back up.
	 */
	private static final double PRONE_CRAWL_SPEED = 0.04;
	/**
	 * Base movement-speed applied to players in the deep crouch stance. Deliberately
	 * FASTER than the prone belly-crawl ({@link #PRONE_CRAWL_SPEED} = 0.04): a crouch
	 * is a light, ready posture rather than a committed slide, so the player moves at a
	 * modest-but-slower-than-normal pace. A player's default {@code MOVEMENT_SPEED}
	 * base is ~0.1, so 0.06 is a noticeably slowed but still lively walk.
	 */
	private static final double CROUCH_SPEED = 0.06;
	/** Entry ramp (ticks) over which the client eases into the held prone pose. */
	private static final int PRONE_ENTRY_RAMP_TICKS = 8;
	/**
	 * Prefix of the hidden action-bar signal used to broadcast "enter / hold your
	 * prone dive pose" to the local client. The companion client mod (mz-prone)
	 * catches this message, arms its held prone pose, and suppresses it. An
	 * action-bar/system message is used (rather than a scoreboard objective) because
	 * scoreboard-manipulating plugins such as TAB install a personal scoreboard that
	 * strips hidden objectives before they reach the client. Players without the mod
	 * only ever see a brief, harmless action bar.
	 *
	 * <p>Full format: {@code mzprone:dive:<windowTicks>}.
	 */
	public static final String DIVE_MARKER_PREFIX = "mzprone:dive:";
	/**
	 * Prefix of the hidden action-bar signal used to broadcast "enter / hold your deep
	 * CROUCH stance" to the local client. The client mod (mz-prone) catches this, arms
	 * its animated crouch pose, and suppresses it. See {@link #DIVE_MARKER_PREFIX} for
	 * why an action-bar message is used.
	 *
	 * <p>Full format: {@code mzprone:crouch:<rampTicks>}.
	 */
	public static final String CROUCH_MARKER_PREFIX = "mzprone:crouch:";
	/**
	 * Prefix of the hidden action-bar signal used to broadcast "stand up / release the
	 * prone pose" to the local client. The companion client mod catches this, releases
	 * its prone pose, and suppresses it. See {@link #DIVE_MARKER_PREFIX} for why an
	 * action-bar message is used.
	 *
	 * <p>Full format: {@code mzprone:stand:<seq>}.
	 */
	public static final String STAND_MARKER_PREFIX = "mzprone:stand:";

	/**
	 * Creates a dive listener that defers to the given stamina listener for the
	 * exhaustion check.
	 *
	 * @param stamina the stamina listener to consult, or null to allow dives even
	 *        when out of stamina.
	 */
	/**
	 * The live dive/crouch listener instance, exposed so commands (e.g. {@code /zombies
	 * crouch}) can drive the crouch stance without needing a plugin reference.
	 */
	public static DiveListener INSTANCE;

	public DiveListener(StaminaListener stamina)
	{
		this.stamina = stamina;
		INSTANCE = this;
		// No per-tick task: the old dive/prone system is superseded by the Tactical Movement plugin.
	}

	/**
	 * The bullet/tracer origin follows the client camera height. The stance comes from the TacticalMovementPlugin
	 * (published as the "tacticalmovement_stance" player metadata: 0 standing, 1 prone, 2 diving, 3 crouching);
	 * the eye heights match the Tactical Movement client mod's hitboxes (prone 0.48, crouch 1.08).
	 */
	public org.bukkit.Location getWeaponOrigin(Player player)
	{
		org.bukkit.Location eyes = player.getEyeLocation();
		var meta = player.getMetadata("tacticalmovement_stance");
		int stance = meta.isEmpty() ? 0 : meta.get(0).asInt();
		double height;
		switch(stance)
		{
			case 1:
			case 2:
				height = 0.48;
				break;
			case 3:
				height = 1.08;
				break;
			default:
				return eyes;
		}
		eyes.setY(player.getLocation().getY() + height);
		return eyes;
	}

	@EventHandler
	public void onSneakToggle(PlayerToggleSneakEvent e)
	{
		UUID id = e.getPlayer().getUniqueId();
		if(e.isSneaking())
		{
			sneakSince.put(id, System.currentTimeMillis());
			// A prone player who PRESSES sneak again stands back up. We key on the
			// sneak-ON (a deliberate press/tap) rather than the de-sneak: after a dive the
			// player is already sneaking, so the natural "let go of Shift" is a de-sneak —
			// if we keyed stand-up on that it would cancel the pose the moment they
			// released, tying the pose to holding Shift. Keying on a press means releasing
			// Shift no longer cancels the prone pose, and the next Shift press stands up.
			if(pronePlayers.remove(id))
				exitProne(e.getPlayer());
			return;
		}
		sneakSince.remove(id);
		// A de-sneak never ends the prone pose — it is held until the next sneak press.
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e)
	{
		UUID id = e.getPlayer().getUniqueId();
		lastDive.remove(id);
		sneakSince.remove(id);
		preProneSpeed.remove(id);
		pronePlayers.remove(id);
		crouchPlayers.remove(id);
		// No flag to clear: the client pose is released by the "stand" marker, and the
		// action-bar signals are fire-and-forget.
	}

	/**
	 * Ends a player's prone pose: restore normal walk speed, release the forced crouch
	 * lock, and signal the client to stand back up.
	 *
	 * @param p the player to stand up, or null if already gone.
	 */
	private void exitProne(Player p)
	{
		if(p == null)
			return;
		UUID id = p.getUniqueId();
		// If the player is still in the deep crouch stance, the crouch cap (0.06)
		// should stay in force via tick(). Do not restore the full base speed here —
		// the player has NOT stood up yet; they merely released the *dive* and are
		// now in the (lighter) crouch posture.
		if(!crouchPlayers.contains(id))
		{
			// Restore the player's original base movement speed (saved on entry).
			AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
			Double original = preProneSpeed.remove(id);
			double restore = (original != null) ? original : 0.1;
			if(restore < 0.1)
				restore = 0.1;
			if(speed != null)
				speed.setBaseValue(restore);
		}
		p.setSprinting(false);
		p.setSneaking(false);
		if(stamina != null && !stamina.isExhausted(p))
			p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
		sendStandSignal(p);
		log.info("[mz-prone] PRONE ENDED for " + p.getName() + (crouchPlayers.contains(id) ? " (staying in crouch)" : " — restored speed"));
	}

	/**
	 * Saves a player's current base movement speed (if not already saved) and sets the
	 * slow crawl. Called once when the player enters the prone pose.
	 *
	 * @param p the player entering the prone pose.
	 */
	private void applyCrawl(Player p)
	{
		AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
		if(speed == null)
			return;
		UUID id = p.getUniqueId();
		// Save the current base as the "original" to restore, but only if it's a sane
		// (non-crawl) value. If it's already at crawl (e.g. a re-dive or a plugin reload
		// mid-prone), fall back to a standard base so exitProne never restores crawl.
		double current = speed.getBaseValue();
		Double saved = preProneSpeed.get(id);
		if(saved == null || saved < 0.1)
			saved = (current >= 0.1) ? current : 0.1;
		preProneSpeed.put(id, saved);
		speed.setBaseValue(PRONE_CRAWL_SPEED);
	}

	/**
	 * Enters a player's deep crouch stance: adds them to the held-crouch set (the tick
	 * task from then on governs their movement), and signals the local client mod to
	 * ease into its animated crouch pose.
	 *
	 * @param p the player to crouch.
	 */
	public void enterCrouch(Player p)
	{
		if(p == null)
			return;
		UUID id = p.getUniqueId();
		// Crouch and flat-dive-prone are mutually exclusive postures. Drop a held
		// dive-prone first so the crouch stance owns the pose on its own and the
		// movement cap lifts to the lighter crouch pace (the client does the same in
		// DiveState.armCrouch, which clears the held-dive flag). Just remove them from
		// pronePlayers — do NOT call exitProne (that would send a "stand" action-bar
		// signal and restore full speed, which is wrong: the player is moving to
		// CROUCH, not standing up). The crouch marker that follows replaces the dive
		// marker client-side, and applyCrouch below sets the correct lighter pace.
		pronePlayers.remove(id);
		if(crouchPlayers.contains(id))
		{
			// Already crouched (a refresh): still normalise the sneak flag so the
			// CROUCH stance never carries a stale vanilla-crouch, then bail.
			p.setSneaking(false);
			return;
		}
		crouchPlayers.add(id);
		// CROUCH stance target: NOT sneaking. Set explicitly (rather than relying on
		// exitProne's side-effect) so PRONE→CROUCH, STANDING→CROUCH, and CROUCH
		// refresh all converge to a deterministic sneak state.
		p.setSneaking(false);
		applyCrouch(p);
		sendCrouchSignal(p, PRONE_ENTRY_RAMP_TICKS);
		log.info("[mz-prone] CROUCH ENTERED for " + p.getName() + " — speed " + CROUCH_SPEED);
	}

	/**
	 * Enters a player's held flat-dive-PRONE pose WITHOUT the horizontal lunge.
	 * Used on the "hold C 2 seconds" transition from STANDING or CROUCH to PRONE.
	 * <p>
	 * This mirrors the dive-entry path ({@link #onJump}) but skips the velocity
	 * burst — the player settles into the same held prone posture, just in place
	 * rather than sliding forward. Mutually exclusive with the crouch stance: a
	 * held crouch is dropped first (exclusivity) so the flat-prone pose owns the
	 * local player on the client.
	 *
	 * @param p the player to enter the flat prone pose; null is ignored.
	 */
	public void enterProneNoLunge(Player p)
	{
		if(p == null)
			return;
		UUID id = p.getUniqueId();
		// Drop a held crouch first: the flat-prone pose is the stricter, slower
		// posture and must own the pose exclusively (client does the same in
		// DiveState.arm).
		if(crouchPlayers.remove(id))
			applyCrawl(p); // ensure speed is at the prone cap; entry ramp below sends
		// the dive marker to replace the crouch marker client-side.
		if(pronePlayers.contains(id))
		{
			// Already prone (a refresh): re-apply the cap and re-arm the pose so
			// any in-progress stand-up exit is cancelled, then bail.
			applyCrawl(p);
			if(!p.isSneaking())
				p.setSneaking(true);
			sendDiveSignal(p, PRONE_ENTRY_RAMP_TICKS);
			log.info("[mz-prone] PRONE REFRESH (no lunge) for " + p.getName());
			return;
		}
		pronePlayers.add(id);
		if(!p.isSneaking())
			p.setSneaking(true);
		applyCrawl(p);
		sendDiveSignal(p, PRONE_ENTRY_RAMP_TICKS);
		log.info("[mz-prone] PRONE ENTERED (no lunge) for " + p.getName() + " — speed " + PRONE_CRAWL_SPEED);
	}

	/**
	 * @return true if the given player is currently holding the deep crouch stance.
	 */
	public boolean isCrouched(UUID id)
	{
		return id != null && crouchPlayers.contains(id);
	}

	/**
	 * Exits a player's deep crouch stance: removes them from the held set, restores
	 * normal movement unless they are still prone, and signals the client to stand up.
	 *
	 * @param p the player to release.
	 */
	public void exitCrouch(Player p)
	{
		if(p == null)
			return;
		UUID id = p.getUniqueId();
		if(!crouchPlayers.remove(id))
			return;
		if(pronePlayers.contains(id))
			// Still prone: keep the slower prone cap in place.
			return;
		// Restore normal base speed (guard against a stale saved value).
		AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
		if(speed != null && speed.getBaseValue() < 0.1)
		{
			Double saved = preProneSpeed.get(id);
			double restore = (saved != null) ? saved : 0.1;
			if(restore < 0.1)
				restore = 0.1;
			speed.setBaseValue(restore);
		}
		// If we were the last held pose, clean up the saved speed.
		if(!pronePlayers.contains(id))
			preProneSpeed.remove(id);
		// STANDING stance target: not sneaking. Set explicitly so leaving CROUCH
		// never carries a stale vanilla-crouch into the standing state.
		p.setSneaking(false);
		p.setSprinting(false);
		sendStandSignal(p);
		log.info("[mz-prone] CROUCH EXITED for " + p.getName());
	}

	/**
	 * Saves a player's current base movement speed (if not already saved) and sets the
	 * crouch pace. Faster than the belly crawl because a crouch is a lighter posture.
	 *
	 * @param p the player entering the deep crouch.
	 */
	private void applyCrouch(Player p)
	{
		AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
		if(speed == null)
			return;
		UUID id = p.getUniqueId();
		// If a *prone* crawl save is already recorded (we just entered the crouch
		// from a dive transition, or the player is in both poses), leave it alone
		// so exitProne / exitCrouch can restore it later. Otherwise, save the
		// current base so it can be restored when the player leaves the crouch.
		if(preProneSpeed.get(id) == null)
		{
			double current = speed.getBaseValue();
			preProneSpeed.put(id, (current >= 0.1) ? current : 0.1);
		}
		speed.setBaseValue(CROUCH_SPEED);
	}

	/**
	 * Signals the local client mod to enter / hold its deep crouch pose via the hidden
	 * action-bar marker.
	 *
	 * @param p the player to signal.
	 * @param rampTicks the entry ramp length in ticks over which the pose eases in.
	 */
	private static void sendCrouchSignal(Player p, int rampTicks)
	{
		if(p == null)
			return;
		p.sendActionBar(CROUCH_MARKER_PREFIX + Math.max(1, rampTicks));
	}

	/**
	 * Signals the start of a dive to the local client mod by sending the hidden
	 * action-bar marker. The client catches this, arms its dive window for
	 * {@code windowTicks}, and suppresses the message. The client window is
	 * self-expiring, so there is nothing for the server to clear afterwards.
	 *
	 * @param p the player to signal.
	 * @param windowTicks the dive window length in ticks (used for the client progress).
	 */
	private static void sendDiveSignal(Player p, int windowTicks)
	{
		if(p == null)
			return;
		int value = Math.max(1, windowTicks);
		p.sendActionBar(DIVE_MARKER_PREFIX + value);
	}

	/**
	 * Signals the local client mod to release the held prone pose (stand back up).
	 *
	 * @param p the player to signal.
	 */
	private static void sendStandSignal(Player p)
	{
		if(p == null)
			return;
		p.sendActionBar(STAND_MARKER_PREFIX + "1");
	}

	/**
	 * Holds each prone / crouch player in their capped movement every tick.
	 *
	 * <ul>
	 *   <li>Prone: the flat belly-crawl {@link #PRONE_CRAWL_SPEED} (slower).</li>
	 *   <li>Crouch: the deeper-but-livelier {@link #CROUCH_SPEED}.</li>
	 * </ul>
	 *
	 * <p>When a player is in BOTH sets, the slower (prone) cap wins; the tick simply
	 * applies the lower of the two values, so switching between them never lifts a
	 * player faster than their most restrictive current pose.
	 *
	 * <p>We deliberately do NOT force the crouch flag here: the server must be able to
	 * observe the player's own sneak state so that a sneak-tap (de-sneak) releases the
	 * dive-pose. Force-sneaking every tick would mask the player's de-sneak toggle and
	 * make it impossible to stand back up. The visual pose is driven entirely by the
	 * client mod, so the server only needs to govern movement.
	 */
	private void tick()
	{
		// Snapshot the held set first so removals during the loop can't break iteration.
		Set<UUID> held = new HashSet<>();
		held.addAll(pronePlayers);
		held.addAll(crouchPlayers);
		for(UUID id : held)
		{
			Player p = COMZombies.getPlugin().getServer().getPlayer(id);
			if(p == null || !p.isOnline())
			{
				pronePlayers.remove(id);
				crouchPlayers.remove(id);
				continue;
			}
			if(p.isSprinting())
				p.setSprinting(false);
			// The stricter (slower) cap wins when a player holds both poses.
			double target = 0.1;
			if(pronePlayers.contains(id))
				target = Math.min(target, PRONE_CRAWL_SPEED);
			if(crouchPlayers.contains(id))
				target = Math.min(target, CROUCH_SPEED);
			AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
			if(speed != null && speed.getBaseValue() > target + 0.0001)
				speed.setBaseValue(target);
		}
	}

	/**
	 * Converts a crouch+jump into a forward thrust plus a brief locked "prone" posture.
	 *
	 * <p>The jump itself is <b>not</b> cancelled: modern Paper applies jump velocity
	 * client-side (local prediction), so cancelling it makes the server yank the player
	 * back to the pre-jump position — a rubber-band glitch. Instead we keep the small
	 * hop and simply add a horizontal burst on top of it, which reads as a jump-thrust.
	 *
	 * @param e the player jump event, used only to gate the dive; the jump is left to
	 *        complete.
	 */
	@EventHandler
	public void onJump(PlayerJumpEvent e)
	{
		Player p = e.getPlayer();
		// While already holding the prone pose, a jump is a "stand up" request, not a
		// new dive. This is the event that fires for players here (proven: it is the
		// event the dive above uses), so we cancel the jump on the same event and run
		// the stand-up path. A cancelled PlayerJumpEvent does not fire an
		// EntityJumpEvent, so the player cannot hop while prone.
		if(pronePlayers.contains(p.getUniqueId()))
		{
			e.setCancelled(true);
			p.setSprinting(false);
			// Remove the player from the held-prone set BEFORE running the stand-up
			// path, the same way onSneakToggle does: exitProne itself does not remove
			// the id, and the per-tick task keeps re-applying crawl speed to anyone
			// still in pronePlayers — leaving the id in the set would freeze the
			// player at crawl speed and keep the jump cancelled on future presses.
			if(pronePlayers.remove(p.getUniqueId()))
				exitProne(p);
			return;
		}
		String block = null;
		if(!ConfigManager.getMainConfig().diveEnabled)
			block = "diveEnabled=false";
		else if(!p.isOnline())
			block = "not online";
		else if(!p.isSneaking())
			block = null;
		if(block == null)
		{
			// Require a sustained crouch: the player must have been genuinely holding
			// sneak for a moment before jumping. A one-tick or leftover sneak flag must
			// not turn a normal jump into a dive.
			Long since = sneakSince.get(p.getUniqueId());
			if(since == null || (System.currentTimeMillis() - since) < SNEAK_HOLD_MS)
				block = "sneak held too briefly (< " + SNEAK_HOLD_MS + "ms) or no sneak-toggle seen";
			else if(!inActiveGame(p))
				block = "not in an active game";
			else if(stamina != null && stamina.isExhausted(p))
				block = "out of stamina";
		}
		if(block == null)
		{
			double cooldownMs = Math.max(0.0, ConfigManager.getMainConfig().diveCooldownSeconds) * 1000.0;
			long now = System.currentTimeMillis();
			Long last = lastDive.get(p.getUniqueId());
			if(last != null && (now - last) < cooldownMs)
				block = "on cooldown";
		}

		if(block != null)
		{
			// Only log when the player was actually sneaking (a real dive attempt); a
			// plain standing jump is expected to be blocked and would be spammy.
			if(p.isSneaking())
				log.info("[mz-prone] dive attempt BLOCKED for " + p.getName() + " — " + block);
			return;
		}

		long now = System.currentTimeMillis();

		// Thrust: keep the small sneak-hop and add a horizontal burst in the facing
		// direction on top of it. We do NOT cancel the jump — the velocity is applied
		// client-side, so cancelling would rubber-band the player. Adding to the
		// existing (mostly Y) velocity preserves the hop's lift while giving the slide.
		double yaw = Math.toRadians(p.getLocation().getYaw());
		double strength = Math.max(0.0, ConfigManager.getMainConfig().diveLungeStrength);
		Vector v = p.getVelocity().add(new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw)).multiply(strength));
		p.setVelocity(v);
		lastDive.put(p.getUniqueId(), now);

		// Prone: enter a HELD prone pose. The tick task keeps the player in a low crawl
		// (forced crouch, capped speed, no sprint) until they tap sneak to stand up.
		// Drop a held crouch first for exclusivity (the flat-prone pose must own the
		// posture); the dive marker that follows replaces the crouch marker client-side.
		crouchPlayers.remove(p.getUniqueId());
		pronePlayers.add(p.getUniqueId());
		if(!p.isSneaking())
			p.setSneaking(true);
		applyCrawl(p);
		// Signal the local client mod to enter/hold its prone pose via a hidden
		// action-bar marker. The value is the entry ramp length (ticks) over which the
		// pose eases in; the pose then stays held until the client receives the matching
		// "stand" marker (sent on a sneak tap).
		sendDiveSignal(p, PRONE_ENTRY_RAMP_TICKS);
		log.info("[mz-prone] DIVE FIRED for " + p.getName() + " — entering held prone, strength=" + strength);
	}

	/**
	 * Determines whether the given player is participating in an active game (not a
	 * spectator).
	 *
	 * @param p the player to check.
	 * @return true if the player is in a running game.
	 */
	private boolean inActiveGame(Player p)
	{
		if(GameManager.INSTANCE == null)
			return false;
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game == null || game.getStatus() != Game.GameStatus.INGAME)
				continue;
			if(game.isPlayerPlaying(p))
				return true;
		}
		return false;
	}
}


