package com.theprogrammingturkey.comz.game;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.managers.DoorProximityManager;

/**
 * Tests the pure proximity geometry in {@link DoorProximityManager} that is shared with the
 * per-tick proximity scan: the nearest-door selection (distance + radius filter + world match +
 * opened-skip + tie ordering) and the minimum anchor distance.
 *
 * <p>The methods under test only call {@code Door.isOpened()}/{@code getSignsLocations()}/{@code
 * getBlockLocations()} and do {@code Location.distanceSquared} math, so they can be exercised with
 * a fake door and proxy worlds without a running server.
 */
class DoorProximityManagerTest
{
	/** Two distinct worlds, identified by reference like real server worlds. */
	private final World worldA = world("a");
	private final World worldB = world("b");

	private static World world(String name)
	{
		return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
				(proxyObj, method, values) ->
				{
					switch(method.getName())
					{
						case "getName": return name;
						// Identity semantics so equals()/hashCode() behave like real server worlds.
						case "equals": return proxyObj == values[0];
						case "hashCode": return System.identityHashCode(proxyObj);
						default: return null;
					}
				});
	}

	/** A door whose anchors and open state are fully controlled by the test. */
	private static final class FakeDoor extends Door
	{
		private final List<Location> signs = new ArrayList<>();
		private final List<Location> blocks = new ArrayList<>();
		private boolean open;

		private FakeDoor(String id)
		{
			super(null, id, false);
		}

		FakeDoor sign(Location loc)
		{
			signs.add(loc);
			return this;
		}

		FakeDoor block(Location loc)
		{
			blocks.add(loc);
			return this;
		}

		FakeDoor open(boolean opened)
		{
			open = opened;
			return this;
		}

		@Override
		public List<Location> getSignsLocations()
		{
			return signs;
		}

		@Override
		public List<Location> getBlockLocations()
		{
			return blocks;
		}

		@Override
		public boolean isOpened()
		{
			return open;
		}
	}

	@Test
	void minAnchorDistanceUsesNearestOfSignAndBlockAnchors()
	{
		FakeDoor door = new FakeDoor("d1").sign(new Location(worldA, 1, 0, 0)).block(new Location(worldA, 4, 0, 0));
		Location player = new Location(worldA, 0, 0, 0);
		// Nearest anchor is the sign 1 block away -> squared distance 1.0, not the block's 16.0.
		assertEquals(1.0, DoorProximityManager.minAnchorDistance(door, player), 1e-9);
	}

	@Test
	void minAnchorDistanceIgnoresAnchorsInOtherWorlds()
	{
		FakeDoor door = new FakeDoor("d2").sign(new Location(worldB, 1, 0, 0)).block(new Location(worldB, 4, 0, 0));
		Location player = new Location(worldA, 0, 0, 0);
		assertTrue(Double.isInfinite(DoorProximityManager.minAnchorDistance(door, player)),
				"No anchor shares the player's world");
	}

	@Test
	void minAnchorDistanceReturnsInfinityWhenNoAnchors()
	{
		FakeDoor door = new FakeDoor("d3");
		Location player = new Location(worldA, 0, 0, 0);
		assertTrue(Double.isInfinite(DoorProximityManager.minAnchorDistance(door, player)));
	}

	@Test
	void nearestDoorInPicksClosestUnopenedDoor()
	{
		FakeDoor far = new FakeDoor("far").sign(new Location(worldA, 3, 0, 0));
		FakeDoor near = new FakeDoor("near").sign(new Location(worldA, 1, 0, 0));
		Location player = new Location(worldA, 0, 0, 0);
		assertSame(near, DoorProximityManager.nearestDoorIn(List.of(far, near), player, 100.0));
	}

	@Test
	void nearestDoorInSkipsOpenedDoors()
	{
		FakeDoor far = new FakeDoor("far").sign(new Location(worldA, 3, 0, 0));
		FakeDoor near = new FakeDoor("near").sign(new Location(worldA, 1, 0, 0)).open(true);
		Location player = new Location(worldA, 0, 0, 0);
		assertSame(far, DoorProximityManager.nearestDoorIn(List.of(far, near), player, 100.0));
	}

	@Test
	void nearestDoorInReturnsNullWhenAllDoorsOutOfRadius()
	{
		FakeDoor far = new FakeDoor("far").sign(new Location(worldA, 5, 0, 0));
		Location player = new Location(worldA, 0, 0, 0);
		assertNull(DoorProximityManager.nearestDoorIn(List.of(far), player, 4.0));
	}

	@Test
	void nearestDoorInRespectsInclusiveRadiusBoundary()
	{
		FakeDoor exact = new FakeDoor("exact").sign(new Location(worldA, 3, 0, 0));
		Location player = new Location(worldA, 0, 0, 0);
		// Distance squared is exactly 9.0; a radiusSq of 9.0 must still count.
		assertSame(exact, DoorProximityManager.nearestDoorIn(List.of(exact), player, 9.0));
		assertNull(DoorProximityManager.nearestDoorIn(List.of(exact), player, 8.999));
	}

	@Test
	void nearestDoorInIgnoresDoorsInOtherWorlds()
	{
		FakeDoor otherWorld = new FakeDoor("ow").sign(new Location(worldB, 1, 0, 0));
		Location player = new Location(worldA, 0, 0, 0);
		assertNull(DoorProximityManager.nearestDoorIn(List.of(otherWorld), player, 100.0));
	}

	@Test
	void nearestDoorInPrefersBlockAnchorWhenCloserThanSign()
	{
		// The sign is far away but a block is right next to the player, so the block should win.
		FakeDoor door = new FakeDoor("d").sign(new Location(worldA, 10, 0, 0)).block(new Location(worldA, 2, 0, 0));
		Location player = new Location(worldA, 0, 0, 0);
		FakeDoor farDoor = new FakeDoor("far").sign(new Location(worldA, 4, 0, 0));
		assertSame(door, DoorProximityManager.nearestDoorIn(List.of(farDoor, door), player, 100.0));
	}
}
