package com.theprogrammingturkey.comz.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import com.theprogrammingturkey.comz.COMZombies;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Glowing coloured outlines around blocks that only one player sees, visible through walls.
 */
public final class Highlight
{
	private static final long EXPIRE_MS = 1500;
	private static final int MAX_PER_PLAYER = 1500; // /z showall draws a whole arena
	private static final Map<UUID, Map<String, Entry>> ACTIVE = new HashMap<>();
	private static int cleanupTask = -1;

	public static final Color BARRIER = Color.fromRGB(255, 200, 0);
	/** The door that is being added or edited right now (white), so it stands out from the doors /z showdoors draws. */
	public static final Color DOOR_EDITING = Color.fromRGB(255, 255, 255);
	public static final Color DOOR = Color.fromRGB(0, 200, 255);
	public static final Color LINKED_SPAWN = Color.fromRGB(60, 255, 60);
	public static final Color OTHER_SPAWN = Color.fromRGB(255, 50, 50);
	public static final Color INTACT = Color.fromRGB(60, 255, 60);
	public static final Color DAMAGED = Color.fromRGB(255, 220, 0);
	public static final Color BROKEN = Color.fromRGB(255, 50, 50);

	private Highlight()
	{
	}

	/** Outlines every block of the collection. Blocks in another world than the player's are skipped. */
	public static void blocks(Player player, Collection<Block> blocks, Color color)
	{
		blocks(player, blocks, color, 0);
	}

	public static void blocks(Player player, Collection<Block> blocks, Color color, int layer)
	{
		for(Block block : blocks)
			box(player, block.getLocation(), color, layer);
	}

	/**
	 * Outlines the block cell at the location with a glowing outline that only this player sees (through walls too).
	 * It is a glowing block display, kept alive for a short while: callers just keep calling this every few ticks and the
	 * same entity is reused, and it vanishes by itself shortly after they stop.
	 */
	public static void box(Player player, Location cell, Color color)
	{
		box(player, cell, color, 0);
	}

	/**
	 * Same, on its own layer: outlines of different layers on the same block do not fight over colour. Layer 1 is drawn
	 * slightly larger so both show nested.
	 */
	public static void box(Player player, Location cell, Color color, int layer)
	{
		if(cell.getWorld() == null || !cell.getWorld().equals(player.getWorld()))
			return;
		long now = System.currentTimeMillis();
		Map<String, Entry> mine = ACTIVE.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
		String key = cell.getWorld().getName() + ":" + cell.getBlockX() + ":" + cell.getBlockY() + ":" + cell.getBlockZ() + ":" + layer;
		Entry entry = mine.get(key);
		if(entry != null && entry.display.isValid())
		{
			entry.lastSeen = now;
			if(!entry.color.equals(color))
			{
				entry.display.setGlowColorOverride(color);
				entry.color = color;
			}
			return;
		}
		if(mine.size() >= MAX_PER_PLAYER)
			return;
		Location at = new Location(cell.getWorld(), cell.getBlockX(), cell.getBlockY(), cell.getBlockZ());
		final float off = layer == 0 ? 0.01f : layer == 2 ? 0.06f : 0.12f;
		BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, d ->
		{
			d.setVisibleByDefault(false);
			d.setPersistent(false);
			d.setBlock(Material.GLASS.createBlockData());
			d.setGlowing(true);
			d.setGlowColorOverride(color);
			d.setBrightness(new BlockDisplay.Brightness(15, 15));
			d.setTransformation(new Transformation(new Vector3f(-off, -off, -off), new AxisAngle4f(), new Vector3f(1 + 2 * off, 1 + 2 * off, 1 + 2 * off), new AxisAngle4f()));
			d.setViewRange(2.0f);
		});
		player.showEntity(COMZombies.getPlugin(), display);
		mine.put(key, new Entry(display, color, now));
		startCleanup();
	}

	/**
	 * A thin glowing bar from a to b that only this player sees (through walls too), kept alive like {@link #box}: keep
	 * calling it every few ticks while it should show. The entity sits in the middle so it is visible from either end.
	 */
	public static void line(Player player, Location a, Location b, Color color)
	{
		if(a.getWorld() == null || !a.getWorld().equals(player.getWorld()) || !a.getWorld().equals(b.getWorld()))
			return;
		long now = System.currentTimeMillis();
		Map<String, Entry> mine = ACTIVE.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
		String key = "line:" + a.getWorld().getName() + ":" + a.getX() + ":" + a.getY() + ":" + a.getZ() + ">" + b.getX() + ":" + b.getY() + ":" + b.getZ();
		Entry entry = mine.get(key);
		if(entry != null && entry.display.isValid())
		{
			entry.lastSeen = now;
			if(!entry.color.equals(color))
			{
				entry.display.setGlowColorOverride(color);
				entry.color = color;
			}
			return;
		}
		if(mine.size() >= MAX_PER_PLAYER)
			return;
		Vector3f dir = new Vector3f((float) (b.getX() - a.getX()), (float) (b.getY() - a.getY()), (float) (b.getZ() - a.getZ()));
		float length = dir.length();
		if(length < 0.1f)
			return;
		final float width = 0.12f;
		org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir.normalize(new Vector3f()));
		// The block is the unit cube from the origin: centre it on the line, then turn it along the line
		Vector3f translation = rotation.transform(new Vector3f(-width / 2, -width / 2, -length / 2));
		Location middle = a.clone().add(b).multiply(0.5);
		BlockDisplay display = middle.getWorld().spawn(middle, BlockDisplay.class, d ->
		{
			d.setVisibleByDefault(false);
			d.setPersistent(false);
			d.setBlock(Material.WHITE_CONCRETE.createBlockData());
			d.setGlowing(true);
			d.setGlowColorOverride(color);
			d.setBrightness(new BlockDisplay.Brightness(15, 15));
			d.setTransformation(new Transformation(translation, rotation, new Vector3f(width, width, length), new org.joml.Quaternionf()));
			d.setViewRange(8.0f);
		});
		player.showEntity(COMZombies.getPlugin(), display);
		mine.put(key, new Entry(display, color, now));
		startCleanup();
	}

	private static void startCleanup()
	{
		if(cleanupTask != -1)
			return;
		cleanupTask = COMZombies.scheduleTask(10, 10, () ->
		{
			long now = System.currentTimeMillis();
			for(Iterator<Map.Entry<UUID, Map<String, Entry>>> players = ACTIVE.entrySet().iterator(); players.hasNext(); )
			{
				Map<String, Entry> mine = players.next().getValue();
				mine.values().removeIf(e ->
				{
					if(now - e.lastSeen <= EXPIRE_MS && e.display.isValid())
						return false;
					e.display.remove();
					return true;
				});
				if(mine.isEmpty())
					players.remove();
			}
			if(ACTIVE.isEmpty())
			{
				Bukkit.getScheduler().cancelTask(cleanupTask);
				cleanupTask = -1;
			}
		});
	}

	/** Removes every outline this player sees right now (anything still being drawn comes back on its next redraw). */
	public static void clear(Player player)
	{
		Map<String, Entry> mine = ACTIVE.remove(player.getUniqueId());
		if(mine != null)
			for(Entry e : mine.values())
				e.display.remove();
	}

	/** Removes every outline at once (plugin disable). */
	public static void clearAll()
	{
		for(Map<String, Entry> mine : ACTIVE.values())
			for(Entry e : mine.values())
				e.display.remove();
		ACTIVE.clear();
		if(cleanupTask != -1)
			Bukkit.getScheduler().cancelTask(cleanupTask);
		cleanupTask = -1;
	}

	private static final class Entry
	{
		final BlockDisplay display;
		Color color;
		long lastSeen;

		Entry(BlockDisplay display, Color color, long lastSeen)
		{
			this.display = display;
			this.color = color;
			this.lastSeen = lastSeen;
		}
	}
}
