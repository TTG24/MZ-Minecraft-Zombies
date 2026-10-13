package com.theprogrammingturkey.comz.game;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.protection.flags.*;
import com.sk89q.worldguard.protection.regions.*;
import java.lang.reflect.Proxy;
import java.util.*;

public final class WorldGuardInstancesTest
{
	public static void main(String[] args) throws Exception
	{
		World destination = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
				(proxy, method, values) -> method.getName().equals("getName") ? "instance" : null);
		Class<?> platformType = com.sk89q.worldguard.internal.platform.WorldGuardPlatform.class;
		Class<?> matcherType = platformType.getMethod("getMatcher").getReturnType();
		Object matcher = Proxy.newProxyInstance(matcherType.getClassLoader(), new Class<?>[]{matcherType},
				(proxy, method, values) -> method.getName().equals("getWorldByName") && "instance".equals(values[0]) ? destination : null);
		var platform = (com.sk89q.worldguard.internal.platform.WorldGuardPlatform) Proxy.newProxyInstance(platformType.getClassLoader(), new Class<?>[]{platformType},
				(proxy, method, values) -> method.getName().equals("getMatcher") ? matcher : null);
		com.sk89q.worldguard.WorldGuard.getInstance().setPlatform(platform);
		ProtectedRegion global = new GlobalProtectedRegion("__global__");
		global.setFlag(Flags.PVP, StateFlag.State.DENY);
		ProtectedRegion parent = new ProtectedCuboidRegion("parent", BlockVector3.at(-10, 0, -10), BlockVector3.at(20, 100, 20));
		parent.setFlag(Flags.GREET_MESSAGE, "Welcome");
		parent.getMembers().addGroup("players");
		ProtectedRegion polygon = new ProtectedPolygonalRegion("arena", List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(0, 10)), 20, 80);
		polygon.setParent(parent);
		polygon.setPriority(15);
		UUID owner = UUID.randomUUID();
		polygon.getOwners().addPlayer(owner);
		polygon.setFlag(Flags.BLOCKED_CMDS, new HashSet<>(Set.of("/home")));
		polygon.setFlag(Flags.TELE_LOC, Flags.TELE_LOC.unmarshal(Map.of("world", "template", "x", 2.5, "y", 30, "z", 2.5, "yaw", 90, "pitch", 15)));
		polygon.setFlag(Flags.SPAWN_LOC, Flags.SPAWN_LOC.unmarshal(Map.of("world", "hub", "x", 1, "y", 60, "z", 1, "yaw", 0, "pitch", 0)));
		var originals = Map.of("__global__", global, "parent", parent, "arena", polygon);
		var first = WorldGuardInstances.copyRegions(originals, "template", destination);
		var second = WorldGuardInstances.copyRegions(originals, "template", destination);
		ProtectedRegion copy = first.get("arena");
		check(copy.contains(2, 30, 2) && !copy.contains(8, 30, 8) && !copy.contains(2, 90, 2), "Polygon shape and height");
		check(copy.getPriority() == 15 && copy.getOwners().contains(owner), "Priority and owners");
		check(copy.getParent() == first.get("parent") && copy.getParent() != parent, "Parent belongs to instance");
		check(first.get("__global__").getFlag(Flags.PVP) == StateFlag.State.DENY, "Global flags");
		check(first.get("parent").contains(-10, 0, -10) && !first.get("parent").contains(21, 0, 0), "Cuboid boundaries");
		var teleport = copy.getFlag(Flags.TELE_LOC);
		check(((Map<?, ?>) Flags.TELE_LOC.marshal(teleport)).get("world").equals("instance") && teleport.getYaw() == 90 && teleport.getPitch() == 15, "Local teleport remapped with facing");
		check(((Map<?, ?>) Flags.SPAWN_LOC.marshal(copy.getFlag(Flags.SPAWN_LOC))).get("world").equals("hub"), "External destination preserved");
		check(((Map<?, ?>) Flags.TELE_LOC.marshal(polygon.getFlag(Flags.TELE_LOC))).get("world").equals("template"), "Template teleport unchanged");
		copy.getOwners().clear();
		copy.getParent().getMembers().clear();
		copy.getFlag(Flags.BLOCKED_CMDS).add("/spawn");
		check(polygon.getOwners().contains(owner) && second.get("arena").getOwners().contains(owner), "Owners isolated");
		check(parent.getMembers().getGroups().contains("players"), "Inherited membership isolated");
		check(!polygon.getFlag(Flags.BLOCKED_CMDS).contains("/spawn") && !second.get("arena").getFlag(Flags.BLOCKED_CMDS).contains("/spawn"), "Mutable flags isolated");
		boolean rejected = false;
		try { WorldGuardInstances.copyRegions(Map.of("arena", polygon), "template", destination); }
		catch(java.io.IOException expected) { rejected = true; }
		check(rejected, "Missing parent must fail instead of weakening protections");
		System.out.println("PASS: WorldGuard polygon bounds, hierarchy, protections, ownership isolation and teleport remapping");
	}
	private static void check(boolean result, String message) { if(!result) throw new AssertionError(message); }
}
