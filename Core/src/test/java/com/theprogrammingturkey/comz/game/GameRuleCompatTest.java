package com.theprogrammingturkey.comz.game;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;

/** Runs the compiled plugin helper against both API shapes without a Minecraft server. */
public final class GameRuleCompatTest
{
	public static void main(String[] args) throws Exception
	{
		Path root = Files.createTempDirectory(Path.of(args[0]), "gamerule-abi-");
		try
		{
			for(String shape : new String[]{"class", "interface"})
			{
				Path output = Files.createDirectories(root.resolve(shape));
				Path file = output.resolve("GameRule.java");
				Files.writeString(file, "package org.bukkit; public " + shape + " GameRule<T> { public static GameRule<?> getByName(String name) { return name.equals(\"missing\") ? null : new GameRule<Boolean>() {}; } }");
				if(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", output.toString(), file.toString()) != 0)
					throw new AssertionError("Could not compile API fixture");
				URL helper = com.theprogrammingturkey.comz.util.GameRuleCompat.class.getProtectionDomain().getCodeSource().getLocation();
				try(URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL(), helper}, ClassLoader.getPlatformClassLoader()))
				{
					Class<?> api = Class.forName("org.bukkit.GameRule", false, loader);
					Class<?> bridge = Class.forName("com.theprogrammingturkey.comz.util.GameRuleCompat", true, loader);
					var lookup = bridge.getMethod("getByName", String.class);
					if(!api.isInstance(lookup.invoke(null, "doDaylightCycle"))) throw new AssertionError("Lookup failed for " + shape);
					if(lookup.invoke(null, "missing") != null) throw new AssertionError("Unknown rule must remain null");
				}
			}
			System.out.println("PASS: game rule lookup across class/interface API changes, including unknown rules");
		}
		finally
		{
			try(var paths = Files.walk(root))
			{
				for(Path file : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
			}
		}
	}
}
