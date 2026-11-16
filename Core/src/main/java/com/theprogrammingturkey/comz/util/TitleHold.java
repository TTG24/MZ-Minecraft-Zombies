package com.theprogrammingturkey.comz.util;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/**
 * Keeps the big round title ("Round 5") on screen while interaction prompts (repair, buy door, ...) come and go. Prompts
 * are shown as a subtitle, and Minecraft only shows a subtitle together with a title, so a prompt used to send an empty
 * title and wipe the round off the screen. Now the round title is remembered for as long as it shows, and prompts put it
 * back above themselves for that time.
 */
public final class TitleHold
{
	private record Held(Component title, long until) {}

	private static final Map<UUID, Held> HELD = new ConcurrentHashMap<>();

	private TitleHold() {}

	/** Shows a title (with its subtitle) and remembers the title for as long as it stays up. Times in ticks. */
	public static void show(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut)
	{
		player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(fadeIn * 50L),
				Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
		HELD.put(player.getUniqueId(), new Held(title, System.currentTimeMillis() + (fadeIn + stay) * 50L));
	}

	/**
	 * Shows a prompt under the held title (or under nothing, if none is up). It lingers {@code stayMillis}, but never ends
	 * before the held title would have.
	 */
	public static void prompt(Player player, Component prompt, long stayMillis, long fadeOutMillis)
	{
		Held held = HELD.get(player.getUniqueId());
		long left = held == null ? 0 : held.until - System.currentTimeMillis();
		Component title = Component.empty();
		long stay = stayMillis;
		if(left > 0)
		{
			title = held.title;
			stay = Math.max(stayMillis, left);
		}
		else if(held != null)
		{
			HELD.remove(player.getUniqueId());
		}
		player.showTitle(Title.title(title, prompt, Title.Times.times(Duration.ZERO, Duration.ofMillis(stay),
				Duration.ofMillis(fadeOutMillis))));
	}

	public static void forget(Player player)
	{
		HELD.remove(player.getUniqueId());
	}
}
