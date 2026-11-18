package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.util.ApiCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.nio.ByteBuffer;

/**
 * Crawlers: a grenade that does not kill a zombie may blow its legs off. It falls, then crawls on its arms: slower, low
 * to the ground (shots and headshots are checked against its body lying along the ground, not the standing hitbox).
 *
 * The chance grows with how much of its health the blast took: under 15 % never, then about 1.2x the share taken, at
 * most 90 %. Zombie health goes up every round, so crawlers come mostly from early rounds and close blasts.
 *
 * Marked with the "mz_crawler" metadata, which TacticalMovementPlugin reads to play the crawler animations to players
 * without the client mod; players with it are sent {@link #CHANNEL} (the zombie's entity id, and whether it just
 * happened), now and whenever they start seeing that zombie.
 */
public final class Crawlers implements Listener
{
	public static final String META = "mz_crawler";
	public static final String CHANNEL = "tacticalmovement:crawler";
	/** The fall (the legs blowing off) takes this long; the zombie stays put meanwhile. */
	public static final int FALL_TICKS = 22;
	/** A crawler's speed, as a share of what it had standing. */
	private static final double SPEED = 0.35;

	// The crawler's body in its own frame (blocks): forward along its facing, sideways, up. The head end is a headshot.
	private static final double BACK = 0.6, FRONT = 1.0, HALF_WIDTH = 0.4, TOP = 0.65, HEAD_FROM = 0.45, HEAD_ABOVE = 0.15;

	private Crawlers()
	{
	}

	public static void register(COMZombies plugin)
	{
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
		plugin.getServer().getPluginManager().registerEvents(new Crawlers(), plugin);
	}

	public static boolean isCrawler(Entity entity)
	{
		return entity.hasMetadata(META);
	}

	/**
	 * After a grenade hit a zombie for {@code damage} out of {@code healthBefore}: maybe blow its legs off.
	 */
	public static void afterBlast(Mob mob, double damage, double healthBefore)
	{
		if(!(mob instanceof Zombie) || !mob.isValid() || mob.isDead() || isCrawler(mob) || mob.hasMetadata("mz_rise_ticks")
				|| healthBefore <= 0)
			return;
		double share = damage / healthBefore;
		if(share < 0.15)
			return;
		if(COMZombies.rand.nextDouble() < Math.min(0.9, share * 1.2))
			make(mob);
	}

	/** Blows the zombie's legs off: gore, it falls (staying put while it does), then crawls on at a fraction of its speed. */
	public static void make(Mob mob)
	{
		mob.setMetadata(META, new FixedMetadataValue(COMZombies.getPlugin(), true));
		Location at = mob.getLocation();
		at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 40, 0.25, 0.3, 0.25, 0,
				Material.REDSTONE_BLOCK.createBlockData());
		at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1f, 0.6f);
		at.getWorld().playSound(at, Sound.BLOCK_HONEY_BLOCK_BREAK, SoundCategory.HOSTILE, 1f, 0.5f);
		for(Player p : at.getWorld().getPlayers())
			send(p, mob, true);

		AttributeInstance speed = mob.getAttribute(ApiCompat.MOVEMENT_SPEED);
		if(speed != null)
		{
			double standing = speed.getBaseValue();
			speed.setBaseValue(0);
			Bukkit.getScheduler().runTaskLater(COMZombies.getPlugin(), () ->
			{
				if(mob.isValid())
					speed.setBaseValue(standing * SPEED);
			}, FALL_TICKS);
		}
	}

	/** Tells a player with the client mod that this zombie is a crawler (players without it see BetterModel's). */
	private static void send(Player player, Entity zombie, boolean fresh)
	{
		if(!player.getListeningPluginChannels().contains(CHANNEL))
			return;
		player.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, ByteBuffer.allocate(5).putInt(zombie.getEntityId()).put((byte) (fresh ? 1 : 0)).array());
	}

	/** A player starting to see a crawler (walking up to it, joining): their client is told too, once it has the zombie. */
	@EventHandler
	public void onTrack(io.papermc.paper.event.player.PlayerTrackEntityEvent event)
	{
		Entity e = event.getEntity();
		if(!isCrawler(e))
			return;
		Player p = event.getPlayer();
		Bukkit.getScheduler().runTask(COMZombies.getPlugin(), () ->
		{
			if(p.isOnline() && e.isValid())
				send(p, e, false);
		});
	}

	// ------------------------------------------------------------------------------------------------------- hitbox

	/** Where {@code point} is in the crawler's own frame: (forward, sideways, up) from its position. */
	private static double[] local(LivingEntity crawler, Vector point)
	{
		Location at = crawler.getLocation();
		double yaw = Math.toRadians(crawler.getBodyYaw());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double dx = point.getX() - at.getX(), dz = point.getZ() - at.getZ();
		return new double[] {dx * fx + dz * fz, dx * -fz + dz * fx, point.getY() - at.getY()};
	}

	/** Is the point inside the crawler's body (lying along the ground, head first)? */
	public static boolean contains(LivingEntity crawler, Vector point)
	{
		double[] l = local(crawler, point);
		return l[0] >= -BACK && l[0] <= FRONT && Math.abs(l[1]) <= HALF_WIDTH && l[2] >= -0.05 && l[2] <= TOP;
	}

	/** Is a hit at the point (inside its body) on the crawler's head? */
	public static boolean isHeadshot(LivingEntity crawler, Vector point)
	{
		double[] l = local(crawler, point);
		return l[0] >= HEAD_FROM && l[2] >= HEAD_ABOVE;
	}
}
