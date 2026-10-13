package com.theprogrammingturkey.comz.game;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** File operations limited to marked instance directories, with no symbolic-link traversal. */
final class InstanceWorldFiles
{
	private static final String MARKER = ".mz-instance";

	/** Paper 26.x stores dimensions beneath the primary save rather than beside it. */
	static Path destination(Path container, Path primaryWorld, String name) throws IOException
	{
		if(!name.matches("mz_instance_[a-f0-9-]+_[0-9]+")) throw new IOException("Invalid instance directory name");
		primaryWorld = primaryWorld.toRealPath();
		Path namespace = primaryWorld.getParent();
		Path dimensions = namespace == null ? null : namespace.getParent();
		if(dimensions != null && dimensions.getFileName().toString().equals("dimensions"))
			return dimensions.resolve("minecraft").resolve(name);
		return container.toRealPath().resolve(name);
	}

	/** Older versions staged these same owned slots outside the unified dimension directory. */
	static void migrateMarker(Path legacy, Path destination, String owner) throws IOException
	{
		if(legacy.equals(destination) || !Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
				|| Files.exists(destination.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) return;
		if(Files.isSymbolicLink(destination) || !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)
				|| Files.isSymbolicLink(legacy) || !Files.isRegularFile(legacy.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)
				|| !Files.readString(legacy.resolve(MARKER)).equals(owner)
				|| !legacy.getFileName().equals(destination.getFileName()))
			throw new IOException("Cannot establish ownership of old instance dimension " + destination);
		Files.writeString(destination.resolve(MARKER), owner, StandardOpenOption.CREATE_NEW);
	}

	static void prepare(Path source, Path destination, String owner) throws IOException
	{
		prepare(source, destination, owner, true);
	}

	static void prepareEmpty(Path source, Path destination, String owner) throws IOException
	{
		prepare(source, destination, owner, false);
	}

	private static void prepare(Path source, Path destination, String owner, boolean copyFiles) throws IOException
	{
		deleteOwned(source, destination, owner);
		Files.createDirectory(destination);
		Files.writeString(destination.resolve(MARKER), owner);
		if(copyFiles) copyFiles(source.toRealPath(), destination);
	}

	/** Only called for unloaded slots at known instance storage locations. */
	static void deleteOwned(Path source, Path destination, String owner) throws IOException
	{
		source = source.toRealPath();
		destination = destination.toAbsolutePath().normalize();
		// Resolve the parent before checking overlap, including junctions in ancestor directories.
		destination = destination.getParent().toRealPath().resolve(destination.getFileName());
		if(destination.startsWith(source) || source.startsWith(destination))
			throw new IOException("Source and instance directories overlap");
		if(Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
		{
			if(Files.isSymbolicLink(destination) || !Files.isRegularFile(destination.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)
					|| !Files.readString(destination.resolve(MARKER)).equals(owner))
				throw new IOException("Refusing to replace an unowned directory: " + destination);
			Files.walkFileTree(destination, new SimpleFileVisitor<>()
			{
				@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
				{
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException
				{
					if(failure != null) throw failure;
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	private static void copyFiles(Path source, Path destination) throws IOException
	{
		final Path sourceRoot = source;
		final Path destinationRoot = destination;
		Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>()
		{
			@Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
			{
				String name = dir.getFileName().toString();
				if(!dir.equals(sourceRoot) && (name.equals("playerdata") || name.equals("stats") || name.equals("advancements")))
					return FileVisitResult.SKIP_SUBTREE;
				Files.createDirectories(destinationRoot.resolve(sourceRoot.relativize(dir)));
				return FileVisitResult.CONTINUE;
			}
			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				if(attrs.isSymbolicLink()) throw new IOException("Symlink in template world: " + file);
				String name = file.getFileName().toString();
				Path relative = sourceRoot.relativize(file);
				// Paper 26.x moved the world UUID out of uid.dat into per-dimension metadata.
				// Let Paper create a fresh identity; copying this makes it reject the world as a duplicate.
				if(relative.equals(Path.of("data", "paper", "metadata.dat"))
						|| relative.equals(Path.of("data", "paper", "metadata.dat_old")))
					return FileVisitResult.CONTINUE;
				// Copied metadata would override WorldCreator's flat generator settings.
				if(file.getParent().equals(sourceRoot) && (name.equals("level.dat") || name.equals("level.dat_old")))
					return FileVisitResult.CONTINUE;
				if(!name.equals("uid.dat") && !name.equals("session.lock") && !name.equals(MARKER))
					Files.copy(file, destinationRoot.resolve(sourceRoot.relativize(file)));
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
