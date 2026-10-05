package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;

public interface IGameSign
{
	/**
	 * @return the key this sign type is saved under in gamesigns.json and its text is under in config.yml
	 */
	String getType();

	/**
	 * Turns the lines typed onto a new sign (or found on an old-style sign) into this sign's data.
	 *
	 * @param player the player who placed the sign, or null when converting an old-style sign
	 * @param lines  the sign's lines, with colors stripped
	 * @throws SignParseException with the error text to show on the sign if the lines are invalid
	 */
	Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException;

	/**
	 * Called once when a new sign of this type is placed, after {@link #parse}.
	 */
	default void onCreate(Game game, Player player, Location location, Map<String, String> data)
	{
	}

	void onInteract(Game game, Player player, Location location, Map<String, String> data);

	default void onBreak(Game game, Player player, Location location)
	{
	}

	/**
	 * @return the text to show on the sign, or null to leave it as it is
	 */
	String[] getText(Map<String, String> data);

	boolean requiresGame();

	/**
	 * Signs belonging to a game feature (doors, mystery boxes) are found through that feature instead of the
	 * sign registry, since the feature already tracks them.
	 */
	default boolean isRegistered()
	{
		return true;
	}
}
