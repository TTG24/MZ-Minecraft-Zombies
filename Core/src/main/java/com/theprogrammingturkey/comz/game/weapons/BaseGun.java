package com.theprogrammingturkey.comz.game.weapons;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.util.ApiCompat;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public abstract class BaseGun extends Weapon
{
	public int fireDelay;
	public double distance;
	public int clipAmmo;
	public Color particleColor = Color.GRAY;
	public boolean multiHit;

	public Sound sound;
	/** A resource pack sound ("mzguns:gun.mp40"), played instead of `sound` when set. */
	public String soundKey;
	/** "semi" (a shot a click), "auto" (fires while the trigger is held: the client mod), or "burst" (`burst` shots a click). */
	public String fireMode = "semi";
	public int burst = 3;
	/** Chain lightning (the Wunderwaffe, ChainLightning): how many zombies a shot kills in all, jumping up to chainRange blocks. */
	public int chain = 0;
	public double chainRange = 7;

	public BaseGun(String name, WeaponType type)
	{
		super(name, type);
	}

	@Override
	public void loadWeapon(JsonObject json)
	{
		super.loadWeapon(json);
		this.clipAmmo = CustomConfig.getInt(json, "clip_ammo", 1);
		this.fireDelay = CustomConfig.getInt(json, "fire_delay", 5);
		this.distance = CustomConfig.getDouble(json, "max_distance", 30);
		this.multiHit = CustomConfig.getBoolean(json, "multi_hit", false);

		String particleColor = CustomConfig.getString(json, "particle_color", "808080");
		if(particleColor.matches("\\A[0-9a-fA-F]{6}$"))
			this.particleColor = Color.fromRGB(Integer.parseInt(particleColor, 16));
		else if(particleColor.matches("\\A\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*$"))
		{
			// "168,229,73"
			String[] c = particleColor.split(",");
			this.particleColor = Color.fromRGB(Math.min(255, Integer.parseInt(c[0].trim())), Math.min(255, Integer.parseInt(c[1].trim())),
					Math.min(255, Integer.parseInt(c[2].trim())));
		}

		String defaultSound = this.isPackAPunched() ? "ENTITY_GHAST_SHOOT" : "BLOCK_LAVA_POP";
		String soundName = CustomConfig.getString(json, "sound", defaultSound);
		this.soundKey = soundName.contains(":") ? soundName : null;
		Sound gunSound = this.soundKey != null ? null : ApiCompat.getSound(soundName);
		this.sound = gunSound != null ? gunSound : ApiCompat.getSound(defaultSound);
		this.fireMode = CustomConfig.getString(json, "fire_mode", "semi").toLowerCase(java.util.Locale.ROOT);
		this.burst = Math.max(2, CustomConfig.getInt(json, "burst", 3));
		this.chain = Math.max(0, CustomConfig.getInt(json, "chain", 0));
		this.chainRange = CustomConfig.getDouble(json, "chain_range", 7);
	}

	public void updateAmmo(int clip, int total)
	{
		clipAmmo = clip;
		totalAmmo = total;
	}

	public boolean isPackAPunched()
	{
		return false;
	}

	public abstract boolean isPackAPunchable();

	public abstract PackAPunchGun getPackAPunchGun();

	@Override
	public WeaponInstance getNewInstance(Player player, int slot)
	{
		return new GunInstance(this, player, slot);
	}
}
