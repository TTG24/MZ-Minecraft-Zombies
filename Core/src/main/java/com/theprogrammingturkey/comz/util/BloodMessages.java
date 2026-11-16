package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Tells players with the tactical client mod that a zombie was hit, so it can show blood (at the zombie, and on their
 * screen when it was close). Zombie damage is applied by setting health directly, so Minecraft itself sends no hurt
 * flash the mod could notice. Players without the mod never receive it (the channel is only sent to clients that
 * registered it).
 */
public final class BloodMessages
{
	public static final String CHANNEL = "tacticalmovement:blood";
	private static final double RANGE = 48;

	private BloodMessages()
	{
	}

	public static final int SHOT = 0, SLASH = 1, OTHER = 2, HEADSHOT = 3;
	private static org.bukkit.util.Vector nextPoint, nextDir;
	private static int nextKind = OTHER;

	/**
	 * Where (and how) the next hit lands, set right before Game.damageMob: the exact point on the zombie, the direction of the
	 * shot or swing, and SHOT / SLASH. The tactical mod paints a bullet hole or slash there.
	 */
	public static void nextHit(org.bukkit.util.Vector point, org.bukkit.util.Vector dir, int kind)
	{
		nextPoint = point;
		nextDir = dir;
		nextKind = kind;
	}

	public static void hit(Mob mob, boolean killed)
	{
		org.bukkit.util.Vector point = nextPoint, dir = nextDir;
		int kind = nextKind;
		nextPoint = null;
		nextDir = null;
		nextKind = OTHER;
		Location at = point != null ? point.toLocation(mob.getWorld()) : mob.getLocation().add(0, mob.getHeight() * 0.65, 0);
		byte[] data;
		try(ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes))
		{
			out.writeDouble(at.getX());
			out.writeDouble(at.getY());
			out.writeDouble(at.getZ());
			writeVarInt(out, mob.getEntityId());
			out.writeBoolean(killed);
			out.writeByte(point == null ? OTHER : kind);
			out.writeFloat(dir == null ? 0 : (float) dir.getX());
			out.writeFloat(dir == null ? 0 : (float) dir.getY());
			out.writeFloat(dir == null ? 0 : (float) dir.getZ());
			out.flush();
			data = bytes.toByteArray();
		}
		catch(IOException e)
		{
			return;
		}
		for(Player player : mob.getWorld().getPlayers())
			if(player.getLocation().distanceSquared(at) <= RANGE * RANGE && player.getListeningPluginChannels().contains(CHANNEL))
				player.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, data);
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
