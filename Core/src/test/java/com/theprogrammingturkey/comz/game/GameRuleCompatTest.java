package com.theprogrammingturkey.comz.game;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs the compiled plugin helper against both API shapes without a Minecraft server. */
class GameRuleCompatTest
{
	@TempDir
	Path root;

	@ParameterizedTest
	@ValueSource(strings = {"class", "interface"})
	void lookupAcrossApiShapes(String shape) throws Exception
	{
		Path output = Files.createDirectories(root.resolve(shape));
		Path file = output.resolve("GameRule.java");
		Files.writeString(file, "package org.bukkit; public " + shape + " GameRule<T> { public static GameRule<?> getByName(String name) { return name.equals(\"missing\") ? null : new GameRule<Boolean>() {}; } }");
		int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", output.toString(), file.toString());
		if(exit != 0)
			throw new AssertionError("Could not compile API fixture");
		URL helper = com.theprogrammingturkey.comz.util.GameRuleCompat.class.getProtectionDomain().getCodeSource().getLocation();
		try(URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL(), helper}, ClassLoader.getPlatformClassLoader()))
		{
			Class<?> api = Class.forName("org.bukkit.GameRule", false, loader);
			Class<?> bridge = Class.forName("com.theprogrammingturkey.comz.util.GameRuleCompat", true, loader);
			var lookup = bridge.getMethod("getByName", String.class);
			Object found = lookup.invoke(null, "doDaylightCycle");
			assertTrue(api.isInstance(found), "Lookup failed for " + shape);
			assertNull(lookup.invoke(null, "missing"), "Unknown rule must remain null");
		}
	}
}
