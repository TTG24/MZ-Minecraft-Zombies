package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Once a day, zips the server's important files into backups/daily/backup-YYYY-MM-DD.zip (next to the plugins folder) and
 * keeps the last few days: the main world (arena builds), every plugin's settings and data (MZ arenas, BlockBlocker
 * barriers, Essentials, LuckPerms, WorldGuard, portals...) and the server's own config files. Arena instance copies,
 * downloaded libraries, logs and caches are left out (they are rebuilt).
 */
public final class Backups
{
	/** Top-level server files and folders that are backed up. */
	private static final String[] SERVER_FILES = {"server.properties", "bukkit.yml", "spigot.yml", "purpur.yml", "commands.yml",
			"ops.json", "whitelist.json", "banned-players.json", "banned-ips.json", "usercache.json", "server-icon.png", "config", "world",
			"plugins"};
	/** Folder names never backed up (rebuilt, downloaded, or too big). */
	private static final List<String> SKIP_DIRS = List.of("libs", "libraries", "cache", "logs", "client", "packs", "sound-sources",
			"backups", "bStats", "spark", "faststats", ".paper-remapped", "translations");
	private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

	private static volatile boolean running;

	private Backups()
	{
	}

	/** Starts the daily check (every hour: makes today's backup if it does not exist yet). */
	public static void start()
	{
		FileConfiguration config = COMZombies.getPlugin().getConfig();
		if(!config.getBoolean("config.backups.enabled", true))
			return;
		// First one a minute after startup, so it does not slow the start down
		COMZombies.scheduleTask(20L * 60, 20L * 60 * 60, Backups::runIfDue);
	}

	private static Path serverRoot()
	{
		return COMZombies.getPlugin().getDataFolder().getAbsoluteFile().getParentFile().getParentFile().toPath();
	}

	private static Path folder()
	{
		return serverRoot().resolve("backups").resolve("daily");
	}

	private static void runIfDue()
	{
		if(running)
			return;
		Path target = folder().resolve("backup-" + LocalDate.now() + ".zip");
		if(Files.exists(target))
			return;
		running = true;
		// Worlds are saved first (main thread), then zipped in the background
		for(World w : Bukkit.getWorlds())
			if(!w.getName().startsWith("mz_instance_"))
				w.save();
		Bukkit.getScheduler().runTaskAsynchronously(COMZombies.getPlugin(), () ->
		{
			try
			{
				write(target);
				prune();
			} catch(Exception e)
			{
				COMZombies.log.log(Level.WARNING, "Daily backup failed", e);
			} finally
			{
				running = false;
			}
		});
	}

	private static void write(Path target) throws IOException
	{
		Files.createDirectories(target.getParent());
		Path root = serverRoot();
		Path temp = target.resolveSibling(target.getFileName() + ".part");
		int[] count = {0};
		try(OutputStream out = Files.newOutputStream(temp); ZipOutputStream zip = new ZipOutputStream(out))
		{
			for(String name : SERVER_FILES)
			{
				Path p = root.resolve(name);
				if(Files.exists(p))
					add(zip, root, p, count);
			}
		}
		Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		COMZombies.log.log(Level.INFO, "Daily backup made: {0} ({1} files, {2} KB)",
				new Object[]{target.getFileName(), count[0], Files.size(target) / 1024});
	}

	private static void add(ZipOutputStream zip, Path root, Path path, int[] count) throws IOException
	{
		String fileName = path.getFileName().toString();
		if(Files.isDirectory(path))
		{
			if(SKIP_DIRS.contains(fileName) || fileName.startsWith("mz_instance_"))
				return;
			List<Path> children = new ArrayList<>();
			try(Stream<Path> s = Files.list(path))
			{
				s.forEach(children::add);
			}
			for(Path child : children)
				add(zip, root, child, count);
			return;
		}
		// Plugin jars are kept in backups/plugin-jars already; session.lock is held by the server
		if(fileName.equals("session.lock") || fileName.endsWith(".jar") || fileName.endsWith(".bak") || Files.size(path) > MAX_FILE_BYTES)
			return;
		try
		{
			byte[] data = Files.readAllBytes(path);
			zip.putNextEntry(new ZipEntry(root.relativize(path).toString().replace('\\', '/')));
			zip.write(data);
			zip.closeEntry();
			count[0]++;
		} catch(IOException e)
		{
			// A file in use (or removed meanwhile) is skipped, the rest is still backed up
			COMZombies.log.log(Level.FINE, "Backup skipped " + path, e);
		}
	}

	/** Keeps the newest {@code config.backups.keepDays} backups. */
	private static void prune() throws IOException
	{
		int keep = Math.max(1, COMZombies.getPlugin().getConfig().getInt("config.backups.keepDays", 14));
		List<Path> all = new ArrayList<>();
		try(Stream<Path> s = Files.list(folder()))
		{
			s.filter(p -> p.getFileName().toString().matches("backup-\\d{4}-\\d{2}-\\d{2}\\.zip")).sorted().forEach(all::add);
		}
		for(int i = 0; i < all.size() - keep; i++)
			Files.deleteIfExists(all.get(i));
	}
}
