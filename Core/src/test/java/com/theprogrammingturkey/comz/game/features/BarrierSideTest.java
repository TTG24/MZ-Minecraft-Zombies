package com.theprogrammingturkey.comz.game.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The geometry that decides which side of a barrier a zombie started on, and whether it has got through. If this is wrong
 * zombies are held at a barrier from the wrong side, or never let go to hunt the players.
 */
class BarrierSideTest
{
	// A wall of three blocks along x at z = 20 (block centres x = 10.5..12.5, z = 20.5).
	private static final double MIN_X = 10.5, MAX_X = 12.5, MIN_Z = 20.5, MAX_Z = 20.5;

	@Test
	void wallAlongXIsCrossedAlongZ()
	{
		double[] side = Barrier.wallSide(MIN_X, MAX_X, MIN_Z, MAX_Z, 11.5, 15.0);
		assertNotNull(side);
		assertEquals(1, side[0], "a wall running along x is crossed along z");
		assertEquals(-1, side[1], "the zombie spawned at smaller z");
	}

	@Test
	void zombieOnItsOwnSideIsHeldAndOnceThroughIsReleased()
	{
		double[] side = Barrier.wallSide(MIN_X, MAX_X, MIN_Z, MAX_Z, 11.5, 15.0);
		// Still outside, in front of the wall: positive offset, not released.
		assertTrue(Barrier.sideOffset(side, 11.5, 17.0) > 0);
		// Standing in the gap: not far enough through to count (release is below -0.75).
		assertTrue(Barrier.sideOffset(side, 11.5, 20.5) > -0.75);
		// Walked through to the arena side: released.
		assertTrue(Barrier.sideOffset(side, 11.5, 24.0) < -0.75);
	}

	@Test
	void theOtherSideWorksTheSameWay()
	{
		double[] side = Barrier.wallSide(MIN_X, MAX_X, MIN_Z, MAX_Z, 11.5, 28.0);
		assertEquals(1, side[0]);
		assertEquals(1, side[1], "spawned at larger z");
		assertTrue(Barrier.sideOffset(side, 11.5, 26.0) > 0);
		assertTrue(Barrier.sideOffset(side, 11.5, 16.0) < -0.75, "through to the smaller-z side");
	}

	@Test
	void wallAlongZIsCrossedAlongX()
	{
		// A wall of three blocks along z at x = 30.
		double[] side = Barrier.wallSide(30.5, 30.5, 5.5, 7.5, 24.0, 6.5);
		assertNotNull(side);
		assertEquals(0, side[0], "a wall running along z is crossed along x");
		assertEquals(-1, side[1]);
		assertTrue(Barrier.sideOffset(side, 35.0, 6.5) < -0.75);
	}

	@Test
	void singleBlockUsesTheAxisTheSpawnIsFurtherOutOn()
	{
		double[] side = Barrier.wallSide(50.5, 50.5, 50.5, 50.5, 50.5, 40.0);
		assertNotNull(side);
		assertEquals(1, side[0], "spawned far out along z");
		side = Barrier.wallSide(50.5, 50.5, 50.5, 50.5, 62.0, 50.5);
		assertEquals(0, side[0], "spawned far out along x");
		assertEquals(1, side[1]);
	}

	@Test
	void spawnLevelWithTheWallCannotBeTold()
	{
		// Within half a block of the wall's centre line: no usable side, so the caller falls back to other rules.
		assertNull(Barrier.wallSide(MIN_X, MAX_X, MIN_Z, MAX_Z, 11.5, 20.7));
	}

	@Test
	void sideIsIndependentOfWhereAlongTheWallTheZombieSpawned()
	{
		// Far to one end of the wall still gives the same side; only the distance across the wall matters.
		double[] a = Barrier.wallSide(MIN_X, MAX_X, MIN_Z, MAX_Z, 2.0, 14.0);
		double[] b = Barrier.wallSide(MIN_X, MAX_X, MIN_Z, MAX_Z, 25.0, 14.0);
		assertEquals(a[0], b[0]);
		assertEquals(a[1], b[1]);
	}
}
