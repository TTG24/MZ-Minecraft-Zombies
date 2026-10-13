package com.theprogrammingturkey.comz.util;

import org.bukkit.GameRule;
import java.lang.reflect.InvocationTargetException;

/** Spigot exposes GameRule as an interface; Paper/Purpur builds may expose it as a class. */
public final class GameRuleCompat
{
	private GameRuleCompat() {}

	public static GameRule<?> getByName(String name)
	{
		try
		{
			// A direct static call embeds either InterfaceMethodref or Methodref in the JAR.
			// Reflection resolves the actual runtime declaration instead.
			return (GameRule<?>) GameRule.class.getMethod("getByName", String.class).invoke(null, name);
		}
		catch(InvocationTargetException failure)
		{
			throw new IllegalStateException("Could not resolve game rule " + name, failure.getCause());
		}
		catch(ReflectiveOperationException failure)
		{
			throw new IllegalStateException("Server does not expose game rule lookup", failure);
		}
	}
}
