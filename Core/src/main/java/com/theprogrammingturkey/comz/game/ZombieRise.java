package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * New zombies dig themselves up out of the ground at their spawn. The zombie is frozen in place (no AI, can't move or
 * attack, can still be shot) for {@link #TICKS}, while:
 * <ul>
 *     <li>players with the tactical client mod are sent a "tacticalmovement:rise" message and play the animation on the
 *     real zombie model;</li>
 *     <li>players without it see the BetterModel "zombie_rise" model, which the Tactical Movement plugin puts on any
 *     zombie carrying the {@link #META} marker;</li>
 *     <li>everyone sees dirt bursting up from the ground and hears it.</li>
 * </ul>
 * The length matches the keyframes in the mod's tools/models/zombie_rise.py (2.5 s).
 */
public final class ZombieRise
{
	public static final String CHANNEL = "tacticalmovement:rise";
	/** Metadata on a rising zombie: the tick count of its rise (read by the Tactical Movement plugin). */
	public static final String META = "mz_rise_ticks";
	public static final int TICKS = 50;
	private static final double RANGE = 96;

	private ZombieRise()
	{
	}

	public static void start(Zombie zombie, Player facing)
	{
		Location at = zombie.getLocation();
		if(facing != null && facing.getWorld().equals(at.getWorld()))
		{
			Vector d = facing.getLocation().toVector().subtract(at.toVector());
			at.setYaw((float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ())));
			at.setPitch(0);
			zombie.teleport(at);
			zombie.setRotation(at.getYaw(), 0);
		}
		zombie.setAI(false);
		zombie.setMetadata(META, new FixedMetadataValue(COMZombies.getPlugin(), TICKS));
		send(zombie);

		Block ground = at.clone().subtract(0, 0.2, 0).getBlock();
		BlockData dirt = ground.getType().isSolid() ? ground.getBlockData() : Material.DIRT.createBlockData();
		final Location home = at.clone();
		final int[] tick = {0};
		COMZombies.scheduleTask(1, new Runnable()
		{
			@Override
			public void run()
			{
				if(!zombie.isValid() || zombie.isDead())
					return;
				int t = ++tick[0];
				if(t >= TICKS)
				{
					zombie.removeMetadata(META, COMZombies.getPlugin());
					zombie.setAI(true);
					return;
				}
				// Stay exactly where it is (nothing pushes or paths it while it climbs out)
				if(zombie.getLocation().distanceSquared(home) > 0.0004)
					zombie.teleport(home);
				zombie.setVelocity(new Vector());
				// Dirt flies most while it breaks the surface and hauls itself out
				boolean busy = t < 8 || (t > 10 && t < 16) || (t > 16 && t < 40);
				if(t % (busy ? 2 : 5) == 0)
				{
					Location p = home.clone().add(0, 0.1, 0);
					home.getWorld().spawnParticle(Particle.BLOCK, p, busy ? 14 : 5, 0.35, 0.05, 0.35, 0.15, dirt);
					if(t < 40)
						home.getWorld().spawnParticle(Particle.BLOCK, p, 4, 0.2, 0.3, 0.2, 0.3, dirt);
				}
				if(t == 1 || t == 11 || t == 20 || t == 28 || t == 34)
					home.getWorld().playSound(home, dirt.getSoundGroup().getBreakSound(), SoundCategory.HOSTILE, 1.0f, 0.7f + (float) Math.random() * 0.2f);
				else if(busy && t % 4 == 0)
					home.getWorld().playSound(home, dirt.getSoundGroup().getHitSound(), SoundCategory.HOSTILE, 0.8f, 0.6f + (float) Math.random() * 0.3f);
				COMZombies.scheduleTask(1, this);
			}
		});
	}

	public static boolean isRising(Zombie zombie)
	{
		return zombie.hasMetadata(META);
	}

	/** Players with the mod nearby play the animation themselves. */
	private static void send(Zombie zombie)
	{
		byte[] data;
		try(ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes))
		{
			writeVarInt(out, zombie.getEntityId());
			writeVarInt(out, TICKS);
			out.flush();
			data = bytes.toByteArray();
		}
		catch(IOException e)
		{
			return;
		}
		Location at = zombie.getLocation();
		StringBuilder log = new StringBuilder();
		for(Player player : zombie.getWorld().getPlayers())
		{
			boolean listens = player.getListeningPluginChannels().contains(CHANNEL);
			log.append(' ').append(player.getName()).append(listens ? "(mod)" : "(no mod)");
			if(player.getLocation().distanceSquared(at) <= RANGE * RANGE && listens)
				player.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, data);
		}
		COMZombies.log.info("[ZombieRise] zombie " + zombie.getEntityId() + " rising at " + at.toVector() + ", players:" + log);
	}

	private static void writeVarInt(DataOutputStream out, int value) throws IOException
	{
		while((value & ~0x7F) != 0)
		{
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}
}
