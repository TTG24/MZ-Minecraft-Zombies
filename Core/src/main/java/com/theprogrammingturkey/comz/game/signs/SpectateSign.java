package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class SpectateSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "spectate";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		// Typed with the arena on line 3. Old-style signs show "Arena:" there and the name on line 4.
		String name = lines[2].equalsIgnoreCase("Arena:") ? lines[3] : lines[2];
		Game arena = GameManager.INSTANCE.getGame(name);
		if(arena == null)
			throw new SignParseException(ChatColor.RED + "" + ChatColor.BOLD + "Arena name is", ChatColor.RED + "" + ChatColor.BOLD + "not a valid", ChatColor.RED + "" + ChatColor.BOLD + "arena!");

		Map<String, String> data = new HashMap<>();
		data.put("arena", arena.getName());
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		game = GameManager.INSTANCE.getGame(data.get("arena"));
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Invalid Arena!");
			return;
		}
		player.performCommand("zombies spec " + game.getName());
	}

	@Override
	public String[] getText(Map<String, String> data)
	{
		return SignText.render(getType(), data);
	}

	@Override
	public boolean requiresGame()
	{
		return false;
	}
}
