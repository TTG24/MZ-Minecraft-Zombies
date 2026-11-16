package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells players with the tactical client mod which round their game is on (0 = not in a game); the mod draws it as tally
 * marks in the bottom-left corner, and those players get no round title in the middle of the screen. Sent when a round
 * starts, and kept in step once a second (joining mid-game, leaving, the game ending).
 */
public final class RoundMessages
{
	public static final String CHANNEL = "tacticalmovement:round";
	private static final Map<UUID, Integer> sent = new HashMap<>();

	private RoundMessages()
	{
	}

	public static void start(Plugin plugin)
	{
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
		Bukkit.getScheduler().runTaskTimer(plugin, RoundMessages::sync, 40L, 20L);
	}

	/**
	 * True when the player has the mod (and was sent the round), so the caller can skip the title. A negative round
	 * -r means round r has ended (the tally blinks and goes away until the next round is sent).
	 */
	public static boolean send(Player player, int round)
	{
		if(!player.getListeningPluginChannels().contains(CHANNEL))
			return false;
		write(player, round);
		return true;
	}

	private static void sync()
	{
		for(Player player : Bukkit.getOnlinePlayers())
		{
			if(!player.getListeningPluginChannels().contains(CHANNEL))
				continue;
			int round = 0;
			if(GameManager.INSTANCE.isPlayerInGame(player))
			{
				Game game = GameManager.INSTANCE.getGame(player);
				if(game != null && game.getStatus() == Game.GameStatus.INGAME)
				{
					round = game.getWave();
					// Between rounds (the next one counting down) the old round has ended: negative
					if(game.changingRound && round > 1)
						round = -(round - 1);
				}
			}
			Integer last = sent.get(player.getUniqueId());
			if(last == null ? round != 0 : last != round)
				write(player, round);
		}
		sent.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
	}

	private static void write(Player player, int round)
	{
		int value = round;
		byte[] data = new byte[5];
		int n = 0;
		while((value & ~0x7F) != 0)
		{
			data[n++] = (byte) ((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		data[n++] = (byte) value;
		player.sendPluginMessage(COMZombies.getPlugin(), CHANNEL, java.util.Arrays.copyOf(data, n));
		sent.put(player.getUniqueId(), round);
	}
}
