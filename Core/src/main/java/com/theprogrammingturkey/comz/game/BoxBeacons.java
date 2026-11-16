package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A blue beacon-style light beam above every mystery box that can be used right now (the current box, or every box during a
 * fire sale / with multiple boxes). It is made of display entities, so no real beacon is needed. Height and colour come from
 * config.gameSettings.boxBeacon (height 0 turns it off).
 */
public final class BoxBeacons
{
	private static final Map<RandomBox, BlockDisplay[]> BEAMS = new HashMap<>();

	private BoxBeacons()
	{
	}

	public static void start(Plugin plugin)
	{
		Bukkit.getScheduler().runTaskTimer(plugin, BoxBeacons::tick, 40L, 10L);
	}

	private static void tick()
	{
		float height = (float) COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.boxBeaconHeight", 40);
		Set<RandomBox> wanted = new HashSet<>();
		if(height > 0)
		{
			for(Game game : GameManager.INSTANCE.getGames())
			{
				if(game.getStatus() != Game.GameStatus.INGAME)
					continue;
				boolean all = game.boxManager.isMultiBox() || game.isFireSale();
				List<RandomBox> boxes = game.boxManager.getBoxes();
				for(RandomBox box : boxes)
					if(all || game.boxManager.getCurrentbox() == box)
						wanted.add(box);
			}
		}

		for(Iterator<Map.Entry<RandomBox, BlockDisplay[]>> it = BEAMS.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<RandomBox, BlockDisplay[]> e = it.next();
			if(!wanted.contains(e.getKey()) || !e.getValue()[0].isValid())
			{
				for(BlockDisplay d : e.getValue())
					d.remove();
				it.remove();
			}
		}
		for(RandomBox box : wanted)
			if(!BEAMS.containsKey(box))
			{
				BlockDisplay[] beam = spawn(box, height);
				if(beam != null)
					BEAMS.put(box, beam);
			}
	}

	/** The middle of the box: the block, or the middle of both halves of a double chest. */
	private static Location centre(RandomBox box)
	{
		Location loc = box.getLocation();
		if(!box.isChestBox())
		{
			// A sign box: the beam goes on the chest beside the sign, if there is one
			for(org.bukkit.block.BlockFace face : org.bukkit.block.BlockFace.values())
			{
				Block near = loc.getBlock().getRelative(face.getModX(), face.getModY(), face.getModZ());
				if(near.getType() == Material.CHEST)
				{
					loc = near.getLocation();
					break;
				}
			}
		}
		Location c = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY() + 1, loc.getBlockZ() + 0.5);
		if(loc.getBlock().getType() == Material.CHEST || loc.getBlock().getType() == Material.TRAPPED_CHEST)
		{
			Block other = BlockUtils.doubleChestPartner(loc.getBlock());
			if(other != null)
				c.add((other.getX() - loc.getBlockX()) * 0.5, 0, (other.getZ() - loc.getBlockZ()) * 0.5);
		}
		return c;
	}

	private static BlockDisplay[] spawn(RandomBox box, float height)
	{
		Location c = centre(box);
		if(c.getWorld() == null || !c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4))
			return null;
		// A bright core and a wider faint glow around it, like a beacon beam
		BlockDisplay core = beam(c, Material.LIGHT_BLUE_STAINED_GLASS, 0.22f, height);
		BlockDisplay glow = beam(c, Material.BLUE_STAINED_GLASS, 0.42f, height);
		return new BlockDisplay[] {core, glow};
	}

	private static BlockDisplay beam(Location c, Material material, float width, float height)
	{
		return c.getWorld().spawn(c, BlockDisplay.class, d ->
		{
			d.setPersistent(false);
			d.setBlock(material.createBlockData());
			d.setBrightness(new Display.Brightness(15, 15));
			d.setViewRange(8f);
			d.setTransformation(new Transformation(new Vector3f(-width / 2, 0, -width / 2), new AxisAngle4f(),
					new Vector3f(width, height, width), new AxisAngle4f()));
		});
	}

	/** Removes every beam (plugin disable). */
	public static void clearAll()
	{
		for(BlockDisplay[] beam : BEAMS.values())
			for(BlockDisplay d : beam)
				d.remove();
		BEAMS.clear();
	}
}
