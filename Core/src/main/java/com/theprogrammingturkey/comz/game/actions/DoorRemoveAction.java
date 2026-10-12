package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class DoorRemoveAction extends BaseAction
{
	public DoorRemoveAction(Player player, Game game)
	{
		super(player, game);
		for(Door door : game.doorManager.getDoors())
			door.closeDoor();
		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
		CommandUtil.sendMessageToPlayer(player, ChatColor.GRAY + "Click a door block with the wooden sword to remove that door.");
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Type done or cancel to exit this operation.");
	}

	private Door findDoor(Location location)
	{
		Door door = game.doorManager.getDoorFromBlock(location);
		return door != null ? door : game.doorManager.getDoorFromSign(location);
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		if(event.getClickedBlock() == null || (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK))
			return;
		Door door = findDoor(event.getClickedBlock().getLocation());
		if(door == null)
			return;
		event.setCancelled(true);
		removeDoor(door);
	}

	@Override
	public void onBlockBreakevent(BlockBreakEvent event)
	{
		Door door = findDoor(event.getBlock().getLocation());
		if(door == null)
			return;
		event.setCancelled(true);
		removeDoor(door);
	}

	private void removeDoor(Door door)
	{
		for(Location location : door.getSignsLocations())
			if(BlockUtils.isSign(location.getBlock()))
				BlockUtils.setBlockToAir(location);
		game.doorManager.removeDoor(door);
		GameManager.INSTANCE.saveAllGames();
		CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "Door removed!");
		if(game.doorManager.getDoors().isEmpty())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "No doors left!");
			COMZombies.getPlugin().activeActions.remove(player);
		}
	}

	@Override
	public void onChatMessage(String message)
	{
		if(message.equalsIgnoreCase("done"))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Exiting door removal!");
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
	}
}