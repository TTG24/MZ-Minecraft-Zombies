package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Tells a player with the tactical client mod that the game is over: where they went down, which way they faced and the
 * round reached. The mod plays a game-over cutscene there (their own body, zombies closing in) and then a GAME OVER
 * screen with the rounds survived. Players without the mod are not sent anything.
 */
public final class GameOverMessages
{
	public static final String CHANNEL = "tacticalmovement:gameover";

	private GameOverMessages()
	{
	}

	/** Stops a game-over cutscene the player may be watching (they left the arena). */
	public static void stop(Player player)
	{
		if(!player.getListeningPluginChannels().contains(CHANNEL))
			return;
		byte[] data = new byte[8 * 3 + 4 + 5];
		// Coordinates and yaw 0, then the round as a varint of -1 (five bytes): the mod reads a negative round as "stop"
		int n = 28;
		int value = -1;
		while((value & ~0x7F) != 0)
		{
			data[n++] = (byte) ((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		data[n++] = (byte) value;
		player.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, java.util.Arrays.copyOf(data, n));
	}

	/** True when the player has the mod and was sent the game over. */
	public static boolean send(Player player, Location downAt, int rounds)
	{
		if(!player.getListeningPluginChannels().contains(CHANNEL) || downAt == null)
			return false;
		try(ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes))
		{
			out.writeDouble(downAt.getX());
			out.writeDouble(downAt.getY());
			out.writeDouble(downAt.getZ());
			out.writeFloat(downAt.getYaw());
			int value = Math.max(0, rounds);
			while((value & ~0x7F) != 0)
			{
				out.writeByte((value & 0x7F) | 0x80);
				value >>>= 7;
			}
			out.writeByte(value);
			out.flush();
			player.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, bytes.toByteArray());
			return true;
		}
		catch(Exception e)
		{
			return false;
		}
	}
}
