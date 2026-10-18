package com.theprogrammingturkey.comz.game;

/**
 * Per-match stats for a single player, used to build the post-match leaderboard shown
 * while players linger in spectator mode. Unlike {@link com.theprogrammingturkey.comz.leaderboards.PlayerStats},
 * these counts are scoped to the current match only.
 */
public class PlayerMatchStats
{
	private final String name;
	private int points;
	private int kills;
	private int revives;
	private int deaths;

	public PlayerMatchStats(String name, int points, int kills, int revives, int deaths)
	{
		this.name = name;
		this.points = points;
		this.kills = kills;
		this.revives = revives;
		this.deaths = deaths;
	}

	public void incKills()
	{
		this.kills++;
	}

	public void incRevives()
	{
		this.revives++;
	}

	public void incDeaths()
	{
		this.deaths++;
	}

	public String getName()
	{
		return name;
	}

	public int getPoints()
	{
		return points;
	}

	public int getKills()
	{
		return kills;
	}

	public int getRevives()
	{
		return revives;
	}

	public int getDeaths()
	{
		return deaths;
	}
}
