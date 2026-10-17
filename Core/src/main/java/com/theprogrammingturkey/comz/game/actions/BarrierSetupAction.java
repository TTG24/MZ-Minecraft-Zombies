package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class BarrierSetupAction extends BaseAction
{
	private final Barrier barrier;

	private int state = 0;

	public BarrierSetupAction(Player player, Game game, Barrier barrier)
	{
		super(player, game);
		this.barrier = barrier;

		game.showSpawnLocations();
		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "------" + Colors.RED + "Barrier Setup" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Select each block individually to be the barrier using the wooden sword.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Once you have this complete, type done, go into the room the barrier blocks and click on any ender portal frames (spawn points) in there with the sword.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Lastly, type in chat the points given for each repair stage of the barrier.");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Type cancel to cancel this operation.");
	}

	public void cancelAction()
	{
		game.resetSpawnLocationBlocks();
		for(Barrier barrier : game.barrierManager.getBarriers())
		{
			barrier.repairFull();
		}
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier setup operation has been canceled!");
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		Block clickedBlock = event.getClickedBlock();
		if(clickedBlock == null)
			return;

		if(state == 0)
		{
			if(!barrier.hasBarrierLoc(clickedBlock))
			{
				if(event.getAction().equals(Action.LEFT_CLICK_BLOCK) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
				{
					barrier.addBarrierBlock(clickedBlock.getLocation());
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "Block added!");
					event.setCancelled(true);
				}
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "The block has already been added!");
			}
		}
		else if(state == 1)
		{
			if(clickedBlock.getType().equals(Material.END_PORTAL_FRAME))
			{
				SpawnPoint point = game.spawnManager.getSpawnPoint(clickedBlock.getLocation());
				if(point == null || barrier.hasSpawnPoint(point))
					return;

				barrier.addSpawnPoint(point);
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Spawn point selected!");
				event.setCancelled(true);
			}
		}
	}

	@Override
	public void onChatMessage(String message)
	{
		if(barrier == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You have not selected a block for the barrier yet!");
			return;
		}

		if(message.equalsIgnoreCase("done"))
		{
			if(state == 0)
			{
				if(barrier.getBlocks().isEmpty())
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "Select at least one barrier block first!");
					return;
				}
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier block for barrier set!");
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Now select the spawn points that are located behind the barrier.");
				state++;
			}
			else if(state == 1)
			{
				if(barrier.getSpawnPoints().isEmpty())
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "Select at least one spawn point first!");
					return;
				}
				state++;
				COMZombies.scheduleTask(1, game::resetSpawnLocationBlocks);

				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Spawn point for barrier set!");
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Now type in the points a player will receive per repair stage of the barrier.");
			}
		}
		else if(state == 2)
		{
			if(!message.matches("[0-9]{1,5}"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + message + " is not a number!");
				return;
			}

			int price = Integer.parseInt(message);

			barrier.setReward(price);
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier setup complete!");
			barrier.getGame().barrierManager.addBarrier(barrier);
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
	}
}
