package com.theprogrammingturkey.comz.economy;

import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class PointManager
{
	public static final PointManager INSTANCE = new PointManager();
	private final HashMap<java.util.UUID, PlayerPoints> playersPoints = new HashMap<>();

	private PointManager()
	{

	}

	public void initalizePlayer(Player player)
	{
		playersPoints.put(player.getUniqueId(), new PlayerPoints(player, startingPoints()));
	}

	public boolean canBuy(Player player, int required)
	{
		return getPlayerPoints(player).canWithdraw(required);
	}

	public PlayerPoints getPlayerPoints(Player player)
	{
		if(!playersPoints.containsKey(player.getUniqueId()))
			initalizePlayer(player);
		return playersPoints.get(player.getUniqueId());
	}

	public void addPoints(Player player, int amount)
	{
		getPlayerPoints(player).addPoints(amount);
	}

	/**
	 * Updates all other players ingame on points change
	 */
	public void notifyPlayer(Player player)
	{
		Game game = GameManager.INSTANCE.getGame(player);
		if(game == null)
			return;
		game.scoreboard.update();
	}

	public void takePoints(Player player, int amount)
	{
		getPlayerPoints(player).takePoints(amount);
	}

	public void unloadPlayer(Player player)
	{
		playersPoints.remove(player.getUniqueId());
	}

	public void playerLeaveGame(Player player)
	{
		playersPoints.remove(player.getUniqueId());
	}

	public int getPlayersPoints(Player player)
	{
		PlayerPoints p = playersPoints.get(player.getUniqueId());
		return p != null ? p.getPoints() : startingPoints();
	}

	/**
	 * Starting points from config (default 500).
	 */
	private static int startingPoints()
	{
		return ConfigManager.getMainConfig().startingPoints;
	}

	public void saveAll()
	{
		PlayerPoints.storeAll(playersPoints.values());
	}

	public void clearGamePoints(Game game)
	{
		for(Player pl : game.getPlayersInGame())
			playerLeaveGame(pl);
	}

	public void setPoints(Player player, int points)
	{
		PlayerPoints p = playersPoints.get(player.getUniqueId());
		if(p != null)
			p.setPoints(points);
	}
}
