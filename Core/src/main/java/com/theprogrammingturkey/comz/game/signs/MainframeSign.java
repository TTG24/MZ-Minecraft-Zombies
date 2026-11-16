package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.TeleporterLinks;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * The mainframe: activated teleporter pads are linked here. Sign: [Zombies] / mainframe.
 */
public class MainframeSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "mainframe";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines)
	{
		return new HashMap<>();
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		if(!GameManager.INSTANCE.isPlayerInGame(player) || game.getStatus() != Game.GameStatus.INGAME)
			return;
		TeleporterLinks.useMainframe(game, player, location);
	}

	@Override
	public String[] getText(Map<String, String> data)
	{
		return SignText.render(getType(), data);
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
