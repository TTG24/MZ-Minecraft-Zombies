package com.theprogrammingturkey.comz.game;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

class WorldGuardInstancesTest
{
	private static World destination;
	private static GlobalProtectedRegion global;
	private static ProtectedCuboidRegion parent;
	private static ProtectedPolygonalRegion polygon;
	private static UUID owner;

	@BeforeAll
	@SuppressWarnings("unused") // Invoked reflectively by JUnit via @BeforeAll
	static void installPlatform() throws Exception
	{
		destination = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
				(proxy, method, values) -> method.getName().equals("getName") ? "instance" : null);
		Class<?> platformType = com.sk89q.worldguard.internal.platform.WorldGuardPlatform.class;
		Class<?> matcherType = platformType.getMethod("getMatcher").getReturnType();
		Object matcher = Proxy.newProxyInstance(matcherType.getClassLoader(), new Class<?>[]{matcherType},
				(proxy, method, values) -> method.getName().equals("getWorldByName") && "instance".equals(values[0]) ? destination : null);
		var platform = (com.sk89q.worldguard.internal.platform.WorldGuardPlatform) Proxy.newProxyInstance(platformType.getClassLoader(), new Class<?>[]{platformType},
				(proxy, method, values) -> method.getName().equals("getMatcher") ? matcher : null);
		com.sk89q.worldguard.WorldGuard.getInstance().setPlatform(platform);

		global = new GlobalProtectedRegion("__global__");
		global.setFlag(Flags.PVP, StateFlag.State.DENY);
		parent = new ProtectedCuboidRegion("parent", BlockVector3.at(-10, 0, -10), BlockVector3.at(20, 100, 20));
		parent.getMembers().addGroup("players");
		polygon = new ProtectedPolygonalRegion("arena", List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(0, 10)), 20, 80);
		polygon.setParent(parent);
		polygon.setPriority(15);
		owner = UUID.randomUUID();
		polygon.getOwners().addPlayer(owner);
		polygon.setFlag(Flags.BLOCKED_CMDS, new HashSet<>(Set.of("/home")));
		polygon.setFlag(Flags.TELE_LOC, Flags.TELE_LOC.unmarshal(Map.of("world", "template", "x", 2.5, "y", 30, "z", 2.5, "yaw", 90, "pitch", 15)));
		polygon.setFlag(Flags.SPAWN_LOC, Flags.SPAWN_LOC.unmarshal(Map.of("world", "hub", "x", 1, "y", 60, "z", 1, "yaw", 0, "pitch", 0)));
	}

	@Test
	void copiesPolygonShapeHierarchyAndCuboidBounds() throws Exception
	{
		Map<String, ProtectedRegion> originals = Map.of("__global__", global, "parent", parent, "arena", polygon);
		Map<String, ProtectedRegion> first = WorldGuardInstances.copyRegions(originals, "template", destination);
		ProtectedRegion copy = first.get("arena");
		assertTrue(copy.contains(2, 30, 2) && !copy.contains(8, 30, 8) && !copy.contains(2, 90, 2), "Polygon shape and height");
		assertEquals(15, copy.getPriority(), "Priority");
		assertTrue(copy.getOwners().contains(owner), "Owners");
		assertSame(first.get("parent"), copy.getParent(), "Parent belongs to instance");
		assertNotSame(parent, copy.getParent(), "Parent belongs to instance");
		assertEquals(StateFlag.State.DENY, first.get("__global__").getFlag(Flags.PVP), "Global flags");
		assertTrue(first.get("parent").contains(-10, 0, -10) && !first.get("parent").contains(21, 0, 0), "Cuboid boundaries");
	}

	@Test
	@SuppressWarnings("null") // JDT cannot see assertNotNull as a nullability refinement
	void remapsTeleportAndPreservesExternalDestinations() throws Exception
	{
		Map<String, ProtectedRegion> originals = Map.of("__global__", global, "parent", parent, "arena", polygon);
		Map<String, ProtectedRegion> first = WorldGuardInstances.copyRegions(originals, "template", destination);
		ProtectedRegion copy = first.get("arena");
		var teleport = copy.getFlag(Flags.TELE_LOC);
		assertNotNull(teleport, "Teleport location flag present");
		assertEquals("instance", ((Map<?, ?>) Flags.TELE_LOC.marshal(teleport)).get("world"), "Local teleport world remapped");
		assertEquals(90, teleport.getYaw(), "Local teleport yaw preserved");
		assertEquals(15, teleport.getPitch(), "Local teleport pitch preserved");
		assertEquals("hub", ((Map<?, ?>) Flags.SPAWN_LOC.marshal(copy.getFlag(Flags.SPAWN_LOC))).get("world"), "External destination preserved");
		assertEquals("template", ((Map<?, ?>) Flags.TELE_LOC.marshal(polygon.getFlag(Flags.TELE_LOC))).get("world"), "Template teleport unchanged");
	}

	@Test
	@SuppressWarnings("null") // JDT cannot see assertNotNull as a nullability refinement
	void isolatesOwnersMembershipsAndMutableFlags() throws Exception
	{
		Map<String, ProtectedRegion> originals = Map.of("__global__", global, "parent", parent, "arena", polygon);
		Map<String, ProtectedRegion> first = WorldGuardInstances.copyRegions(originals, "template", destination);
		Map<String, ProtectedRegion> second = WorldGuardInstances.copyRegions(originals, "template", destination);
		ProtectedRegion copy = first.get("arena");
		copy.getOwners().clear();
		ProtectedRegion copyParent = copy.getParent();
		assertNotNull(copyParent, "Deep copy still exposes parent region");
		copyParent.getMembers().clear();
		Set<String> copyBlocked = copy.getFlag(Flags.BLOCKED_CMDS);
		assertNotNull(copyBlocked, "Copy still exposes blocked commands flag");
		copyBlocked.add("/spawn");
		assertTrue(polygon.getOwners().contains(owner) && second.get("arena").getOwners().contains(owner), "Owners isolated");
		assertTrue(parent.getMembers().getGroups().contains("players"), "Inherited membership isolated");
		Set<String> originalBlocked = polygon.getFlag(Flags.BLOCKED_CMDS);
		assertNotNull(originalBlocked, "Template still exposes blocked commands flag");
		assertFalse(originalBlocked.contains("/spawn"), "Template mutable flags isolated");
		Set<String> secondBlocked = second.get("arena").getFlag(Flags.BLOCKED_CMDS);
		assertNotNull(secondBlocked, "Independent copy still exposes blocked commands flag");
		assertFalse(secondBlocked.contains("/spawn"), "Independent copy mutable flags isolated");
	}

	@Test
	void missingParentFailsInsteadOfWeakeningProtections()
	{
		Throwable thrown = assertThrows(java.io.IOException.class,
				() -> WorldGuardInstances.copyRegions(Map.of("arena", polygon), "template", destination));
		assertNotNull(thrown, "Missing parent must fail with IOException");
	}
}
