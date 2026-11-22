package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How mystery boxes look in a running match, made of item displays with the guns pack's sprites and models (so every player
 * sees it, with or without the client mod):
 * <ul>
 * <li>a box that can be used (the current one; every one in a fire sale or with multiple boxes) has a soft glowing aura (a
 * halo behind it that always faces the viewer, and a glow pooled on the floor) that slowly breathes, a glowing "?" floating
 * over its middle (bobbing, its glow pulsing; hidden while the box is spinning), and a beam of light up to the sky that
 * shimmers. Each player has their own beam, which fades as they get near the box and is gone close up;</li>
 * <li>every other box spot looks smashed: its chest is swapped, for the players only (fake block changes; it stays a chest
 * on the server, so the map isn't changed), for an invisible barrier with a crumbled box model in it. The real chest is
 * shown again as soon as the box is back there or the match ends.</li>
 * </ul>
 * Beam height: config.gameSettings.boxBeaconHeight (0: no beam).
 */
public final class BoxEffects
{
	/** Ticks between updates (and each update's interpolation). */
	private static final int PERIOD = 5;
	/** The beam fades in steps (fx_beam_0..3) as a player comes within these distances (blocks), and is gone inside the last. */
	private static final double[] BEAM_FADE = {20, 15, 11, 7};
	/** Players this far away (blocks) don't get a beam (or the smashed look) sent at all. */
	private static final double RANGE = 192;

	private static final class Active
	{
		Location centre;
		boolean twoWide;
		ItemDisplay halo, floor, qmark;
		int qLevel = -1;
		final Map<UUID, ItemDisplay> beams = new HashMap<>();
		final Map<UUID, Integer> beamLevels = new HashMap<>();
		final float phase = COMZombies.rand.nextFloat() * 6.28f;
	}

	private static final class Broken
	{
		final List<Block> chests = new ArrayList<>();
		final List<ItemDisplay> models = new ArrayList<>();
		final Set<UUID> sentTo = new HashSet<>();
	}

	private static final Map<RandomBox, Active> ACTIVE = new HashMap<>();
	private static final Map<RandomBox, Broken> BROKEN = new HashMap<>();
	private static long ticks;
	private static Plugin plugin;

	private BoxEffects()
	{
	}

	public static void start(Plugin p)
	{
		plugin = p;
		Bukkit.getScheduler().runTaskTimer(p, BoxEffects::tick, 40L, PERIOD);
	}

	private static void tick()
	{
		ticks += PERIOD;
		float height = (float) COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.boxBeaconHeight", 40);
		Set<RandomBox> active = new HashSet<>();
		Set<RandomBox> broken = new HashSet<>();
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
				continue;
			boolean all = game.boxManager.isMultiBox() || game.isFireSale();
			for(RandomBox box : game.boxManager.getBoxes())
			{
				if(all || game.boxManager.getCurrentbox() == box)
					active.add(box);
				else
					broken.add(box);
			}
		}

		// ---- boxes in use
		for(Iterator<Map.Entry<RandomBox, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<RandomBox, Active> e = it.next();
			if(!active.contains(e.getKey()) || !e.getValue().halo.isValid())
			{
				remove(e.getValue());
				it.remove();
			}
		}
		for(RandomBox box : active)
		{
			Active a = ACTIVE.get(box);
			if(a == null)
			{
				a = spawnActive(box);
				if(a == null)
					continue;
				ACTIVE.put(box, a);
			}
			animate(box, a, height);
		}

		// ---- the spots it isn't at
		for(Iterator<Map.Entry<RandomBox, Broken>> it = BROKEN.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<RandomBox, Broken> e = it.next();
			if(!broken.contains(e.getKey()))
			{
				restore(e.getValue());
				it.remove();
			}
		}
		for(RandomBox box : broken)
		{
			Broken b = BROKEN.get(box);
			if(b == null)
			{
				b = spawnBroken(box);
				if(b == null)
					continue;
				BROKEN.put(box, b);
			}
			// (again now and then: a chunk sent afresh, or a player who just came, shows the real chest)
			if(b.sentTo.isEmpty() || ticks % 20 == 0)
				sendBroken(b);
		}
	}

	// ---------------------------------------------------------------------------------------------------- in use

	private static Active spawnActive(RandomBox box)
	{
		List<Block> chests = chests(box);
		Location c = centre(box, chests);
		if(c.getWorld() == null || !c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4))
			return null;
		Active a = new Active();
		a.centre = c;
		a.twoWide = chests.size() > 1;
		a.halo = sprite(c.clone().add(0, -0.45, 0), "fx_halo", Display.Billboard.CENTER, true);
		a.floor = sprite(c.clone().add(0, -0.97, 0), "fx_halo", Display.Billboard.FIXED, true);
		a.qmark = sprite(c.clone(), "fx_qmark_0", Display.Billboard.VERTICAL, true);
		a.qLevel = 0;
		return a;
	}

	private static void animate(RandomBox box, Active a, float height)
	{
		double t = ticks + a.phase * 20;
		// the aura: breathing, the halo and the floor glow out of step
		float halo = (a.twoWide ? 2.9f : 2.1f) * (1 + 0.07f * (float) Math.sin(t * 0.15));
		set(a.halo, new Vector3f(), new Quaternionf(), new Vector3f(halo, halo, halo));
		float floor = (a.twoWide ? 3.6f : 2.6f) * (1 + 0.07f * (float) Math.sin(t * 0.15 + Math.PI));
		set(a.floor, new Vector3f(), new Quaternionf().rotateX((float) -Math.PI / 2), new Vector3f(floor, floor, floor));
		// the "?": bobbing over the middle, its glow pulsing; out of the way while the box is spinning
		float q = box.canActivate() ? 0.75f : 0f;
		set(a.qmark, new Vector3f(0, 0.8f + 0.09f * (float) Math.sin(t * 0.12), 0), new Quaternionf(), new Vector3f(q, q, q));
		int level = (int) Math.round(Math.sin(t * 0.2) + 1);
		if(level != a.qLevel)
		{
			a.qmark.setItemStack(item("fx_qmark_" + level));
			a.qLevel = level;
		}
		beams(a, height, t);
	}

	/** Each player's own beam: fainter the nearer they are, none close up (or when the beam is turned off). */
	private static void beams(Active a, float height, double t)
	{
		Set<UUID> seen = new HashSet<>();
		if(height > 0)
		{
			for(Player p : a.centre.getWorld().getPlayers())
			{
				double dx = p.getLocation().getX() - a.centre.getX(), dz = p.getLocation().getZ() - a.centre.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				if(d > RANGE)
					continue;
				int level = -1;
				for(int i = 0; i < BEAM_FADE.length; i++)
					if(d >= BEAM_FADE[i])
					{
						level = i;
						break;
					}
				if(level < 0)
					continue;
				seen.add(p.getUniqueId());
				ItemDisplay beam = a.beams.get(p.getUniqueId());
				if(beam == null || !beam.isValid())
				{
					beam = sprite(a.centre.clone(), "fx_beam_" + level, Display.Billboard.VERTICAL, false);
					p.showEntity(plugin, beam);
					a.beams.put(p.getUniqueId(), beam);
					a.beamLevels.put(p.getUniqueId(), level);
				}
				else if(a.beamLevels.get(p.getUniqueId()) != level)
				{
					beam.setItemStack(item("fx_beam_" + level));
					a.beamLevels.put(p.getUniqueId(), level);
				}
				// shimmering: its width wavers
				float w = (a.twoWide ? 1.6f : 1.3f) * (1 + 0.12f * (float) Math.sin(t * 0.21) + 0.05f * (float) Math.sin(t * 0.53));
				set(beam, new Vector3f(0, height / 2, 0), new Quaternionf(), new Vector3f(w, height, 1));
			}
		}
		for(Iterator<Map.Entry<UUID, ItemDisplay>> it = a.beams.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<UUID, ItemDisplay> e = it.next();
			if(!seen.contains(e.getKey()))
			{
				e.getValue().remove();
				a.beamLevels.remove(e.getKey());
				it.remove();
			}
		}
	}

	private static void remove(Active a)
	{
		a.halo.remove();
		a.floor.remove();
		a.qmark.remove();
		for(ItemDisplay beam : a.beams.values())
			beam.remove();
		a.beams.clear();
	}

	// ---------------------------------------------------------------------------------------------------- smashed

	private static Broken spawnBroken(RandomBox box)
	{
		List<Block> chests = chests(box);
		if(chests.isEmpty())
			return null;
		Block first = chests.get(0);
		if(first.getWorld() == null || !first.getWorld().isChunkLoaded(first.getX() >> 4, first.getZ() >> 4))
			return null;
		Broken b = new Broken();
		b.chests.addAll(chests);
		for(Block chest : chests)
		{
			// turned so its front is where the chest's front was
			float yaw = 0;
			if(chest.getBlockData() instanceof Directional dir)
				yaw = switch(dir.getFacing())
				{
					case SOUTH -> (float) Math.PI;
					case EAST -> (float) -Math.PI / 2;
					case WEST -> (float) Math.PI / 2;
					default -> 0f;
				};
			final float turn = yaw;
			ItemDisplay model = chest.getWorld().spawn(chest.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, d ->
			{
				d.setPersistent(false);
				d.setItemStack(item("box_broken"));
				d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
				d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(turn), new Vector3f(1, 1, 1), new Quaternionf()));
			});
			b.models.add(model);
		}
		return b;
	}

	private static void sendBroken(Broken b)
	{
		BlockData barrier = Material.BARRIER.createBlockData();
		Location at = b.chests.get(0).getLocation();
		for(Player p : at.getWorld().getPlayers())
		{
			if(p.getLocation().distanceSquared(at) > RANGE * RANGE)
				continue;
			for(Block chest : b.chests)
				p.sendBlockChange(chest.getLocation(), barrier);
			b.sentTo.add(p.getUniqueId());
		}
	}

	/** The real chest back for everyone shown the smashed one. */
	private static void restore(Broken b)
	{
		for(ItemDisplay model : b.models)
			model.remove();
		for(UUID id : b.sentTo)
		{
			Player p = Bukkit.getPlayer(id);
			if(p == null || !p.getWorld().equals(b.chests.get(0).getWorld()))
				continue;
			for(Block chest : b.chests)
				p.sendBlockChange(chest.getLocation(), chest.getBlockData());
		}
		b.sentTo.clear();
	}

	// ---------------------------------------------------------------------------------------------------- helpers

	/** The box's chest: its own (both halves of a double chest), or for a sign box the chest beside the sign. */
	private static List<Block> chests(RandomBox box)
	{
		List<Block> out = new ArrayList<>();
		Block block = box.getLocation().getBlock();
		if(!box.isChestBox())
		{
			block = null;
			for(BlockFace face : BlockFace.values())
			{
				Block near = box.getLocation().getBlock().getRelative(face.getModX(), face.getModY(), face.getModZ());
				if(near.getType() == Material.CHEST || near.getType() == Material.TRAPPED_CHEST)
				{
					block = near;
					break;
				}
			}
		}
		if(block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST))
			return out;
		out.add(block);
		Block other = BlockUtils.doubleChestPartner(block);
		if(other != null)
			out.add(other);
		return out;
	}

	/** The top middle of the box (of both halves of a double chest); the sign's block for a sign box with no chest. */
	private static Location centre(RandomBox box, List<Block> chests)
	{
		Location loc = chests.isEmpty() ? box.getLocation() : chests.get(0).getLocation();
		Location c = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY() + 1, loc.getBlockZ() + 0.5);
		if(chests.size() > 1)
			c.add((chests.get(1).getX() - loc.getBlockX()) * 0.5, 0, (chests.get(1).getZ() - loc.getBlockZ()) * 0.5);
		return c;
	}

	private static ItemStack item(String model)
	{
		ItemStack stack = new ItemStack(Material.PAPER);
		ItemMeta meta = stack.getItemMeta();
		meta.setItemModel(new NamespacedKey("mzguns", model));
		stack.setItemMeta(meta);
		return stack;
	}

	/** A glowing sprite (full bright). shared: seen by everyone; otherwise by no one until shown to a player. */
	private static ItemDisplay sprite(Location at, String model, Display.Billboard billboard, boolean shared)
	{
		return at.getWorld().spawn(at, ItemDisplay.class, d ->
		{
			d.setPersistent(false);
			d.setVisibleByDefault(shared);
			d.setItemStack(item(model));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setBillboard(billboard);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setViewRange(8f);
			d.setShadowRadius(0);
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0), new Quaternionf()));
		});
	}

	/** Moves a display smoothly (over the update's ticks) to this transformation. */
	private static void set(ItemDisplay d, Vector3f translation, Quaternionf rotation, Vector3f scale)
	{
		d.setInterpolationDelay(0);
		d.setInterpolationDuration(PERIOD);
		d.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
	}

	/** Removes everything and shows the real chests again (plugin disable). */
	public static void clearAll()
	{
		for(Active a : ACTIVE.values())
			remove(a);
		ACTIVE.clear();
		for(Broken b : BROKEN.values())
			restore(b);
		BROKEN.clear();
	}
}
