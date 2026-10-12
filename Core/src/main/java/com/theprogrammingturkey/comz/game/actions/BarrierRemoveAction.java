package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class BarrierRemoveAction extends BaseAction
{
	public BarrierRemoveAction(Player player, Game game)
	{
		super(player, game);
		for(Barrier barrier : game.barrierManager.getBarriers())
			barrier.repairFull();
		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
		CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Click a barrier block with the wooden sword to remove that barrier.");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Type done or cancel to exit this operation.");
	}

	private Barrier findBarrier(Location location)
	{
		Barrier barrier = game.barrierManager.getBarrier(location);
		return barrier != null ? barrier : game.barrierManager.getBarrierFromRepair(location);
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		if(event.getClickedBlock() == null || (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK))
			return;
		Barrier barrier = findBarrier(event.getClickedBlock().getLocation());
		if(barrier == null)
			return;
		event.setCancelled(true);
		removeBarrier(barrier);
	}

	@Override
	public void onBlockBreakevent(BlockBreakEvent event)
	{
		Barrier barrier = findBarrier(event.getBlock().getLocation());
		if(barrier == null)
			return;
		event.setCancelled(true);
		removeBarrier(barrier);
	}

	private void removeBarrier(Barrier barrier)
	{
		game.barrierManager.removeBarrier(player, barrier);
		if(game.barrierManager.getTotalBarriers() == 0)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "No barriers left!");
			COMZombies.getPlugin().activeActions.remove(player);
		}
	}

	@Override
	public void onChatMessage(String message)
	{
		if(message.equalsIgnoreCase("done"))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Exiting barrier removal!");
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
	}
}