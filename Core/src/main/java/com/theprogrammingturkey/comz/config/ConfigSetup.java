package com.theprogrammingturkey.comz.config;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Sound;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.leaderboards.Leaderboard;

public class ConfigSetup
{
	/**
	 * Points a player starts with when they join a game.
	 */
	public int startingPoints;
	/**
	 * Whether players join a game wearing starting armor.
	 */
	public boolean startingArmor;
	/**
	 * Starting armor material for the helmet slot ("none" leaves the slot empty).
	 */
	public String startingArmorHelmet;
	/**
	 * Starting armor material for the chestplate slot ("none" leaves the slot empty).
	 */
	public String startingArmorChestplate;
	/**
	 * Starting armor material for the leggings slot ("none" leaves the slot empty).
	 */
	public String startingArmorLeggings;
	/**
	 * Starting armor material for the boots slot ("none" leaves the slot empty).
	 */
	public String startingArmorBoots;
	/**
	 * Last round a new player may join a running lobby-less match (0 disables late joins).
	 */
	public int lateJoinRound;
	/**
	 * Default points when a player shoots a zombie.
	 */
	public int pointsOnHit;
	/**
	 * Default points when a player kills a zombie.
	 */
	public int pointsOnKill;
	/**
	 * Time it takes to revive a player.
	 */
	public int reviveTimer;
	/**
	 * Maximum wave.
	 */
	public int maxWave;
	/**
	 * Interval between round change and zombie spawn.
	 */
	public int waveSpawnInterval;
	/**
	 * Max zombies that can be contained in an arena or game.
	 */
	public int maxZombies;
	/**
	 * Does the plugin nag the player a message when they login?
	 */
	public String configVersion;
	/**
	 * Time it takes to reload a gun.
	 */
	public int reloadTime;
	/**
	 * Time double points is active for.
	 */
	public int doublePointsTimer;
	/**
	 * Time insta kill is active for.
	 */
	public int instaKillTimer;
	/**
	 * Maximum range the Reviver can remove from whoever (s)he is reviving.
	 */
	public int reviveRange;
	/**
	 * Maximum range melees are allowed for. Values over 6 -> 6 (Maximum vanilla reach) for now.
	 */
	public float meleeRange;
	/**
	 * Time Fire salse is active for.
	 */
	public int fireSaleTimer;
	/**
	 * Time it takes for an arena to start.
	 */
	public int arenaStartTime;
	/**
	 * Max possible perks a player can obtain.
	 */
	public int maxPerks;

	public int KillMoney;

	public float roundSoundVolume;

	/**
	 * Volume for the bundled round start/end music; 0 disables it.
	 */
	public float roundMusicVolume;

	public double zombieDamage;

	public double juggernogHealth;

	/**
	 * Seconds players stay in spectator mode in the arena after a match ends, so they can
	 * hear the game-over stinger (and see the post-match leaderboard); 0 returns them home
	 * immediately.
	 */
	public int gameOverSpectateSeconds;

	public double healTime;

	/**
	 * Sounds cycled through in order while zombies break a barrier.
	 */
	public List<Sound> barrierDamageSounds;
	/**
	 * Sound when a barrier is fully broken (null mutes it).
	 */
	public Sound barrierBreakSound;
	/**
	 * Sound when a player repairs a barrier, played with alternating pitch (null mutes it).
	 */
	public Sound barrierRepairSound;

	/**
	 * Main method to assign values to every field.
	 */
	public void setup()
	{
		COMZombies plugin = COMZombies.getPlugin();
		doublePointsTimer = plugin.getConfig().getInt("config.gameSettings.doublePointsTimer");
		instaKillTimer = plugin.getConfig().getInt("config.gameSettings.instaKillTimer");
		fireSaleTimer = plugin.getConfig().getInt("config.gameSettings.fireSaleTimer");
		maxZombies = (int) plugin.getConfig().getDouble("config.gameSettings.maxZombies");
		waveSpawnInterval = plugin.getConfig().getInt("config.gameSettings.waveSpawnInterval");
		startingPoints = plugin.getConfig().getInt("config.gameSettings.startingPoints", 500);
		startingArmor = plugin.getConfig().getBoolean("config.gameSettings.startingArmor", true);
		startingArmorHelmet = plugin.getConfig().getString("config.gameSettings.startingArmorMaterials.helmet", "LEATHER_HELMET");
		startingArmorChestplate = plugin.getConfig().getString("config.gameSettings.startingArmorMaterials.chestplate", "LEATHER_CHESTPLATE");
		startingArmorLeggings = plugin.getConfig().getString("config.gameSettings.startingArmorMaterials.leggings", "LEATHER_LEGGINGS");
		startingArmorBoots = plugin.getConfig().getString("config.gameSettings.startingArmorMaterials.boots", "LEATHER_BOOTS");
		lateJoinRound = Math.max(0, plugin.getConfig().getInt("config.gameSettings.lateJoinRound", 5));
		pointsOnHit = plugin.getConfig().getInt("config.gameSettings.defaultPointsOnHit");
		pointsOnKill = plugin.getConfig().getInt("config.gameSettings.defaultPointsOnKill");
		maxWave = plugin.getConfig().getInt("config.gameSettings.maxWave");
		reviveTimer = plugin.getConfig().getInt("config.ReviveSettings.ReviveTimer");
		reviveRange = Math.min(plugin.getConfig().getInt("config.ReviveSettings.ReviveRange"), 6);
		meleeRange = (float) plugin.getConfig().getDouble("config.gameSettings.MeleeRange");
		configVersion = plugin.getConfig().getString("vID");
		reloadTime = plugin.getConfig().getInt("config.gameSettings.reloadTime");
		roundSoundVolume = (float) plugin.getConfig().getDouble("config.gameSettings.roundSoundVolume");
		roundMusicVolume = (float) plugin.getConfig().getDouble("config.gameSettings.roundMusicVolume", 1.0);
		arenaStartTime = plugin.getConfig().getInt("config.gameSettings.arenaStartTime");
		gameOverSpectateSeconds = Math.max(0, plugin.getConfig().getInt("config.gameSettings.gameOverSpectateSeconds", 30));
		// We only have a max of 4 inventory slots for perks
		maxPerks = Math.min(plugin.getConfig().getInt("config.perks.maxPerks", 4), 4);
		KillMoney = plugin.getConfig().getInt("config.Economy.MoneyPerKill");
		//PistolMaterial = plugin.getConfig().getInt("config.Guns.PistolMaterial");
		zombieDamage = plugin.getConfig().getDouble("config.gameSettings.zombieDamage", 9);
		juggernogHealth = plugin.getConfig().getDouble("config.perks.juggernogHealth", 2.5);
		healTime = plugin.getConfig().getDouble("config.gameSettings.healTime", 5);

		barrierDamageSounds = parseBarrierSounds(plugin.getConfig().getStringList("config.gameSettings.barrierSounds.zombieDamage"),
				List.of(Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR));
		barrierBreakSound = parseBarrierSound(plugin.getConfig().getString("config.gameSettings.barrierSounds.zombieBreak"), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR);
		barrierRepairSound = parseBarrierSound(plugin.getConfig().getString("config.gameSettings.barrierSounds.playerRepair"), Sound.BLOCK_BONE_BLOCK_BREAK);

		Leaderboard.loadLeaderboard();
	}

	private static Sound parseBarrierSound(String name, Sound fallback)
	{
		if(name == null)
			return fallback;
		name = name.trim();
		if(name.isEmpty())
			return null;
		Sound sound = com.theprogrammingturkey.comz.util.ApiCompat.getSound(name);
		if(sound != null)
			return sound;
		final String soundName = name;
		COMZombies.log.log(Level.WARNING, () -> "Invalid barrier sound \"" + soundName + "\" in config.yml, using the default instead.");
		return fallback;
	}

	private static List<Sound> parseBarrierSounds(List<String> names, List<Sound> fallback)
	{
		List<Sound> sounds = new ArrayList<>();
		for(String name : names)
		{
			if(name == null || name.isBlank())
				continue;
			Sound sound = com.theprogrammingturkey.comz.util.ApiCompat.getSound(name);
			if(sound == null)
			{
				final String soundName = name;
				COMZombies.log.log(Level.WARNING, () -> "Invalid barrier sound \"" + soundName + "\" in config.yml, ignoring it.");
				continue;
			}
			sounds.add(sound);
		}
		return sounds.isEmpty() ? fallback : sounds;
	}
}