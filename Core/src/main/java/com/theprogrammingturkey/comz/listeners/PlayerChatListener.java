package com.theprogrammingturkey.comz.listeners;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

import net.kyori.adventure.text.Component;
import com.theprogrammingturkey.comz.util.Colors;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.actions.BaseAction;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class PlayerChatListener implements Listener
{

	/**
	 * Token sent by the mz-prone client mod when the player quick-TAPS the C key
	 * (pressing-and-releasing, less than the hold threshold). The stance matrix:
	 *
	 * <ul>
	 *   <li>STANDING → CROUCH — {@code DiveListener.enterCrouch}</li>
	 *   <li>CROUCH   → STANDING — {@code DiveListener.exitCrouch}</li>
	 *   <li>PRONE    → CROUCH — {@code DiveListener.enterCrouch} (auto-drops prone
	 *       for posture exclusivity)</li>
	 * </ul>
	 *
	 * <p>Cancelled on receipt so the player never sees it in chat.
	 */
	public static final String CROUCH_TOKEN = "mzcrouch";

	/**
	 * Token sent by the mz-prone client mod when the player HOLDS the C key
	 * (2 s sustained, as detected on the client). Transition:
	 *
	 * <ul>
	 *   <li>STANDING → PRONE — {@code DiveListener.enterProneNoLunge}</li>
	 *   <li>CROUCH   → PRONE — {@code DiveListener.enterProneNoLunge} (auto-drops
	 *       crouch for posture exclusivity)</li>
	 *   <li>PRONE    → PRONE (refresh — no-op but re-arms the held pose)</li>
	 * </ul>
	 *
	 * <p><b>Why a separate token instead of just sending {@link #CROUCH_TOKEN}
	 * with different timing?</b> The client already has to distinguish tap-vs-hold
	 * locally (the hold requires polling key-state over a window), so the clean
	 * dispatch is client-side. Two tokens keep the server handler declarative.
	 */
	public static final String CROUCH_HOLD_TOKEN = "mzcrouchhold";

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(AsyncChatEvent playerChat)
	{
		COMZombies plugin = COMZombies.getPlugin();
		Player player = playerChat.getPlayer();
		String message = Colors.plain(playerChat.message()).replaceFirst(" ", "").trim();

		// C-key tap (quick press-and-release): toggles the deep crouch stance.
		// CANCELLED on receipt — no visible message in chat. Delegate to DiveListener
		// for the actual stance transition. The existing toggle logic already covers
		// the full 3-state matrix because enterCrouch / exitCrouch are idempotent
		// per current state and handle exclusivity with the flat-prone pose
		// (enterCrouch auto-drops prone; exitCrouch leaves a held prone alone).
		if(message.equalsIgnoreCase(CROUCH_TOKEN))
		{
			playerChat.setCancelled(true);
			DiveListener dive = DiveListener.INSTANCE;
			if(dive != null)
			{
				UUID id = player.getUniqueId();
				if(dive.isCrouched(id))
				{
					COMZombies.scheduleTask(1, () -> dive.exitCrouch(player));
				}
				else
				{
					COMZombies.scheduleTask(1, () -> dive.enterCrouch(player));
				}
			}
			return;
		}
		// C-key hold (2 s sustained, detected client-side): force transition to the
		// flat-dive-PRONE pose regardless of current stance (STANDING→PRONE or
		// CROUCH→PRONE; PRONE is a no-op refresh). Distinct from the dive-lunge
		// path because there is no horizontal thrust — the player simply settles
		// into the held flat pose in place.
		if(message.equalsIgnoreCase(CROUCH_HOLD_TOKEN))
		{
			playerChat.setCancelled(true);
			DiveListener dive = DiveListener.INSTANCE;
			if(dive != null)
			{
				COMZombies.scheduleTask(1, () -> dive.enterProneNoLunge(player));
			}
			return;
		}

		if(plugin.activeActions.containsKey(player))
		{
			BaseAction action = plugin.activeActions.get(player);

			if(message.equalsIgnoreCase("cancel"))
			{
				COMZombies.scheduleTask(1, () ->
				{
					plugin.activeActions.remove(player);
					action.cancelAction();
				});
			}
			else
			{
				COMZombies.scheduleTask(1, () -> action.onChatMessage(message));
			}
			playerChat.setCancelled(true);
		}

		if(plugin.isEditingASign.containsKey(player))
		{
			if(message.equalsIgnoreCase("done"))
			{
				Location loc = plugin.isEditingASign.get(player);
				Sign sign = (Sign) loc.getBlock().getState();
				plugin.isEditingASign.remove(player);
				// Build the modern List<Component> form from the sign's current
				// front-side lines so the event behaves identically for downstream listeners.
				org.bukkit.block.sign.SignSide front = sign.getSide(Side.FRONT);
				List<Component> lines = new ArrayList<>();
				for(int i = 0; i < 4; i++)
					lines.add(front.line(i));
				Bukkit.getServer().getPluginManager().callEvent(new SignChangeEvent(sign.getBlock(), player, lines, Side.FRONT));
				CommandUtil.sendMessageToPlayer(player, "You are no longer editing a sign!");
				playerChat.setCancelled(true);
				sign.update();
			}
		}
	}
}
