package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /zombies debug zombies}: shows the players who turn it on what each arena mob's AI is doing. Only they see it.
 * <ul>
 * <li>a label over each mob: its job (the window it is going to, or the player it hunts), how far along its route it is,
 * and how long it has made no progress;</li>
 * <li>its route as particles: green where it walks, yellow where it jumps up, blue where it drops; its goal in white;</li>
 * <li>red over a mob that has made no progress for a while.</li>
 * </ul>
 */
public final class ZombieDebug
{
	private static final Set<UUID> VIEWERS = new HashSet<>();
	private static final Map<UUID, TextDisplay> LABELS = new HashMap<>();
	private static final double RANGE_SQUARED = 48 * 48;
	private static final Particle.DustOptions WALK = new Particle.DustOptions(Color.fromRGB(60, 220, 60), 0.8f);
	private static final Particle.DustOptions JUMP = new Particle.DustOptions(Color.fromRGB(240, 220, 40), 0.9f);
	private static final Particle.DustOptions DROP = new Particle.DustOptions(Color.fromRGB(60, 120, 255), 0.9f);
	private static final Particle.DustOptions GOAL = new Particle.DustOptions(Color.WHITE, 1.4f);
	private static final Particle.DustOptions STUCK = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.6f);

	private ZombieDebug()
	{
	}

	/** Turns it on or off for the player. @return true if it is now on */
	public static boolean toggle(Player player)
	{
		if(VIEWERS.remove(player.getUniqueId()))
		{
			for(TextDisplay label : LABELS.values())
				if(label.isValid())
					player.hideEntity(COMZombies.getPlugin(), label);
			if(VIEWERS.isEmpty())
				clear();
			return false;
		}
		VIEWERS.add(player.getUniqueId());
		for(TextDisplay label : LABELS.values())
			if(label.isValid())
				player.showEntity(COMZombies.getPlugin(), label);
		return true;
	}

	/** Removes every label (debug off for everyone, or the plugin is stopping). */
	public static void clear()
	{
		for(TextDisplay label : LABELS.values())
			if(label.isValid())
				label.remove();
		LABELS.clear();
	}

	/** Called by ZombieBrain every few ticks. */
	static void update(long tick)
	{
		if(VIEWERS.isEmpty())
			return;
		List<Player> viewers = new ArrayList<>();
		for(Iterator<UUID> it = VIEWERS.iterator(); it.hasNext(); )
		{
			Player p = Bukkit.getPlayer(it.next());
			if(p == null || !p.isOnline())
				it.remove();
			else
				viewers.add(p);
		}
		if(viewers.isEmpty())
		{
			clear();
			return;
		}

		Set<UUID> alive = new HashSet<>();
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
				continue;
			for(Mob mob : game.spawnManager.getEntities())
			{
				if(!mob.isValid())
					continue;
				ZombieBrain.View view = ZombieBrain.view(mob, tick);
				if(view == null)
					continue;
				alive.add(mob.getUniqueId());
				label(mob, view, viewers);
				for(Player p : viewers)
					if(p.getWorld().equals(mob.getWorld()) && p.getLocation().distanceSquared(mob.getLocation()) < RANGE_SQUARED)
						draw(p, mob, view);
			}
		}
		// Labels of mobs that are gone
		for(Iterator<Map.Entry<UUID, TextDisplay>> it = LABELS.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<UUID, TextDisplay> e = it.next();
			if(!alive.contains(e.getKey()))
			{
				if(e.getValue().isValid())
					e.getValue().remove();
				it.remove();
			}
		}
	}

	private static void label(Mob mob, ZombieBrain.View view, List<Player> viewers)
	{
		Location at = mob.getLocation().add(0, mob.getHeight() + 0.55, 0);
		TextDisplay label = LABELS.get(mob.getUniqueId());
		if(label == null || !label.isValid() || label.getWorld() != mob.getWorld())
		{
			if(label != null && label.isValid())
				label.remove();
			label = mob.getWorld().spawn(at, TextDisplay.class, d ->
			{
				d.setVisibleByDefault(false);
				d.setPersistent(false);
				d.setBillboard(Display.Billboard.CENTER);
				d.setSeeThrough(true);
				d.setTeleportDuration(5);
			});
			for(Player p : viewers)
				p.showEntity(COMZombies.getPlugin(), label);
			LABELS.put(mob.getUniqueId(), label);
		}
		else
			label.teleport(at);
		StringBuilder text = new StringBuilder();
		text.append("&f").append(view.job());
		if(view.route() != null)
			text.append("\n&7route ").append(view.index()).append("/").append(view.route().size() - 1);
		else
			text.append(view.noRoute() ? "\n&cno way there" : "\n&7straight to it");
		if(view.stuckTicks() >= 40)
			text.append("\n&cno progress ").append(view.stuckTicks() / 20).append("s");
		label.text(Colors.legacy(text.toString().replace('&', '§')));
	}

	private static void draw(Player p, Mob mob, ZombieBrain.View view)
	{
		World world = mob.getWorld();
		if(view.goal() != null && view.goal().getWorld() == world)
			p.spawnParticle(Particle.DUST, view.goal().clone().add(0, 0.2, 0), 2, 0.05, 0.05, 0.05, 0, GOAL);
		if(view.stuckTicks() >= 60)
			p.spawnParticle(Particle.DUST, mob.getLocation().add(0, mob.getHeight() + 0.2, 0), 3, 0.15, 0.1, 0.15, 0, STUCK);
		List<int[]> route = view.route();
		if(route == null)
			return;
		double lastX = mob.getLocation().getX(), lastY = mob.getLocation().getY(), lastZ = mob.getLocation().getZ();
		for(int i = Math.max(1, view.index()); i < route.size(); i++)
		{
			int[] n = route.get(i);
			double x = n[0] + 0.5, y = ZombiePathing.floorOf(n), z = n[2] + 0.5;
			double rise = y - lastY;
			Particle.DustOptions colour = rise > ZombiePathing.STEP ? JUMP : rise < -ZombiePathing.STEP ? DROP : WALK;
			// a few dots along each step, a little above the floor
			for(int k = 1; k <= 3; k++)
			{
				double f = k / 3.0;
				p.spawnParticle(Particle.DUST, lastX + (x - lastX) * f, lastY + (y - lastY) * f + 0.15, lastZ + (z - lastZ) * f, 1, 0, 0, 0, 0, colour);
			}
			lastX = x;
			lastY = y;
			lastZ = z;
		}
	}
}
