package com.theprogrammingturkey.comz.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GameManager
{
	public static final GameManager INSTANCE = new GameManager();

	private final List<Game> games = new ArrayList<>();

	public List<Game> getGames()
	{
		return games;
	}

	public void removeGame(Game game)
	{
		if(game.isInstance())
			throw new IllegalArgumentException("Runtime instances cannot be removed individually; change the template pool size.");
		for(Game copy : new ArrayList<>(games))
			if(copy.isInstance() && copy.getTemplateName().equals(game.getName()))
			{
				copy.endGame();
				games.remove(copy);
			}
		this.games.remove(game);
	}

	public void endAll()
	{
		for(Game game : games)
			game.endGame();
	}

	public Game getGame(Entity entity)
	{
		for(Game game : games)
			for(Entity ent : game.spawnManager.getEntities())
				if(ent.equals(entity))
					return game;
		return null;
	}

	public void loadAllGames()
	{
		endAll();
		games.forEach(game -> game.retire());
		ArenaInstances.INSTANCE.shutdown();
		games.clear();
		JsonElement jsonElement = ConfigManager.getConfig(COMZConfig.ARENAS).getJson();
		if(jsonElement.isJsonNull())
		{
			COMZombies.log.log(Level.SEVERE, "Failed to load in the arenas from the arenas config!");
			return;
		}
		JsonObject jsonObject = jsonElement.getAsJsonObject();
		for(Map.Entry<String, JsonElement> arena : jsonObject.entrySet())
		{
			Game game = new Game(arena.getKey());
			if(game.loadGame(arena.getValue()))
				games.add(game);
			else
				COMZombies.log.log(Level.SEVERE, "Failed to load arena {0}!", new Object[]{arena.getKey()});
		}
		ArenaInstances.INSTANCE.loadPools();
		Bukkit.broadcast(Colors.legacy(COMZombies.PREFIX + Colors.RED + " Done loading arenas!"));
	}

	public void saveAllGames()
	{
		JsonObject arenasSave = new JsonObject();
		for(Game game : games)
			if(!game.isInstance())
				arenasSave.add(game.getName(), game.saveGame());
		ConfigManager.getConfig(COMZConfig.ARENAS).saveConfig(arenasSave);
	}

	public void disableAllArenas()
	{
		for(Game gl : games)
		{
			for(Game game : games)
				for(Door door : game.doorManager.getDoors())
					door.closeDoor();
			gl.endGame();
			gl.resetSpawnLocationBlocks();
		}
	}

	public void addGame(Game game)
	{
		games.add(game);
	}

	public Game getGame(Door door)
	{
		for(Game gl : games)
			if(gl.doorManager.getDoors().contains(door))
				return gl;
		return null;
	}

	public Game getGame(Player player)
	{
		for(Game game : games)
			if(game.isPlayerPlaying(player) || game.isPlayerSpectating(player))
				return game;
		for(Game game : games)
			if(game.wasDisconnected(player))
				return game;
		return null;
	}

	public boolean isPlayerInGame(Player player)
	{
		for(Game game : games)
			if(game.isPlayerPlaying(player) || game.isPlayerSpectating(player))
				return true;
		return false;
	}

	public Game getGame(String name)
	{
		for(Game gl : games)
			if(name.equalsIgnoreCase(gl.getName()))
				return gl;

		for(Game gl : games)
		{
			String gameName = gl.getName();
			for(int i = 1; i <= gameName.length(); i++)
			{
				String tempName = gameName.substring(0, i);
				if(name.equalsIgnoreCase(tempName))
					return gl;
			}
		}
		return null;
	}

	public boolean isLocationInGame(Location loc)
	{
		for(Game gl : games)
			if(gl.arena.containsBlock(loc) && gl.getStatus() != GameStatus.DISABLED)
				return true;
		return false;
	}

	public boolean isEntityInGame(Entity entity)
	{
		if(entity instanceof Player player)
			return isPlayerInGame(player);

		if(entity instanceof Mob mob)
			for(Game game : games)
				if(game.spawnManager.isEntitySpawned(mob))
					return true;

		return false;
	}

	public boolean isValidArena(String name)
	{
		for(Game gl : games)
		{
			for(int pf = gl.getName().length(); pf >= 0; pf--)
			{
				String gN = gl.getName().substring(0, pf);
				if(gN.equalsIgnoreCase(name))
					return true;
			}
		}
		return false;
	}

	public Game getGame(Location loc)
	{
		for(Game gl : games)
			if(gl.arena.containsBlock(loc))
				return gl;
		return null;
	}

	@Override
	public String toString()
	{
		StringBuilder toString = new StringBuilder("GameManager, games: ");
		for(Game game : this.games)
			toString.append(" ").append(game.getName());

		return toString.toString();
	}

	public List<String> getArenas()
	{
		return games.stream().map(game -> game.getName()).collect(Collectors.toList());
	}

	public Game findJoinable(String template)
	{
		return games.stream()
				.filter(g -> !g.isTemplate() && (template == null || g.getTemplateName().equalsIgnoreCase(template)))
				.filter(g -> g.getStatus() == GameStatus.WAITING || g.getStatus() == GameStatus.STARTING)
				.filter(g -> !g.spawnManager.getPoints().isEmpty() && g.getPlayersInGame().size() < g.maxPlayers)
				.max(java.util.Comparator.comparingInt(g -> g.getPlayersInGame().size())).orElse(null);
	}

	/** Preserve other matches' visibility when one match finishes. */
	public void refreshVisibility()
	{
		for(Player viewer : Bukkit.getOnlinePlayers())
			for(Player other : Bukkit.getOnlinePlayers())
			{
				Game match = isPlayerInGame(viewer) ? getGame(viewer) : null;
				if(match != null && !match.getPlayersAndSpectators().contains(other))
					viewer.hidePlayer(COMZombies.getPlugin(), other);
				else
					viewer.showPlayer(COMZombies.getPlugin(), other);
			}
	}
}
