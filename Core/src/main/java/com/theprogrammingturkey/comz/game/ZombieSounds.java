package com.theprogrammingturkey.comz.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;

import com.theprogrammingturkey.comz.COMZombies;

/**
 * Footsteps for walking zombies and the growl of fast ("sprinting") zombies, using the sounds in the resource pack
 * (mz:zombie_step, mz:zombie_sprint). Only zombies within hearing range of a player in the match are processed, so a
 * big wave stays cheap. Clients without the pack simply hear nothing.
 */
public final class ZombieSounds
{
	/** Metadata key set on zombies that were given extra speed. */
	public static final String SPRINTER = "mz_sprinter";

	private static final double HEAR_RANGE = 24;
	private static final double STRIDE = 1.1;
	private static final long PERIOD = 3L;

	private static final class Track
	{
		double x, z;
		double walked;
		long nextGrowl;
	}

	private static final Map<UUID, Track> tracks = new HashMap<>();

	private ZombieSounds() {}

	public static void start(Plugin plugin)
	{
		Bukkit.getScheduler().runTaskTimer(plugin, ZombieSounds::tick, 20L, PERIOD);
	}

	/** Marks a zombie as a sprinter; it growls now and then while alive. */
	public static void markSprinter(Zombie zombie)
	{
		zombie.setMetadata(SPRINTER, new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), true));
	}

	private static void tick()
	{
		long now = Bukkit.getCurrentTick();
		Set<UUID> seen = new HashSet<>();
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
				continue;
			List<Player> players = game.getPlayersInGame();
			if(players.isEmpty())
				continue;
			for(Mob mob : game.spawnManager.getEntities())
			{
				if(!(mob instanceof Zombie) || !mob.isValid())
					continue;
				UUID id = mob.getUniqueId();
				seen.add(id);
				Location loc = mob.getLocation();
				Track track = tracks.get(id);
				if(track == null)
				{
					track = new Track();
					track.x = loc.getX();
					track.z = loc.getZ();
					track.nextGrowl = now + 20 + COMZombies.rand.nextInt(60);
					tracks.put(id, track);
					continue;
				}
				double moved = Math.hypot(loc.getX() - track.x, loc.getZ() - track.z);
				track.x = loc.getX();
				track.z = loc.getZ();
				if(!anyoneNear(players, loc))
					continue;

				// Footsteps: one per stride walked (a teleport or knockback is not walking).
				if(moved < 3)
					track.walked += moved;
				if(track.walked >= STRIDE)
				{
					track.walked -= STRIDE;
					float pitch = 0.9F + COMZombies.rand.nextFloat() * 0.2F;
					loc.getWorld().playSound(loc, "mz:zombie_step", SoundCategory.HOSTILE, 0.7F, pitch);
				}

				if(mob.hasMetadata(SPRINTER) && now >= track.nextGrowl)
				{
					track.nextGrowl = now + 80 + COMZombies.rand.nextInt(140);
					loc.getWorld().playSound(loc, "mz:zombie_sprint", SoundCategory.HOSTILE, 1.0F, 1.0F);
				}
			}
		}
		tracks.keySet().retainAll(seen);
	}

	private static boolean anyoneNear(List<Player> players, Location loc)
	{
		for(Player p : players)
			if(p.getWorld() == loc.getWorld() && p.getLocation().distanceSquared(loc) <= HEAR_RANGE * HEAR_RANGE)
				return true;
		return false;
	}
}
