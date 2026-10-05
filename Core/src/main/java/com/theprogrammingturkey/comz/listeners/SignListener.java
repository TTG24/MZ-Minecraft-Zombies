package com.theprogrammingturkey.comz.listeners;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.signs.GameSigns;
import com.theprogrammingturkey.comz.game.signs.IGameSign;
import com.theprogrammingturkey.comz.game.signs.SignParseException;
import com.theprogrammingturkey.comz.game.signs.SignRegistry;
import com.theprogrammingturkey.comz.game.signs.SignText;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class SignListener implements Listener
{
	/**
	 * A game sign found at a location: what kind it is and its data.
	 */
	private static class GameSignInfo
	{
		final IGameSign logic;
		final Map<String, String> data;

		GameSignInfo(IGameSign logic, Map<String, String> data)
		{
			this.logic = logic;
			this.data = data;
		}
	}

	/**
	 * Works out which game sign is at a location from where it is, not what it says, so sign text can be anything.
	 *
	 * @param game    the game whose arena contains the sign, if any
	 * @param convert whether to convert an old-style sign (recognised by its text) to the new system
	 * @return the sign, or null if it isn't a game sign
	 */
	private GameSignInfo findGameSign(Sign sign, Game game, boolean convert)
	{
		Location loc = sign.getLocation();

		// Signs owned by game features
		if(game != null)
		{
			if(game.boxManager.getBox(loc) != null)
				return new GameSignInfo(GameSigns.MYSTERY_BOX, Collections.emptyMap());
			if(game.doorManager.getDoorFromSign(loc) != null)
				return new GameSignInfo(GameSigns.DOOR, Collections.emptyMap());
		}

		SignRegistry.Entry entry = SignRegistry.INSTANCE.get(loc);
		if(entry != null && GameSigns.fromType(entry.type) != null)
			return new GameSignInfo(GameSigns.fromType(entry.type), entry.data);

		// Join signs from before the registry. Each game already tracks its own.
		for(Game g : GameManager.INSTANCE.getGames())
		{
			if(g.signManager.isSign(loc))
			{
				Map<String, String> data = Collections.singletonMap("arena", g.getName());
				if(convert)
					SignRegistry.INSTANCE.register(loc, GameSigns.JOIN.getType(), new HashMap<>(data));
				return new GameSignInfo(GameSigns.JOIN, data);
			}
		}

		// Old-style signs, recognised by "[Zombies]" on the first line
		String[] lines = new String[4];
		for(int i = 0; i < 4; i++)
			lines[i] = ChatColor.stripColor(sign.getLine(i)).trim();

		IGameSign logic = lines[0].equalsIgnoreCase("[Zombies]") ? GameSigns.fromName(lines[1]) : null;
		if(logic == null || !logic.isRegistered() || (logic.requiresGame() && game == null))
			return null;

		try
		{
			Map<String, String> data = logic.parse(game, null, lines);
			if(convert)
			{
				SignRegistry.INSTANCE.register(loc, logic.getType(), data);
				String[] text = logic.getText(data);
				if(text != null)
					SignText.apply(sign, text);
			}
			return new GameSignInfo(logic, data);
		} catch(SignParseException e)
		{
			return null;
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockBreakEvent(BlockBreakEvent event)
	{
		if(!BlockUtils.isSign(event.getBlock().getType()))
			return;

		Sign sign = (Sign) event.getBlock().getState();
		Player player = event.getPlayer();

		if(BlockUtils.isBarrierRepairSign(event.getBlock(), player))
		{
			Game game = GameManager.INSTANCE.getGame(player);
			Barrier b = game.barrierManager.getBarrierFromRepair(sign.getLocation());
			b.repair(player);
			event.setCancelled(true);
			sign.update();
			return;
		}

		Location signLoc = sign.getLocation();
		Game game = GameManager.INSTANCE.getGame(signLoc);
		GameSignInfo gameSign = findGameSign(sign, game, false);
		if(gameSign == null)
			return;

		if(game != null && game.getStatus() != Game.GameStatus.DISABLED)
		{
			event.setCancelled(true);
			return;
		}

		if(game != null || !gameSign.logic.requiresGame())
			gameSign.logic.onBreak(game, player, signLoc);
		SignRegistry.INSTANCE.remove(signLoc);
	}

	@EventHandler
	public void RightClickSign(PlayerInteractEvent event)
	{
		if(event.getClickedBlock() == null)
			return;

		COMZombies plugin = COMZombies.getPlugin();

		if(BlockUtils.isSign(event.getClickedBlock().getType()))
		{
			Sign sign = (Sign) event.getClickedBlock().getState();
			Player player = event.getPlayer();

			if(GameManager.INSTANCE.isPlayerInGame(player))
			{
				// We don't want to cancel the event if this case so that barrier signs can work
				if(event.getAction() != Action.LEFT_CLICK_BLOCK || !BlockUtils.isBarrierRepairSign(event.getClickedBlock(), player))
				{
					sign.setEditable(false);
					sign.update();
					event.setCancelled(true);
				}
			}

			Game game = GameManager.INSTANCE.getGame(sign.getLocation());
			GameSignInfo gameSign = findGameSign(sign, game, true);
			if(gameSign == null)
				return;

			if(event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking() && player.isOp())
			{
				if(!plugin.isEditingASign.containsKey(player) && !GameManager.INSTANCE.isPlayerInGame(player))
				{
					plugin.isEditingASign.put(player, event.getClickedBlock().getLocation());
					CommandUtil.sendMessageToPlayer(player, "You are now editing a sign!");
					return;
				}
			}

			if(game != null || !gameSign.logic.requiresGame())
			{
				if(game != null && gameSign.logic.requiresGame() && game.getStatus() != Game.GameStatus.INGAME)
					return;

				gameSign.logic.onInteract(game, player, sign.getLocation(), gameSign.data);
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void eventSignChanged(SignChangeEvent event)
	{
		if(!BlockUtils.isSign(event.getBlock().getType()))
			return;

		String[] lines = new String[4];
		for(int i = 0; i < 4; i++)
		{
			String line = event.getLine(i);
			lines[i] = line == null ? "" : ChatColor.stripColor(line).trim();
		}

		if(!lines[0].equalsIgnoreCase("[Zombies]"))
			return;

		Location loc = event.getBlock().getLocation();
		Game game = GameManager.INSTANCE.getGame(loc);
		IGameSign signLogic = GameSigns.fromName(lines[1]);
		if(signLogic == null)
		{
			event.setLine(0, ChatColor.RED + String.valueOf(ChatColor.BOLD) + lines[1]);
			event.setLine(1, ChatColor.RED + String.valueOf(ChatColor.BOLD) + "is not a");
			event.setLine(2, ChatColor.RED + String.valueOf(ChatColor.BOLD) + "valid sign");
			event.setLine(3, "");
			return;
		}

		if(game == null && signLogic.requiresGame())
		{
			event.setLine(0, ChatColor.RED + String.valueOf(ChatColor.BOLD) + "Sign is");
			event.setLine(1, ChatColor.RED + String.valueOf(ChatColor.BOLD) + "not in");
			event.setLine(2, ChatColor.RED + String.valueOf(ChatColor.BOLD) + "an arena!");
			event.setLine(3, "");
			return;
		}

		try
		{
			Map<String, String> data = signLogic.parse(game, event.getPlayer(), lines);
			signLogic.onCreate(game, event.getPlayer(), loc, data);
			if(signLogic.isRegistered())
				SignRegistry.INSTANCE.register(loc, signLogic.getType(), data);

			String[] text = signLogic.getText(data);
			if(text != null)
				for(int i = 0; i < 4; i++)
					event.setLine(i, text[i]);
		} catch(SignParseException e)
		{
			for(int i = 0; i < 4; i++)
				event.setLine(i, e.getLines()[i]);
		}
	}
}
