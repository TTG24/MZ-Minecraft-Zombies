package com.theprogrammingturkey.comz.game.features;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.GunAnimMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Drinking a perk, World at War style: the perk's bottle (mzguns:perk_<perk>, its colours) in the hand, opened, drunk in
 * a few gulps, thrown away, a burp; the perk takes effect when it's done. It always plays out: while drinking a player
 * can't shoot, reload, throw, switch weapons, swap hands, drop or use anything. The bottle sits in the off hand so
 * everyone sees it; players with the client mod see the whole drink in first person (GunAnimMessages.DRINK).
 */
public final class PerkDrink implements Listener
{
	/** How long a drink takes (ticks), and when its sounds play. */
	public static final int TICKS = 54;
	private static final int OPEN = 14, SMASH = 44, BURP = 48;
	private static final int[] GULPS = {24, 29, 34};

	private record Session(ItemStack oldOffhand, int[] tasks)
	{
	}

	private static final Map<UUID, Session> DRINKING = new HashMap<>();

	public static boolean isDrinking(Player player)
	{
		return DRINKING.containsKey(player.getUniqueId());
	}

	/** The perk's bottle: what's drunk, and what the mod draws. */
	public static ItemStack bottle(PerkType perk)
	{
		ItemStack stack = new ItemStack(Material.GLASS_BOTTLE);
		ItemMeta meta = stack.getItemMeta();
		meta.setItemModel(new NamespacedKey("mzguns", "perk_" + perk.name().toLowerCase(Locale.ROOT)));
		meta.displayName(Colors.legacy(Colors.GOLD + perk.name().replace('_', ' ')));
		meta.setMaxStackSize(1);
		stack.setItemMeta(meta);
		return stack;
	}

	/** Drinks the perk, then runs `done` (gives its effect). A player already drinking gets it at once. */
	public static void drink(Player player, PerkType perk, Runnable done)
	{
		if(isDrinking(player))
		{
			done.run();
			return;
		}
		ItemStack old = player.getInventory().getItemInOffHand();
		player.getInventory().setItemInOffHand(bottle(perk));
		GunAnimMessages.send(player, GunAnimMessages.DRINK, TICKS);
		int[] tasks = new int[GULPS.length + 4];
		int i = 0;
		tasks[i++] = sound(player, OPEN, "mzguns:perk.open", 1.0f);
		for(int g : GULPS)
			tasks[i++] = sound(player, g, "mzguns:perk.gulp", 0.9f);
		tasks[i++] = COMZombies.scheduleTask(SMASH, () ->
		{
			// thrown away: gone from the hand, smashing a little way off
			if(isDrinking(player))
				player.getInventory().setItemInOffHand(null);
			player.getWorld().playSound(player.getLocation().add(player.getLocation().getDirection().setY(0).multiply(1.5)),
					"mzguns:perk.smash", SoundCategory.PLAYERS, 0.9f, 1.0f);
		});
		tasks[i++] = sound(player, BURP, "mzguns:perk.burp", 1.0f);
		tasks[i] = COMZombies.scheduleTask(TICKS, () -> finish(player, done));
		DRINKING.put(player.getUniqueId(), new Session(old == null || old.getType().isAir() ? null : old.clone(), tasks));
	}

	private static int sound(Player player, int at, String key, float volume)
	{
		return COMZombies.scheduleTask(at, () ->
		{
			if(player.isOnline())
				player.getWorld().playSound(player.getLocation(), key, SoundCategory.PLAYERS, volume,
						0.95f + COMZombies.rand.nextFloat() * 0.1f);
		});
	}

	private static void finish(Player player, Runnable done)
	{
		Session s = DRINKING.remove(player.getUniqueId());
		if(s == null)
			return;
		player.getInventory().setItemInOffHand(s.oldOffhand());
		if(player.isOnline())
			done.run();
	}

	/** Stops a drink without its effect (the player left, or the match ended): the off hand is put back. */
	public static void cancel(Player player)
	{
		Session s = DRINKING.remove(player.getUniqueId());
		if(s == null)
			return;
		for(int task : s.tasks())
			Bukkit.getScheduler().cancelTask(task);
		player.getInventory().setItemInOffHand(s.oldOffhand());
	}

	// ---- nothing else while drinking

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent event)
	{
		if(isDrinking(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInteractEntity(PlayerInteractEntityEvent event)
	{
		if(isDrinking(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event)
	{
		if(isDrinking(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSlot(PlayerItemHeldEvent event)
	{
		if(isDrinking(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event)
	{
		if(isDrinking(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event)
	{
		if(isDrinking(event.getPlayer()))
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onClick(InventoryClickEvent event)
	{
		if(event.getWhoClicked() instanceof Player p && isDrinking(p))
			event.setCancelled(true);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event)
	{
		cancel(event.getPlayer());
	}
}
