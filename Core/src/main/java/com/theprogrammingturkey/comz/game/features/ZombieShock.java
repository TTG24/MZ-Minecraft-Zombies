package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.BloodMessages;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.nio.ByteBuffer;

/**
 * A zombie struck by the Wunderwaffe: it convulses on the spot for a moment (it can't move or hit anyone), crackling with
 * lightning, then drops dead; one in ten has its head pop (paid as an ordinary kill).
 * <ul>
 * <li>players with the client mod: "tacticalmovement:shock" (entity id int, animation 1-3 byte, ticks short): the mod plays
 * the zombie model's shock_N and draws arcs crawling over it;</li>
 * <li>players without it: the "mz_shock" metadata ("shock_N"), which TacticalMovementPlugin plays on the BetterModel zombie,
 * and sparks and little arcs of particles around it.</li>
 * </ul>
 */
public final class ZombieShock
{
	public static final String CHANNEL = "tacticalmovement:shock";
	public static final String META = "mz_shock";
	/** How long it convulses before it drops (ticks). */
	public static final int TICKS = 26;
	/** The chance its head pops when it dies. */
	private static final double HEAD_POP = 0.10;
	private static final double RANGE = 64;

	private ZombieShock()
	{
	}

	public static void register(COMZombies plugin)
	{
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
	}

	public static boolean isShocked(Mob mob)
	{
		return mob.hasMetadata(META);
	}

	/** Shocks the zombie, then kills it for the shooter (unless something else kills it first). */
	public static void shock(Game game, Player shooter, Mob mob)
	{
		if(isShocked(mob))
			return;
		int variant = 1 + COMZombies.rand.nextInt(3);
		mob.setMetadata(META, new FixedMetadataValue(COMZombies.getPlugin(), "shock_" + variant));
		mob.setAI(false);
		mob.setVelocity(new Vector());
		// its electrocuted cry (mz:zombie_shock, one of six, in the zombie-sounds pack), heard by everyone
		mob.getWorld().playSound(mob.getLocation(), "mz:zombie_shock", org.bukkit.SoundCategory.HOSTILE, 1f,
				0.95f + COMZombies.rand.nextFloat() * 0.1f);
		byte[] data = ByteBuffer.allocate(7).putInt(mob.getEntityId()).put((byte) variant).putShort((short) TICKS).array();
		for(Player p : mob.getWorld().getPlayers())
			if(p.getListeningPluginChannels().contains(CHANNEL) && p.getLocation().distanceSquared(mob.getLocation()) <= RANGE * RANGE)
				p.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, data);
		final long match = game.getMatchGeneration();
		final float yaw = mob.getLocation().getYaw();
		int[] tick = {0};
		int[] task = new int[1];
		task[0] = COMZombies.scheduleTask(0, 2, () ->
		{
			tick[0] += 2;
			if(!mob.isValid() || mob.isDead() || game.getMatchGeneration() != match || game.getStatus() != Game.GameStatus.INGAME)
			{
				org.bukkit.Bukkit.getScheduler().cancelTask(task[0]);
				mob.removeMetadata(META, COMZombies.getPlugin());
				if(mob.isValid() && !mob.isDead())
					mob.setAI(true);
				return;
			}
			crackle(mob, yaw);
			if(tick[0] >= TICKS)
			{
				org.bukkit.Bukkit.getScheduler().cancelTask(task[0]);
				die(game, shooter, mob);
			}
		});
	}

	/** Players without the mod: sparks over the body, a little arc or two, and a twitch. */
	private static void crackle(Mob mob, float yaw)
	{
		Location base = mob.getLocation();
		double h = mob.getHeight();
		for(Player p : mob.getWorld().getPlayers())
		{
			if(p.getListeningPluginChannels().contains(CHANNEL) || p.getLocation().distanceSquared(base) > RANGE * RANGE)
				continue;
			p.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, h * 0.55, 0), 6, 0.25, h * 0.3, 0.25, 0.15);
			// an arc: a short jagged run of light between two points on the body
			Vector a = new Vector(rnd(0.35), COMZombies.rand.nextDouble() * h, rnd(0.35));
			Vector b = new Vector(rnd(0.35), COMZombies.rand.nextDouble() * h, rnd(0.35));
			org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(150, 210, 255), 0.6f);
			for(int i = 0; i <= 6; i++)
			{
				Vector at = a.clone().multiply(1 - i / 6.0).add(b.clone().multiply(i / 6.0)).add(new Vector(rnd(0.08), rnd(0.08), rnd(0.08)));
				p.spawnParticle(Particle.DUST, base.getX() + at.getX(), base.getY() + at.getY(), base.getZ() + at.getZ(), 0, 0, 0, 0, 1, dust);
			}
		}
		// twitching about where it stands (seen by everyone; the mod's animation is on top)
		Location twitch = base.clone();
		twitch.setYaw(yaw + (float) rnd(30));
		mob.teleport(twitch);
	}

	private static double rnd(double amp)
	{
		return (COMZombies.rand.nextDouble() * 2 - 1) * amp;
	}

	private static void die(Game game, Player shooter, Mob mob)
	{
		mob.removeMetadata(META, COMZombies.getPlugin());
		boolean pop = COMZombies.rand.nextDouble() < HEAD_POP;
		if(pop)
		{
			// the head bursts (the mod's headless corpse, BetterModel's headshot death), paid as an ordinary kill
			Location head = mob.getLocation().add(0, mob.getHeight() * 0.88, 0);
			Vector dir = shooter.isOnline() && shooter.getWorld() == mob.getWorld()
					? head.toVector().subtract(shooter.getEyeLocation().toVector()).normalize() : new Vector(0, 1, 0);
			BloodMessages.nextHit(head.toVector(), dir, BloodMessages.HEADSHOT);
			game.popNextHead();
		}
		mob.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, mob.getLocation().add(0, mob.getHeight() / 2, 0), 25, 0.3, 0.6, 0.3, 0.3);
		game.damageMob(mob, shooter, (float) mob.getHealth() + 1000, false);
	}
}
