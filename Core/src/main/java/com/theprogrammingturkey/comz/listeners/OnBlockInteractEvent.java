package com.theprogrammingturkey.comz.listeners;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class OnBlockInteractEvent implements Listener
{
	private static final java.util.Map<java.util.UUID, Object[]> LAST_CLICK = new java.util.HashMap<>();

	@EventHandler
	public void hitEvent(PlayerInteractEvent event)
	{
		if(event.getPlayer().getInventory().getItemInMainHand().getType() == Material.WOODEN_SWORD)
		{
			Player player = event.getPlayer();
			if(!COMZombies.getPlugin().activeActions.containsKey(player))
				return;
			// One click can arrive twice (main hand, then the off hand when the first was cancelled; or repeated for the same
			// block): setup tools toggle on each click, so a second copy undid the first (a spawn could never be removed).
			if(event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND)
			{
				event.setCancelled(true);
				return;
			}
			long now = System.currentTimeMillis();
			String key = event.getAction() + "@" + (event.getClickedBlock() == null ? "-" : event.getClickedBlock().getLocation().toVector());
			Object[] last = LAST_CLICK.get(player.getUniqueId());
			if(last != null && key.equals(last[0]) && now - (Long) last[1] < 200)
			{
				event.setCancelled(true);
				return;
			}
			LAST_CLICK.put(player.getUniqueId(), new Object[] {key, now});
			COMZombies.getPlugin().activeActions.get(player).onPlayerInteractEvent(event);
		}
		else if(event.getClickedBlock() != null && event.getClickedBlock().getType().equals(Material.CHEST))
		{
			Game game = GameManager.INSTANCE.getGame(event.getPlayer());
			if(game == null)
				return;
			event.setCancelled(true);
			// Right-clicking a chest mystery box uses it, the same as its F prompt
			if(event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
					|| game.getStatus() != Game.GameStatus.INGAME)
				return;
			com.theprogrammingturkey.comz.game.features.RandomBox box = game.boxManager.getBox(event.getClickedBlock().getLocation());
			if(box == null || !box.isChestBox())
				return;
			Player player = event.getPlayer();
			if(!game.boxManager.isMultiBox() && !game.isFireSale() && game.boxManager.getCurrentbox() != box && !box.canPickWeapon(player))
			{
				com.theprogrammingturkey.comz.util.CommandUtil.sendMessageToPlayer(player, com.theprogrammingturkey.comz.util.Colors.RED + "The mystery box is not here right now!");
				return;
			}
			com.theprogrammingturkey.comz.game.signs.GameSigns.MYSTERY_BOX.onInteract(game, player, box.getLocation(), java.util.Collections.emptyMap());
		}
	}
}
