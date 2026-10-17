package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum PerkType
{
	JUGGERNOG,
	SPEED_COLA,
	QUICK_REVIVE,
	DOUBLE_TAP,
	STAMIN_UP,
	PHD_FLOPPER,
	DEADSHOT_DAIQ,
	MULE_KICK,
	ELECTRIC_C,
	DER_WUNDERFIZZ;

	public static PerkType getPerkType(String name)
	{
		for(PerkType pt : values())
			if((Colors.GOLD + pt.toString()).equalsIgnoreCase(name) || (pt.toString().toLowerCase().equalsIgnoreCase(name)))
				return pt;
		if(name.equalsIgnoreCase("der wunderfizz") || name.equalsIgnoreCase("wunderfizz") || name.equalsIgnoreCase("random"))
			return DER_WUNDERFIZZ;
		return null;
	}

	public void initialEffect(final Player player, PerkType type, int slot)
	{
		final World world = player.getLocation().getWorld();
		if(world != null)
		{
			COMZombies.scheduleTask(5, () -> world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1, 1));
			COMZombies.scheduleTask(10, () -> world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1, 1));
			COMZombies.scheduleTask(20, () -> world.playEffect(player.getLocation(), Effect.POTION_BREAK, 1));
		}
		ItemStack stack = new ItemStack(Material.AIR, 1);
		String Perktype = "";
		switch(type)
		{
			case JUGGERNOG ->
			{
				stack = new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1);
				Perktype = "Juggernog";
			}
			case SPEED_COLA ->
			{
				stack = new ItemStack(Material.FEATHER, 1);
				Perktype = "Speed Cola";
			}
			case QUICK_REVIVE ->
			{
				stack = new ItemStack(Material.GLISTERING_MELON_SLICE, 1);
				Perktype = "Quick Revive";
			}
			case DOUBLE_TAP ->
			{
				stack = new ItemStack(Material.REPEATER, 1);
				Perktype = "Double Tap";
			}
			case STAMIN_UP ->
			{
				stack = new ItemStack(Material.SUGAR, 1);
				Perktype = "Stamina Up";
			}
			case PHD_FLOPPER ->
			{
				stack = new ItemStack(Material.FIRE_CHARGE);
				Perktype = "PHD Flopper";
			}
			case DEADSHOT_DAIQ ->
			{
				stack = new ItemStack(Material.GUNPOWDER);
				Perktype = "Deadshot Daiquiri";
			}
			case MULE_KICK ->
			{
				stack = new ItemStack(Material.STRING);
				Perktype = "Mule Kick";
			}
			case ELECTRIC_C ->
			{
				stack = new ItemStack(Material.NETHER_STAR);
				Perktype = "Electric Cherry";
			}
			default -> {}
		}
		player.getInventory().setItem(slot, setItemMeta(stack, Perktype));
		player.updateInventory();
	}

	public static void noPower(Player player)
	{
		World world = player.getLocation().getWorld();
		world.playSound(player.getLocation(), Sound.ENTITY_GHAST_AMBIENT, 1L, 1L);
	}

	private ItemStack setItemMeta(ItemStack item, String type)
	{
		ItemMeta data = item.getItemMeta();
		data.displayName(Colors.legacy(type));
		item.setItemMeta(data);
		return item;
	}

	public ItemStack getPerkItem(PerkType type)
	{
		ItemStack stack = new ItemStack(Material.AIR, 1);
		switch(type)
		{
			case JUGGERNOG -> stack = new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1);
			case SPEED_COLA -> stack = new ItemStack(Material.FEATHER, 1);
			case QUICK_REVIVE -> stack = new ItemStack(Material.GLISTERING_MELON_SLICE, 1);
			case DOUBLE_TAP -> stack = new ItemStack(Material.REPEATER, 1);
			case STAMIN_UP -> stack = new ItemStack(Material.SUGAR, 1);
			case PHD_FLOPPER -> stack = new ItemStack(Material.FIRE_CHARGE);
			case DEADSHOT_DAIQ -> stack = new ItemStack(Material.GUNPOWDER);
			case MULE_KICK -> stack = new ItemStack(Material.STRING);
			case ELECTRIC_C -> stack = new ItemStack(Material.NETHER_STAR);
			default -> {}
		}
		return stack;
	}

	public static PerkType getRandomPerk(List<PerkType> exclude)
	{
		List<PerkType> availablePerks = Arrays.stream(PerkType.values()).filter(pt -> !exclude.contains(pt) && pt != PerkType.DER_WUNDERFIZZ).collect(Collectors.toList());

		if(availablePerks.isEmpty())
			return null;
		// get random perk from list of available perks
		return availablePerks.get(COMZombies.rand.nextInt(availablePerks.size()));
	}
}