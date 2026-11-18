package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.util.PeacefulArenaMobs;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;

public class ZombieSpawner extends RoundSpawner
{
	/** A zombie spawning within this many blocks of a barrier (horizontally) always breaks that barrier. */
	private static final double SPAWNED_BESIDE_RANGE = 4;

	@Override
	public Mob spawnEntity(Game game, SpawnPoint loc, int wave)
	{
		Location location = new Location(loc.getLocation().getWorld(), loc.getLocation().getBlockX(), loc.getLocation().getBlockY(), loc.getLocation().getBlockZ());
		location.add(0.5, 0, 0.5);
		// Marked as rising before it enters the world, so the rise model (for players without the mod) goes on from its first tick
		Zombie zomb = ownSpawn(() -> location.getWorld().spawn(location, Zombie.class, z ->
		{
			PeacefulArenaMobs.prepare(z);
			// Adult from the start (Minecraft rolls some zombies as babies), and marked as an arena zombie for good, so
			// TacticalMovementPlugin gives it the zombie model for players without the mod
			z.setAdult();
			z.setMetadata("mz_zombie", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), true));
			z.setMetadata(com.theprogrammingturkey.comz.game.ZombieRise.META, new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), com.theprogrammingturkey.comz.game.ZombieRise.TICKS));
		}));
		COMZombies.scheduleTask(10, () ->
		{
			if(zomb.getEquipment() != null)
				zomb.getEquipment().clear();
		});
		zomb.setAdult();
		setFallTolerance(zomb);

		float strength = ((wave * 100f) + 50) / 50f;
		setMaxHealth(zomb, strength);
		zomb.setHealth(strength);

		if(game.getWave() > 4 && COMZombies.rand.nextInt(100) < 20 + (15 * (game.getWave() - 5)))
		{
			setSpeed(zomb, 1.25f);
			com.theprogrammingturkey.comz.game.ZombieSounds.markSprinter(zomb);
		}

		// The zombie goes to the barrier between it and the nearest player and breaks it (or, when it is already broken, walks
		// through it), and only then hunts the players. No barrier close by means it goes straight for the players.
		Barrier b = game.barrierManager.chooseBarrierFor(zomb, loc);
		if(b != null)
			b.initBarrier(zomb);

		// It digs itself up out of the ground first, frozen until it is out
		com.theprogrammingturkey.comz.game.ZombieRise.start(zomb, game.spawnManager.getNearestPlayer(zomb));

		return zomb;
	}
}
