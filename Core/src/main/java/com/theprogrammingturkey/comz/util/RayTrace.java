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

	//intersection detection for current raytrace
	public List<RayEntityIntersection> getZombieIntersects(World world, List<Mob> ents, double blocksAway, Game game)
	{
		Map<Entity, RayEntityIntersection> hit = new HashMap<>();
		List<Vector> positions = traverse(blocksAway, ACCURACY);
		for(Vector position : positions)
		{
			Location loc = new Location(world, position.getBlockX(), position.getBlockY(), position.getBlockZ());
			Block block = world.getBlockAt(loc);
			if(!block.isPassable() && game.barrierManager.getBarrier(loc) == null && block.getState().getType() != Material.BARRIER)
				break;

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