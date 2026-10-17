package com.theprogrammingturkey.comz.util;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Non-deprecated colour helpers.
 *
 * <p>{@code org.bukkit.ChatColor} is deprecated across Paper 1.19+ in favour of
 * the Adventure {@link Component} API. The rest of the codebase builds its
 * messages as legacy section-code strings (which is still the correct model for
 * sign text, scoreboard display names, and the {@code sendMessage(String)}
 * shims). This class is the single place that owns those legacy colour
 * constants, so no other class references the deprecated {@code ChatColor}
 * type.
 *
 * <p>The string values are the exact, stable legacy section-code sequences the
 * old {@code ChatColor} constants used to produce. They are assembled from the
 * section character (the {@code '\u00a7'} literal, kept ASCII so this source
 * file is encoding-independent) plus the colour/format digit.
 */
public final class Colors
{
	/** The section/prefix character (U+00A7) used by legacy colour codes. */
	public static final char SECTION = '\u00a7';

	private Colors() {}

	// --- Colour constants (stable legacy section codes) ---
	public static final String BLACK = SECTION + "0";
	public static final String DARK_BLUE = SECTION + "1";
	public static final String DARK_GREEN = SECTION + "2";
	public static final String DARK_AQUA = SECTION + "3";
	public static final String DARK_RED = SECTION + "4";
	public static final String DARK_PURPLE = SECTION + "5";
	public static final String GOLD = SECTION + "6";
	public static final String GRAY = SECTION + "7";
	public static final String BLUE = SECTION + "8";
	public static final String GREEN = SECTION + "9";
	public static final String AQUA = SECTION + "a";
	public static final String RED = SECTION + "c";
	public static final String LIGHT_PURPLE = SECTION + "d";
	public static final String YELLOW = SECTION + "e";
	public static final String WHITE = SECTION + "f";

	// --- Formatting constants ---
	public static final String MAGIC = SECTION + "k";
	public static final String BOLD = SECTION + "l";
	public static final String STRIKETHROUGH = SECTION + "m";
	public static final String UNDERLINE = SECTION + "n";
	public static final String ITALIC = SECTION + "o";
	public static final String RESET = SECTION + "r";

	/**
	 * Non-deprecated replacement for
	 * {@code ChatColor.translateAlternateColorCodes(char, String)}.
	 *
	 * <p>Converts an alternate colour-code character (typically {@code '&'}) in
	 * {@code input} into literal section codes using Adventure's legacy
	 * serializers.
	 */
	public static String translateAlternateColorCodes(char alternate, String input)
	{
		if (input == null)
			return null;
		if (alternate == '&')
		{
			Component c = LegacyComponentSerializer.legacyAmpersand().deserialize(input);
			return LegacyComponentSerializer.legacySection().serialize(c);
		}
		// Any other alternate char: swap to section, then normalise via the section serializer.
		String swapped = input.replace(alternate, SECTION);
		Component c = LegacyComponentSerializer.legacySection().deserialize(swapped);
		return LegacyComponentSerializer.legacySection().serialize(c);
	}

	/**
	 * Non-deprecated replacement for {@code ChatColor.stripColor(String)}.
	 *
	 * <p>Strips all colour and decoration codes, returning plain text.
	 */
	public static String stripColor(String input)
	{
		if (input == null)
			return null;
		Component c = LegacyComponentSerializer.legacySection().deserialize(input);
		// The plain-text serializer emits only visible text, dropping all colour
		// and decoration — exactly the old stripColor() behaviour.
		return PlainTextComponentSerializer.plainText().serialize(c);
	}

	/** The section character as a one-character string. */
	public static String section()
	{
		return String.valueOf(SECTION);
	}

	/**
	 * Deserialises a legacy section-code {@code String} into an Adventure
	 * {@link Component}. {@code null}-safe (returns {@code Component.empty()}).
	 *
	 * <p>Used to feed legacy colour strings into the non-deprecated
	 * {@code displayName(Component)} / {@code broadcast(Component)} /
	 * {@code sendTitle(Title)} APIs that replaced the old {@code String}-based
	 * Bukkit methods.
	 */
	public static Component legacy(String input)
	{
		if (input == null)
			return Component.empty();
		return LegacyComponentSerializer.legacySection().deserialize(input);
	}

	/** Deserialises each legacy string into a {@link Component} list. */
	public static List<Component> legacy(List<String> input)
	{
		List<Component> out = new ArrayList<>();
		if (input != null)
		{
			for (String s : input)
				out.add(legacy(s));
		}
		return out;
	}

	/**
	 * Serialises an Adventure {@link Component} to a plain (colour-free) legacy
	 * section-code string, so it can feed the {@code String}-based sign APIs.
	 */
	public static String plain(Component component)
	{
		if (component == null)
			return "";
		return PlainTextComponentSerializer.plainText().serialize(component);
	}

	/**
	 * Serialises an Adventure {@link Component} into a legacy section-code string,
	 * preserving any colour/formatting codes. {@code null}-safe (returns {@code ""}).
	 */
	public static String serialize(Component component)
	{
		if (component == null)
			return "";
		return LegacyComponentSerializer.legacySection().serialize(component);
	}
}
