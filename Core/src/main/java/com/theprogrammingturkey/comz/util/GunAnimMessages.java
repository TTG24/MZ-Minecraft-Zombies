package com.theprogrammingturkey.comz.util;

import java.nio.ByteBuffer;

import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;

/**
 * Tells players with the client mod (MZ-Movement) that someone fired their gun or started reloading it, so the gun and
 * both arms animate, in first person for the shooter and in third person for everyone near them. Players without the
 * mod aren't listening on the channel and are skipped.
 * <p>
 * Wire format (the mod's GunAnimPayload): the player's entity id (int), the kind (byte: 0 shot, 1 reload), and how
 * long it lasts in ticks (short: the gun's fire delay, or the reload time).
 */
public final class GunAnimMessages
{
	public static final String CHANNEL = "tacticalmovement:gun";
	/** 2: drinking a perk (the bottle is in the off hand); 3: a gun just picked up (bought or from the box): looked over. */
	public static final byte SHOT = 0, RELOAD = 1, DRINK = 2, PICKUP = 3;
	private static final double RANGE_SQUARED = 64 * 64;

	private GunAnimMessages()
	{
	}

	/** A shot's path for the mod to draw as a tracer: shooter id (int), from and to (3 doubles each), colour (int rgb). */
	public static final String TRACER = "tacticalmovement:tracer";

	public static void register(COMZombies plugin)
	{
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, TRACER);
	}

	/** Whether this player's client draws tracers (has the mod). */
	public static boolean drawsTracers(Player p)
	{
		return p.getListeningPluginChannels().contains(TRACER);
	}

	/** A shot from `from` to `to`: a tracer for everyone near with the mod, the old particle line for everyone else. */
	public static void tracer(Player shooter, org.bukkit.util.Vector from, org.bukkit.util.Vector to, org.bukkit.Color color)
	{
		byte[] data = ByteBuffer.allocate(4 + 48 + 4).putInt(shooter.getEntityId())
				.putDouble(from.getX()).putDouble(from.getY()).putDouble(from.getZ())
				.putDouble(to.getX()).putDouble(to.getY()).putDouble(to.getZ()).putInt(color.asRGB()).array();
		org.bukkit.util.Vector step = to.clone().subtract(from);
		double len = step.length();
		for(Player p : shooter.getWorld().getPlayers())
		{
			if(p.getLocation().distanceSquared(shooter.getLocation()) > RANGE_SQUARED)
				continue;
			if(drawsTracers(p))
			{
				p.sendPluginMessage(COMZombies.getPlugin(), TRACER, data);
				continue;
			}
			if(len < 1.0E-6)
				continue;
			org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1);
			for(double d = 0; d <= len; d += 0.5)
			{
				org.bukkit.util.Vector at = from.clone().add(step.clone().multiply(d / len));
				p.spawnParticle(org.bukkit.Particle.DUST, at.getX(), at.getY(), at.getZ(), 0, 0, 0, 0, 1, dust);
			}
		}
	}

	/**
	 * A bolt of lightning (the Wunderwaffe) from `from` to `to`: the mod draws it as a flickering bolt (the colour's top byte
	 * says so: 1 from the shooter's gun, 2 a jump between zombies; older mods ignore it and draw a tracer), everyone else
	 * sees a jagged line of sparks.
	 */
	public static void arc(Player shooter, org.bukkit.util.Vector from, org.bukkit.util.Vector to, org.bukkit.Color color, boolean fromGun)
	{
		int flagged = (fromGun ? 1 : 2) << 24 | color.asRGB();
		byte[] data = ByteBuffer.allocate(4 + 48 + 4).putInt(shooter.getEntityId())
				.putDouble(from.getX()).putDouble(from.getY()).putDouble(from.getZ())
				.putDouble(to.getX()).putDouble(to.getY()).putDouble(to.getZ()).putInt(flagged).array();
		org.bukkit.util.Vector step = to.clone().subtract(from);
		double len = step.length();
		org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.2f);
		for(Player p : shooter.getWorld().getPlayers())
		{
			if(p.getLocation().distanceSquared(shooter.getLocation()) > RANGE_SQUARED)
				continue;
			if(drawsTracers(p))
			{
				p.sendPluginMessage(COMZombies.getPlugin(), TRACER, data);
				continue;
			}
			if(len < 1.0E-6)
				continue;
			for(double d = 0; d <= len; d += 0.35)
			{
				// jagged: further off the straight line towards the middle
				double j = Math.sin(d / len * Math.PI) * 0.35;
				org.bukkit.util.Vector at = from.clone().add(step.clone().multiply(d / len));
				double x = at.getX() + (COMZombies.rand.nextDouble() - 0.5) * j, y = at.getY() + (COMZombies.rand.nextDouble() - 0.5) * j,
						z = at.getZ() + (COMZombies.rand.nextDouble() - 0.5) * j;
				p.spawnParticle(org.bukkit.Particle.DUST, x, y, z, 0, 0, 0, 0, 1, dust);
				if(COMZombies.rand.nextInt(3) == 0)
					p.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0.05);
			}
		}
	}

	/** A new gun went into this slot: hold it and look it over (the mod plays its pickup inspect). */
	public static void pickedUp(Player player, int slot)
	{
		if(slot >= 0 && slot <= 8 && !com.theprogrammingturkey.comz.game.features.PerkDrink.isDrinking(player))
			player.getInventory().setHeldItemSlot(slot);
		COMZombies.scheduleTask(1, () -> send(player, PICKUP, 44));
	}

	public static void send(Player shooter, byte kind, long ticks)
	{
		byte[] data = ByteBuffer.allocate(7).putInt(shooter.getEntityId()).put(kind)
				.putShort((short) Math.max(1, Math.min(Short.MAX_VALUE, ticks))).array();
		for(Player p : shooter.getWorld().getPlayers())
			if(p.getListeningPluginChannels().contains(CHANNEL) && p.getLocation().distanceSquared(shooter.getLocation()) <= RANGE_SQUARED)
				p.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, data);
	}
}
