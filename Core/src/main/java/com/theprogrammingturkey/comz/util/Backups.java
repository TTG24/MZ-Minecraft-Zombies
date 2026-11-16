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
	private static org.bukkit.scheduler.BukkitTask task;
	private static volatile boolean stopping;

	/** On plugin stop: ends a backup that is still being written (it would carry on with the plugin's code unloaded). */
	public static void stop()
	{
		stopping = true;
		if(task != null)
			task.cancel();
		task = null;
		autosaveBack();
		running = false;
	}

	// Worlds whose autosave a backup switched off (switched back on when it is done, or the plugin stops)
	private static final List<World> autosaveOff = new ArrayList<>();

	private static void autosaveBack()
	{
		for(World w : autosaveOff)
			if(Bukkit.getWorld(w.getUID()) != null)
				w.setAutoSave(true);
		autosaveOff.clear();
	}

	private Backups()
	{
	}

	/** Starts the daily check (every hour: makes today's backup if it does not exist yet). */
	public static void start()
	{
		stopping = false;
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
		// Worlds are saved first (main thread). The server writes the saved chunks in the background, so the zip starts 10
		// seconds later, with autosave off meanwhile, so it never copies a region file half-written.
		List<World> saved = new ArrayList<>();
		List<String> extraWorlds = new ArrayList<>();
		for(World w : Bukkit.getWorlds())
			if(!w.getName().startsWith("mz_instance_"))
			{
				w.save();
				if(w.isAutoSave())
				{
					w.setAutoSave(false);
					saved.add(w);
				}
				// Every arena world, not just the one named "world"
				Path folder = w.getWorldFolder().getAbsoluteFile().toPath();
				if(folder.startsWith(serverRoot()))
					extraWorlds.add(serverRoot().relativize(folder).toString());
			}
		int keep = Math.max(1, COMZombies.getPlugin().getConfig().getInt("config.backups.keepDays", 14));
		autosaveOff.addAll(saved);
		Runnable autosaveBack = Backups::autosaveBack;
		COMZombies.scheduleTask(200, () -> task = Bukkit.getScheduler().runTaskAsynchronously(COMZombies.getPlugin(), () ->
		{
			try
			{
				write(target, extraWorlds);
				prune(keep);
			} catch(Exception e)
			{
				COMZombies.log.log(Level.WARNING, "Daily backup failed", e);
			} finally
			{
				running = false;
				if(COMZombies.getPlugin().isEnabled())
					Bukkit.getScheduler().runTask(COMZombies.getPlugin(), autosaveBack);
			}
		}));
	}

	private static void write(Path target, List<String> extraWorlds) throws IOException
	{
		Files.createDirectories(target.getParent());
		Path root = serverRoot();
		Path temp = target.resolveSibling(target.getFileName() + ".part");
		int[] count = {0};
		try
		{
			try(OutputStream out = Files.newOutputStream(temp); ZipOutputStream zip = new ZipOutputStream(out))
			{
				if(stopping)
					throw new IOException("Server stopping");
				java.util.Set<String> names = new java.util.LinkedHashSet<>(List.of(SERVER_FILES));
				names.addAll(extraWorlds);
				for(String name : names)
				{
					Path p = root.resolve(name);
					if(Files.exists(p))
						add(zip, root, p, count);
				}
			}
		}
		catch(IOException e)
		{
			Files.deleteIfExists(temp); // no half-written zip left behind
			throw e;
		}
		Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		COMZombies.log.log(Level.INFO, "Daily backup made: {0} ({1} files, {2} KB)",
				new Object[]{target.getFileName(), count[0], Files.size(target) / 1024});
	}

	private static void add(ZipOutputStream zip, Path root, Path path, int[] count) throws IOException
	{
		if(stopping)
			throw new IOException("Server stopping");
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
	private static void prune(int keep) throws IOException
	{
		List<Path> all = new ArrayList<>();
		try(Stream<Path> s = Files.list(folder()))
		{
			s.filter(p -> p.getFileName().toString().matches("backup-\\d{4}-\\d{2}-\\d{2}\\.zip")).sorted().forEach(all::add);
		}
		for(int i = 0; i < all.size() - keep; i++)
			Files.deleteIfExists(all.get(i));
	}
}
