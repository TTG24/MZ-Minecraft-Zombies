package com.theprogrammingturkey.comz.game;

import java.io.IOException;
import java.nio.file.*;

/** Dependency-free regression checks, run by Gradle's check task. */
public final class InstanceWorldFilesTest
{
	public static void main(String[] args) throws Exception
	{
		Path root = Files.createTempDirectory(Path.of(args[0]), "instance-files-");
		try
		{
			Path source = Files.createDirectory(root.resolve("template"));
			String instanceName = "mz_instance_01234567-89ab-cdef_1";
			check(InstanceWorldFiles.destination(root, source, instanceName).equals(root.resolve(instanceName)), "Legacy world destination");
			Path unified = Files.createDirectories(root.resolve("world/dimensions/minecraft/overworld"));
			Path expected = root.resolve("world/dimensions/minecraft").resolve(instanceName);
			check(InstanceWorldFiles.destination(root, unified, instanceName).equals(expected), "Unified world destination");
			Path legacy = Files.createDirectory(root.resolve(instanceName));
			Files.writeString(legacy.resolve(".mz-instance"), "owner");
			Files.createDirectory(expected);
			Files.writeString(expected.resolve("stale-region"), "previously generated terrain");
			InstanceWorldFiles.migrateMarker(legacy, expected, "owner");
			InstanceWorldFiles.prepare(source, expected, "owner");
			check(!Files.exists(expected.resolve("stale-region")), "Migration rebuilds the actual dimension");
			check(Files.exists(legacy.resolve(".mz-instance")), "Legacy ownership evidence preserved");
			Path unrelatedDimension = Files.createDirectories(root.resolve("other").resolve(instanceName));
			rejected(() -> InstanceWorldFiles.migrateMarker(root.resolve("unowned"), unrelatedDimension, "owner"));
			rejected(() -> InstanceWorldFiles.destination(root, unified, "../overworld"));
			Files.createDirectories(source.resolve("region"));
			Files.writeString(source.resolve("region/r.0.0.mca"), "original map");
			Files.writeString(source.resolve("uid.dat"), "source uuid");
			Files.writeString(source.resolve("session.lock"), "locked");
			Files.writeString(source.resolve("level.dat"), "normal generator metadata");
			Files.writeString(source.resolve("level.dat_old"), "normal generator backup");
			Files.createDirectories(source.resolve("data/paper"));
			Files.writeString(source.resolve("data/paper/metadata.dat"), "template world UUID");
			Files.writeString(source.resolve("data/paper/metadata.dat_old"), "template UUID backup");
			Files.writeString(source.resolve("data/paper/persistent_data_container.dat"), "map plugin data");
			Files.createDirectories(source.resolve("playerdata"));
			Files.writeString(source.resolve("playerdata/player.dat"), "inventory");
			Path copy = root.resolve("copy");
			InstanceWorldFiles.prepare(source, copy, "owner");
			check(Files.readString(copy.resolve("region/r.0.0.mca")).equals("original map"), "Map data copied");
			check(!Files.exists(copy.resolve("uid.dat")), "Copy must receive a unique world UUID");
			check(!Files.exists(copy.resolve("data/paper/metadata.dat")) && !Files.exists(copy.resolve("data/paper/metadata.dat_old")), "Paper dimension UUID must be generated independently");
			check(Files.readString(source.resolve("data/paper/metadata.dat")).equals("template world UUID"), "Source Paper identity preserved");
			check(Files.readString(copy.resolve("data/paper/persistent_data_container.dat")).equals("map plugin data"), "Other Paper data still copied");
			check(!Files.exists(copy.resolve("session.lock")), "Do not copy the source world lock");
			check(!Files.exists(copy.resolve("level.dat")) && !Files.exists(copy.resolve("level.dat_old")), "Template metadata must not override flat generation");
			check(Files.readString(source.resolve("level.dat")).equals("normal generator metadata"), "Template metadata remains intact");
			check(!Files.exists(copy.resolve("playerdata")), "Do not copy player inventory data");

			Files.writeString(copy.resolve("region/r.0.0.mca"), "opened doors");
			Files.writeString(copy.resolve("region/stale.mca"), "new chunks");
			InstanceWorldFiles.prepare(source, copy, "owner");
			check(Files.readString(copy.resolve("region/r.0.0.mca")).equals("original map"), "Rebuild restores original blocks");
			check(!Files.exists(copy.resolve("region/stale.mca")), "Rebuild removes stale chunks");
			check(!Files.exists(copy.resolve("data/paper/metadata.dat")), "Rebuild must not restore the template UUID");
			check(Files.readString(source.resolve("region/r.0.0.mca")).equals("original map"), "Template remains intact");
			InstanceWorldFiles.prepareEmpty(source, copy, "owner");
			check(Files.exists(copy.resolve(".mz-instance")) && !Files.exists(copy.resolve("region")) && !Files.exists(copy.resolve("data")), "Selection instances start empty without copying world data");
			check(Files.exists(source.resolve("region/r.0.0.mca")), "Empty preparation preserves template chunks");
			InstanceWorldFiles.prepare(source, copy, "owner");

			Path unrelated = Files.createDirectory(root.resolve("unrelated"));
			Files.writeString(unrelated.resolve("important"), "keep");
			rejected(() -> InstanceWorldFiles.prepare(source, unrelated, "owner"));
			check(Files.readString(unrelated.resolve("important")).equals("keep"), "Unowned directory preserved");
			rejected(() -> InstanceWorldFiles.prepare(source, copy, "different-owner"));
			check(Files.exists(copy.resolve("region/r.0.0.mca")), "Other owner's copy preserved");
			rejected(() -> InstanceWorldFiles.prepare(source, source.resolve("nested-copy"), "owner"));
			rejected(() -> InstanceWorldFiles.prepare(source, root, "owner"));
			rejected(() -> InstanceWorldFiles.deleteOwned(source, unrelated, "owner"));
			rejected(() -> InstanceWorldFiles.deleteOwned(source, copy, "different-owner"));
			rejected(() -> InstanceWorldFiles.deleteOwned(source, source, "owner"));
			InstanceWorldFiles.deleteOwned(source, copy, "owner");
			check(!Files.exists(copy), "Disabling removes the entire owned instance directory");
			InstanceWorldFiles.deleteOwned(source, copy, "owner");
			check(Files.exists(source.resolve("region/r.0.0.mca")), "Cleanup preserves the original arena world");
			check(Files.exists(unrelated.resolve("important")), "Cleanup preserves unowned folders");
			InstanceWorldFiles.deleteOwned(source, expected, "owner");
			InstanceWorldFiles.deleteOwned(source, legacy, "owner");
			check(!Files.exists(expected) && !Files.exists(legacy), "Unified and legacy instance folders removed");
			System.out.println("PASS: instance copying, UUID isolation, reset, stale chunk cleanup, ownership and overlap guards");
		}
		finally
		{
			try(var paths = Files.walk(root))
			{
				for(Path file : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
			}
		}
	}

	private static void check(boolean result, String message)
	{
		if(!result) throw new AssertionError(message);
	}
	private static void rejected(IOAction action) throws IOException
	{
		try { action.run(); }
		catch(IOException expected) { return; }
		throw new AssertionError("Unsafe copy was accepted");
	}
	private interface IOAction { void run() throws IOException; }
}
