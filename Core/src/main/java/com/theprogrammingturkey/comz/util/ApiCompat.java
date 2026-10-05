package com.theprogrammingturkey.comz.util;

import org.bukkit.GameRule;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;

/**
 * Looks up API constants by name at runtime for the few things that were renamed, or changed from a class to an
 * interface, during 1.21.x. Referencing them directly would tie the plugin to one side of that change.
 */
public class ApiCompat
{
	/** Renamed from GENERIC_* in 1.21.3 */
	public static final Attribute MAX_HEALTH = constant(Attribute.class, "MAX_HEALTH", "GENERIC_MAX_HEALTH");
	public static final Attribute FOLLOW_RANGE = constant(Attribute.class, "FOLLOW_RANGE", "GENERIC_FOLLOW_RANGE");
	public static final Attribute MOVEMENT_SPEED = constant(Attribute.class, "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");

	/** Renamed from DO_DAYLIGHT_CYCLE in 1.21.11 */
	@SuppressWarnings("unchecked")
	public static final GameRule<Boolean> ADVANCE_TIME = constant(GameRule.class, "ADVANCE_TIME", "DO_DAYLIGHT_CYCLE");

	/**
	 * Replacement for Sound.valueOf, which breaks across Sound changing from an enum to an interface in 1.21.3.
	 *
	 * @return the sound, or null if no sound has that name
	 */
	public static Sound getSound(String name)
	{
		return constant(Sound.class, name);
	}

	/**
	 * @return the first public static field of the given type matching one of the names, or null if none exist
	 */
	@SuppressWarnings("unchecked")
	public static <T> T constant(Class<?> type, String... names)
	{
		for(String name : names)
		{
			try
			{
				return (T) type.getField(name).get(null);
			} catch(NoSuchFieldException | IllegalAccessException ignored)
			{
			}
		}
		return null;
	}
}
