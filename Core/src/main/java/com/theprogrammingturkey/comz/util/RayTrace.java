package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.game.Game;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Thanks CJP10 for the base code!
https://www.spigotmc.org/threads/hitboxes-and-ray-tracing.174358/
 */
public class RayTrace
{
	private static final float ACCURACY = 0.1f;
	//origin = start position
	//direction = direction in which the raytrace will go
	private final Vector origin;
	private final Vector direction;

	public RayTrace(Vector origin, Vector direction)
	{
		this.origin = origin;
		this.direction = direction;
	}

	//get a point on the raytrace at X blocks away
	public Vector getPosition(double blocksAway)
	{
		return origin.clone().add(direction.clone().multiply(blocksAway));
	}

	//checks if a position is on contained within the position
	public boolean isOnLine(Vector position)
	{
		double t = (position.getX() - origin.getX()) / direction.getX();
		return position.getBlockY() == origin.getY() + (t * direction.getY()) && position.getBlockZ() == origin.getZ() + (t * direction.getZ());
	}

	//get all positions on a raytrace
	public List<Vector> traverse(double blocksAway, float accuracy)
	{
		List<Vector> positions = new ArrayList<>();
		for(double d = 0; d <= blocksAway; d += accuracy)
			positions.add(getPosition(d));
		return positions;
	}

	/** How far the last {@link #getZombieIntersects} shot got before a block stopped it (its full range if none did). */
	private double stoppedAt = -1;

	public double getStoppedAt()
	{
		return stoppedAt;
	}

	/** The point where the last shot stopped (the block it hit, or the end of its range). */
	public Vector stopPosition()
	{
		return origin.clone().add(direction.clone().normalize().multiply(Math.max(0, stoppedAt)));
	}

	/**
	 * How far along the ray a shot first touches a block, or blocksAway if it touches none. Each block is tested against its
	 * real shape (its collision boxes, cut off at the top of its own cell), not as a full cube: a shot goes through the
	 * gaps beside iron bars, fence posts and panes, over slabs and stairs, and through open fence gates and trapdoors. The
	 * arena's own barrier blocks and barrier-material blocks never stop it.
	 */
	public double blockStop(World world, double blocksAway, Game game)
	{
		Vector dir = direction.clone().normalize();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		// Small steps just to find the cells along the ray; the hit inside each cell is exact
		for(double d = 0; d <= blocksAway + 0.05; d += 0.05)
		{
			Vector p = origin.clone().add(dir.clone().multiply(d));
			int bx = p.getBlockX(), by = p.getBlockY(), bz = p.getBlockZ();
			if(!seen.add(((long) (bx & 0x3FFFFFF) << 38) | ((long) (bz & 0x3FFFFFF) << 12) | (by & 0xFFF)))
				continue;
			if(!world.isChunkLoaded(bx >> 4, bz >> 4))
				return Math.min(d, blocksAway);
			Block block = world.getBlockAt(bx, by, bz);
			if(block.isPassable() || block.getType() == Material.BARRIER || game.barrierManager.getBarrier(block.getLocation()) != null)
				continue;
			double best = Double.MAX_VALUE;
			for(org.bukkit.util.BoundingBox box : block.getCollisionShape().getBoundingBoxes())
			{
				double t = enter(dir, bx + box.getMinX(), by + box.getMinY(), bz + box.getMinZ(),
						bx + box.getMaxX(), by + Math.min(1.0, box.getMaxY()), bz + box.getMaxZ());
				if(t >= 0 && t < best)
					best = t;
			}
			if(best <= blocksAway)
				return best;
		}
		return blocksAway;
	}

	/** Where along the ray (from the origin, unit direction) it enters the box; -1 if it misses. */
	private double enter(Vector dir, double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
	{
		double[] o = {origin.getX(), origin.getY(), origin.getZ()}, v = {dir.getX(), dir.getY(), dir.getZ()};
		double[] lo = {minX, minY, minZ}, hi = {maxX, maxY, maxZ};
		double tMin = 0, tMax = Double.MAX_VALUE;
		for(int i = 0; i < 3; i++)
		{
			if(Math.abs(v[i]) < 1.0E-9)
			{
				if(o[i] < lo[i] || o[i] > hi[i])
					return -1;
				continue;
			}
			double t1 = (lo[i] - o[i]) / v[i], t2 = (hi[i] - o[i]) / v[i];
			tMin = Math.max(tMin, Math.min(t1, t2));
			tMax = Math.min(tMax, Math.max(t1, t2));
			if(tMin > tMax)
				return -1;
		}
		return tMin;
	}

	//intersection detection for current raytrace
	public List<RayEntityIntersection> getZombieIntersects(World world, List<Mob> ents, double blocksAway, Game game)
	{
		Map<Entity, RayEntityIntersection> hit = new HashMap<>();
		// Only as far as the first block it really touches (see blockStop); positions are along the normalized direction
		double stop = blockStop(world, blocksAway, game);
		stoppedAt = stop;
		double len = direction.length();
		List<Vector> positions = traverse(len < 1.0E-9 ? 0 : stop / len, (float) (ACCURACY / Math.max(len, 1.0E-9)));
		for(Vector position : positions)
		{
			for(Entity ent : ents)
				if(!hit.containsKey(ent) && (ent instanceof org.bukkit.entity.LivingEntity living
						&& com.theprogrammingturkey.comz.game.Crawlers.isCrawler(ent)
						? com.theprogrammingturkey.comz.game.Crawlers.contains(living, position)
						: inModel(ent, position)))
					hit.put(ent, new RayEntityIntersection(ent, position));
		}
		return new ArrayList<>(hit.values());
	}

	/**
	 * Inside a standing zombie as it is drawn (the custom model, mod or BetterModel): 0.9 wide with its arms and shoulders
	 * (vanilla's box is 0.6, so shots through an arm or shoulder went past), and up to the top of its head at 2.05. Babies
	 * and anything that isn't a zombie keep their own box.
	 */
	private static boolean inModel(Entity ent, Vector position)
	{
		org.bukkit.util.BoundingBox box = ent.getBoundingBox();
		if(ent instanceof org.bukkit.entity.Zombie z && z.isAdult())
		{
			Location at = ent.getLocation();
			box = new org.bukkit.util.BoundingBox(at.getX() - 0.45, at.getY(), at.getZ() - 0.45, at.getX() + 0.45, at.getY() + 2.05, at.getZ() + 0.45);
		}
		return intersects(position, box.getMin(), box.getMax());
	}

	//general intersection detection
	public static boolean intersects(Vector position, Vector min, Vector max)
	{
		if(position.getX() < min.getX() || position.getX() > max.getX())
			return false;
		else if(position.getY() < min.getY() || position.getY() > max.getY())
			return false;
		else
			return !(position.getZ() < min.getZ()) && !(position.getZ() > max.getZ());
	}

	public void showParticles(World world, double blocksAway, float seperation, Color color)
	{
		for(Vector position : traverse(blocksAway, seperation))
			world.spawnParticle(Particle.DUST, position.getX(), position.getY(), position.getZ(), 0, 0, 0, 0, 1, new Particle.DustOptions(color, 1));
	}


	public static class RayEntityIntersection
	{
		public Entity hitEnt;
		public Vector intersection;

		public RayEntityIntersection(Entity hitEnt, Vector intersection)
		{
			this.hitEnt = hitEnt;
			this.intersection = intersection;
		}
	}
}