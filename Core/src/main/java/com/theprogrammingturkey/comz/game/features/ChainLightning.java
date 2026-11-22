package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.util.GunAnimMessages;
import com.theprogrammingturkey.comz.util.RayTrace;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Wunderwaffe's shot (any gun with "chain" in guns.json): a bolt of lightning that kills the first zombie in its path,
 * then jumps to the nearest zombie it can see within "chain_range" blocks, and on from there, up to "chain" zombies in
 * all. Each one is shocked (ZombieShock): it convulses for a moment, then drops. Players with the client mod see a flickering bolt (GunAnimMessages.arc); everyone sees
 * sparks and hears it crack.
 */
public final class ChainLightning
{
	/** Ticks between jumps (so the bolt is seen travelling from one to the next). */
	private static final int JUMP_TICKS = 2;

	private ChainLightning()
	{
	}

	public static void fire(Player player, Game game, BaseGun gun, Location origin)
	{
		World world = player.getWorld();
		Vector dir = origin.getDirection();
		RayTrace ray = new RayTrace(origin.toVector(), dir);
		List<RayTrace.RayEntityIntersection> hits = ray.getZombieIntersects(world, game.spawnManager.getEntities(), gun.distance, game);
		if(hits.isEmpty())
		{
			// nothing in its path: it strikes whatever it reaches
			Vector end = ray.getPosition(gun.distance);
			var wall = world.rayTraceBlocks(origin, dir, gun.distance, org.bukkit.FluidCollisionMode.NEVER, true);
			if(wall != null)
				end = wall.getHitPosition();
			GunAnimMessages.arc(player, origin.toVector(), end, gun.particleColor, true);
			world.spawnParticle(Particle.ELECTRIC_SPARK, end.getX(), end.getY(), end.getZ(), 20, 0.2, 0.2, 0.2, 0.3);
			return;
		}
		Mob first = null;
		double best = Double.MAX_VALUE;
		for(RayTrace.RayEntityIntersection hit : hits)
		{
			double d = hit.hitEnt.getLocation().distanceSquared(origin);
			if(hit.hitEnt instanceof Mob m && d < best)
			{
				best = d;
				first = m;
			}
		}
		if(first == null)
			return;
		GunAnimMessages.arc(player, origin.toVector(), centre(first), gun.particleColor, true);
		Set<UUID> struck = new HashSet<>();
		struck.add(first.getUniqueId());
		Vector at = centre(first);
		strike(player, game, gun, first);
		jump(player, game, gun, at, struck, Math.max(0, gun.chain - 1), game.getMatchGeneration());
	}

	/** The next jump: from `from` (where the last zombie was) to the nearest one it can see. */
	private static void jump(Player player, Game game, BaseGun gun, Vector from, Set<UUID> struck, int left, long match)
	{
		if(left <= 0)
			return;
		COMZombies.scheduleTask(JUMP_TICKS, () ->
		{
			if(!player.isOnline() || game.getMatchGeneration() != match)
				return;
			World world = player.getWorld();
			Location fromLoc = from.toLocation(world);
			Mob next = null;
			double best = gun.chainRange * gun.chainRange;
			for(Mob m : game.spawnManager.getEntities())
			{
				if(m == null || m.isDead() || !m.isValid() || struck.contains(m.getUniqueId()) || m.getWorld() != world || ZombieShock.isShocked(m))
					continue;
				Vector c = centre(m);
				double d = c.distanceSquared(from);
				if(d >= best)
					continue;
				// only through the air: not through walls
				Vector to = c.clone().subtract(from);
				double len = to.length();
				if(len > 0.01 && world.rayTraceBlocks(fromLoc, to.normalize(), len, org.bukkit.FluidCollisionMode.NEVER, true) != null)
					continue;
				best = d;
				next = m;
			}
			if(next == null)
				return;
			Vector to = centre(next);
			struck.add(next.getUniqueId());
			GunAnimMessages.arc(player, from, to, gun.particleColor, false);
			strike(player, game, gun, next);
			jump(player, game, gun, to, struck, left - 1, match);
		});
	}

	private static void strike(Player player, Game game, BaseGun gun, Mob mob)
	{
		Location l = mob.getLocation().add(0, mob.getHeight() / 2, 0);
		World world = mob.getWorld();
		world.spawnParticle(Particle.ELECTRIC_SPARK, l, 30, 0.3, mob.getHeight() / 3, 0.3, 0.25);
		world.playSound(l, "mzguns:wunderwaffe.arc", SoundCategory.PLAYERS, 1.0f, 0.9f + COMZombies.rand.nextFloat() * 0.2f);
		// it convulses for a moment, then drops (ZombieShock)
		ZombieShock.shock(game, player, mob);
	}

	private static Vector centre(Mob mob)
	{
		return mob.getLocation().toVector().add(new Vector(0, mob.getHeight() * 0.55, 0));
	}
}
