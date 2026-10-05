package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.commands.CommandManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class JoinSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "join";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		Game arena = GameManager.INSTANCE.isValidArena(lines[2]) ? GameManager.INSTANCE.getGame(lines[2]) : null;
		if(arena == null)
			throw new SignParseException(ChatColor.DARK_RED + "No such", ChatColor.DARK_RED + "game!");

		Map<String, String> data = new HashMap<>();
		data.put("arena", arena.getName());
		return data;
	}

	@Override
	public void onCreate(Game game, Player player, Location location, Map<String, String> data)
	{
		GameManager.INSTANCE.getGame(data.get("arena")).signManager.addSign(location);
	}

	@Override
	public void onBreak(Game game, Player player, Location location)
	{
		for(Game g : GameManager.INSTANCE.getGames())
			if(g.signManager.isSign(location))
				g.signManager.removeSign(location);
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		game = GameManager.INSTANCE.getGame(data.get("arena"));
		if(game != null)
		{
			if(!game.signManager.isSign(location))
				game.signManager.addSign(location);

			String[] args = new String[2];
			args[0] = "join";
			args[1] = game.getName();
			player.getLocation().getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1, 1);
			CommandManager.INSTANCE.onRemoteCommand(player, args);
			game.signManager.updateGame();
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_RED + "There is no arena called " + ChatColor.GOLD + data.get("arena") + ChatColor.DARK_RED + "! Contact an admin to fix this issue!");
		}
	}

	@Override
	public String[] getText(Map<String, String> data)
	{
		Game game = GameManager.INSTANCE.getGame(data.get("arena"));
		return game == null ? null : getText(game);
	}

	/**
	 * Join signs change with the state of their game.
	 */
	public static String[] getText(Game game)
	{
		Map<String, String> values = new HashMap<>();
		values.put("arena", game.getName());
		values.put("players", String.valueOf(game.getPlayersInGame().size()));
		values.put("max", String.valueOf(game.maxPlayers));
		values.put("alive", String.valueOf(game.getPlayersInGame().size()));
		values.put("wave", String.valueOf(game.getWave()));

		switch(game.getStatus())
		{
			case DISABLED:
				return SignText.render("join_maintenance", values);
			case INGAME:
				return SignText.render("join_ingame", values);
			default:
				return SignText.render("join_waiting", values);
		}
	}

	@Override
	public boolean requiresGame()
	{
		return false;
	}
}
