package com.theprogrammingturkey.comz.game.signs;

import java.util.HashMap;
import java.util.Map;

/**
 * All the game sign types, looked up by the name typed on line 2 when placing a sign, or by their saved type.
 */
public class GameSigns
{
	private static final Map<String, IGameSign> BY_NAME = new HashMap<>();
	private static final Map<String, IGameSign> BY_TYPE = new HashMap<>();

	public static final MysteryBoxSign MYSTERY_BOX = register(new MysteryBoxSign(), "mystery box", "mysterybox", "box", "randombox");
	public static final JoinSign JOIN = register(new JoinSign(), "join");
	public static final SpectateSign SPECTATE = register(new SpectateSign(), "spectate", "spec");
	public static final KitSign KIT = register(new KitSign(), "kit");
	public static final PerkMachineSign PERK = register(new PerkMachineSign(), "perk", "perk machine");
	public static final PackAPunchSign PACK_A_PUNCH = register(new PackAPunchSign(), "pack-a-punch", "pack", "pack a punch");
	public static final DoorSign DOOR = register(new DoorSign(), "door");
	public static final GunSign GUN = register(new GunSign(), "gun");
	public static final PowerSign POWER = register(new PowerSign(), "power");
	public static final TeleporterSign TELEPORTER = register(new TeleporterSign(), "teleporter");
	public static final AmmoCrateSign AMMO_CRATE = register(new AmmoCrateSign(), "ammo crate", "ammo");
	public static final GrenadeSign GRENADE = register(new GrenadeSign(), "grenade");

	private static <T extends IGameSign> T register(T sign, String... names)
	{
		for(String name : names)
			BY_NAME.put(name, sign);
		BY_TYPE.put(sign.getType(), sign);
		return sign;
	}

	/**
	 * @param name what was typed on line 2 of the sign, e.g. "gun" or "mystery box"
	 */
	public static IGameSign fromName(String name)
	{
		return name == null ? null : BY_NAME.get(name.toLowerCase());
	}

	public static IGameSign fromType(String type)
	{
		return BY_TYPE.get(type);
	}
}
