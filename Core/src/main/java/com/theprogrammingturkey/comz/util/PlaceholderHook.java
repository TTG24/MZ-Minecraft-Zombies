package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.PlayerMatchStats;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.leaderboards.Leaderboard;
import com.theprogrammingturkey.comz.leaderboards.PlayerStats;
import com.theprogrammingturkey.comz.leaderboards.StatsCategory;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class PlaceholderHook extends PlaceholderExpansion
{
	/**
	 * Because this is an internal class,
	 * you must override this method to let PlaceholderAPI know to not unregister your expansion class when
	 * PlaceholderAPI is reloaded
	 *
	 * @return true to persist through reloads
	 */
	@Override
	public boolean persist()
	{
		return true;
	}

	/**
	 * Because this is a internal class, this check is not needed
	 * and we can simply return {@code true}
	 *
	 * @return Always true since it's an internal class.
	 */
	@Override
	public boolean canRegister()
	{
		return true;
	}

	/**
	 * The name of the person who created this expansion should go here.
	 * <br>For convienience do we return the author from the plugin.yml
	 *
	 * @return The name of the author as a String.
	 */
	@Override
	@NotNull
	@SuppressWarnings("null")
	public String getAuthor()
	{
		return COMZombies.getPlugin().getPluginMeta().getAuthors().toString();
	}

	/**
	 * The placeholder identifier should go here.
	 * <br>This is what tells PlaceholderAPI to call our onRequest
	 * method to obtain a value if a placeholder starts with our
	 * identifier.
	 * <br>The identifier has to be lowercase and can't contain _ or %
	 *
	 * @return The identifier in {@code %<identifier>_<value>%} as String.
	 */
	@Override
	@NotNull
	public String getIdentifier()
	{
		return "comz";
	}

	/**
	 * This is the version of the expansion.
	 * <br>You don't have to use numbers, since it is set as a String.
	 * <p>
	 * For convenience do we return the version from the plugin.yml
	 *
	 * @return The version as a String.
	 */
	@Override
	@NotNull
	@SuppressWarnings("null")
	public String getVersion()
	{
		return COMZombies.getPlugin().getPluginMeta().getVersion();
	}

	/**
	 * This is the method called when a placeholder with our identifier
	 * is found and needs a value.
	 * <br>We specify the value identifier in this method.
	 * <br>Since version 2.10.8 you must OfflinePlayer in your requests.
	 *
	 * @param player     A {@link org.bukkit.OfflinePlayer OfflinePlayer}.
	 * @param identifier A String containing the identifier/value.
	 * @return possibly-null String of the requested identifier.
	 */
	@Override
	public String onRequest(OfflinePlayer player, String identifier)
	{
		String[] parts = identifier.split("_");

		// Live, per-viewer game info (used by the TAB header/footer). Read off the main thread by TAB, so a list changing
		// mid-read just gives an empty value for one refresh instead of an error.
		try
		{
			String live = live(player, identifier);
			if(live != null)
				return live;
		} catch(RuntimeException e)
		{
			return "";
		}

		if(parts[0].equals("leaderboard"))
		{
			if(parts.length == 1)
				return null;

			StatsCategory category = getStatEnumFromString(parts[1]);
			if(parts.length == 2)
			{
				PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
				return getStatForCategory(category, stats);
			}
			else
			{
				String part2 = parts[2];
				boolean isPosition = part2.matches("\\d+");

				PlayerStats stat;
				if(isPosition)
				{
					stat = Leaderboard.getPosX(category, Integer.parseInt(part2));
				}
				else
				{
					if(!part2.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
						return null;

					stat = Leaderboard.getPlayerStatFromPlayer(Bukkit.getServer().getOfflinePlayer(UUID.fromString(part2)));
				}

				String statValue = getStatForCategory(category, stat);
				String fullText = stat == null ? "" : stat.getPlayerDisplay() + " - " + statValue;
				if(parts.length == 4)
				{
					if(parts[3].equals("value"))
						return statValue;
				}

				return fullText;
			}
		}
		else if(parts[0].equals("arena"))
		{
			Game game = GameManager.INSTANCE.getGame(parts[1]);
			if(game != null && parts.length == 3)
			{
				switch(parts[2])
				{
					case "status" -> { return game.getName() + " - " + game.getStatus().name(); }
					case "wave" -> { return game.getName() + " - " + game.getWave(); }
					case "players" -> { return game.getName() + " - " + game.getPlayersInGame().size() + "/" + game.maxPlayers; }
					default -> {}
				}
			}
		}

		return null;
	}

	/**
	 * %comz_ingame% (yes/no), %comz_game% (arena), %comz_state% (Waiting/Starting/In game/Spectating/Downed),
	 * %comz_round%, %comz_points%, %comz_zombies% (left this round), %comz_kills% (this match), %comz_players%
	 * (in this game), %comz_playing% (in any game, server wide), %comz_games% (matches being played).
	 * Game values are empty when the player isn't in one.
	 */
	private String live(OfflinePlayer offline, String id)
	{
		switch(id)
		{
			case "playing" -> {
				int n = 0;
				for(Game g : GameManager.INSTANCE.getGames())
					n += g.getPlayersInGame().size();
				return String.valueOf(n);
			}
			case "games" -> {
				int n = 0;
				for(Game g : GameManager.INSTANCE.getGames())
					if(g.getStatus() == Game.GameStatus.INGAME)
						n++;
				return String.valueOf(n);
			}
			case "ingame", "game", "state", "round", "points", "zombies", "kills", "players" -> {}
			default -> { return null; }
		}
		Player p = offline == null ? null : offline.getPlayer();
		Game game = p == null ? null : GameManager.INSTANCE.getGame(p);
		if(id.equals("ingame"))
			return game == null ? "no" : "yes";
		if(game == null)
			// Numbers stay numbers (TAB's player list score needs one); text is just blank
			return switch(id) { case "round", "points", "zombies", "kills", "players" -> "0"; default -> ""; };
		boolean live = game.getStatus() == Game.GameStatus.INGAME;
		return switch(id)
		{
			case "game" -> game.getTemplateName();
			case "state" -> {
				if(game.isPlayerSpectating(p)) yield "Spectating";
				if(live && game.isPlayerDowned(p)) yield "Downed";
				yield switch(game.getStatus())
				{
					case INGAME -> "In game";
					case STARTING -> "Starting";
					default -> "Waiting";
				};
			}
			case "round" -> String.valueOf(game.getWave());
			case "points" -> String.valueOf(PointManager.INSTANCE.getPlayersPoints(p));
			case "zombies" -> live ? String.valueOf(Math.max(0, game.getZombiesLeft())) : "0";
			case "kills" -> {
				PlayerMatchStats stats = game.getMatchStats(p.getUniqueId());
				yield String.valueOf(stats == null ? 0 : stats.getKills());
			}
			case "players" -> String.valueOf(game.getPlayersInGame().size());
			default -> "";
		};
	}

	private String getStatForCategory(StatsCategory category, PlayerStats stats)
	{
		return switch(category)
		{
			case KILLS -> String.valueOf(stats.getKills());
			case REVIVES -> String.valueOf(stats.getRevives());
			case DEATHS -> String.valueOf(stats.getDeaths());
			case DOWNS -> String.valueOf(stats.getDowns());
			case GAMES_PLAYED -> String.valueOf(stats.getGamesPlayed());
			case HIGHEST_ROUND -> String.valueOf(stats.getHighestRound());
			case MOST_POINTS -> String.valueOf(stats.getMostPoints());
			default -> "";
		};
	}

	private StatsCategory getStatEnumFromString(String toGet)
	{
		switch(toGet)
		{
			case "kills" -> { return StatsCategory.KILLS; }
			case "revives" -> { return StatsCategory.REVIVES; }
			case "deaths" -> { return StatsCategory.DEATHS; }
			case "downs" -> { return StatsCategory.DOWNS; }
			case "gamesPlayed" -> { return StatsCategory.GAMES_PLAYED; }
			case "highestRound" -> { return StatsCategory.HIGHEST_ROUND; }
			case "mostPoints" -> { return StatsCategory.MOST_POINTS; }
			default -> { return StatsCategory.KILLS; }
		}
	}
}
