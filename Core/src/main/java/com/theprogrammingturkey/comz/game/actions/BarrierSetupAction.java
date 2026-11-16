package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /zombies addbarrier: click blocks with the wooden sword to select a barrier, type "done" to save it. When the reward comes
 * from the config (barrierRewardFromConfig), the setup keeps going after each barrier so a whole arena can be done in one
 * go: "done" saves the barrier and starts the next one, "finish" (or "done" with nothing selected) ends it, "cancel" drops
 * only the barrier being selected. Otherwise the reward is asked for after each barrier and the setup ends with it.
 * The barrier being selected is outlined yellow, the ones saved in this session green.
 */
public class BarrierSetupAction extends BaseAction
{
	private Barrier barrier;
	private final List<Barrier> saved = new ArrayList<>();
	private final boolean batch;
	/** Toggled by typing "tall": each click also takes the block above. */
	private boolean tall = false;

	/** 0 = selecting blocks, 2 = waiting for the reward to be typed, 3 = finished */
	private int state = 0;

	public BarrierSetupAction(Player player, Game game, Barrier barrier)
	{
		super(player, game);
		this.barrier = barrier;
		this.batch = Barrier.rewardFromConfig();

		COMZombies.scheduleTask(1, this::outline);
		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "------" + Colors.RED + "Barrier Setup" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Click blocks with the wooden sword to select a barrier (click again to unselect). Zombies go to the nearest barrier they can reach, so no spawn points are needed.");
		if(batch)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Type " + Colors.RED + "done" + Colors.GRAY + " to save it and start the next one, "
					+ Colors.RED + "finish" + Colors.GRAY + " when every barrier is done. Each pays " + Barrier.configReward() + " points per repair stage (from the config).");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "cancel" + Colors.GRAY + " drops only the barrier being selected; the saved ones stay.");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Type done, then type the points given for each repair stage of the barrier.");
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Type cancel to cancel this operation.");
		}
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Type " + Colors.RED + "tall" + Colors.GRAY + " to toggle selecting the block above each clicked block too.");
	}

	@Override
	public void cancelAction()
	{
		state = 3;
		game.resetSpawnLocationBlocks();
		for(Barrier b : game.barrierManager.getBarriers())
			b.repairFull();
		if(batch && !saved.isEmpty())
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier setup ended. " + Colors.GRAY + saved.size() + " barrier(s) saved this session are kept; the one being selected was dropped.");
		else
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier setup operation has been canceled!");
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		Block clickedBlock = event.getClickedBlock();
		if(clickedBlock == null || state != 0 || event.getHand() != EquipmentSlot.HAND)
			return;
		if(!event.getAction().equals(Action.LEFT_CLICK_BLOCK) && !event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
			return;
		event.setCancelled(true);

		Block above = clickedBlock.getRelative(0, 1, 0);
		if(barrier.hasBarrierLoc(clickedBlock))
		{
			barrier.removeBarrierBlock(clickedBlock);
			if(tall && barrier.hasBarrierLoc(above))
				barrier.removeBarrierBlock(above);
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Block removed! (" + barrier.getBlocks().size() + " selected)");
			return;
		}
		Barrier other = game.barrierManager.getBarrier(clickedBlock.getLocation());
		if(other != null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "That block already belongs to barrier " + other.getID() + "! Use /z eb " + other.getID() + " to change it.");
			return;
		}
		barrier.addBarrierBlock(clickedBlock.getLocation());
		if(tall && canAddAbove(above))
			barrier.addBarrierBlock(above.getLocation());
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Block added! (" + barrier.getBlocks().size() + " selected)");
	}

	/** In tall mode the block above a clicked one (air too) is taken as well, unless it is already selected or in another barrier. */
	private boolean canAddAbove(Block above)
	{
		return !barrier.hasBarrierLoc(above) && game.barrierManager.getBarrier(above.getLocation()) == null;
	}

	@Override
	public void onChatMessage(String message)
	{
		if(state == 3)
			return;
		String msg = message.trim();

		if(state == 0 && msg.equalsIgnoreCase("tall"))
		{
			tall = !tall;
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Tall mode " + (tall ? Colors.DARK_GREEN + "ON" + Colors.GRAY + ": clicking a block also selects/unselects the block above it."
					: Colors.RED + "OFF" + Colors.GRAY + ": clicks select one block."));
			return;
		}

		if(state == 0 && batch && (msg.equalsIgnoreCase("finish") || (msg.equalsIgnoreCase("done") && barrier.getBlocks().isEmpty() && !saved.isEmpty())))
		{
			if(!barrier.getBlocks().isEmpty())
				save(Barrier.configReward());
			end();
			return;
		}

		if(state == 0 && msg.equalsIgnoreCase("done"))
		{
			int air = barrier.syncWithWorld();
			if(air > 0)
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + air + " selected spot(s) are air and will stay open (placed temp blocks removed).");
			if(barrier.getBlocks().isEmpty())
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Select at least one barrier block first!");
				return;
			}
			if(batch)
			{
				if(save(Barrier.configReward()))
					next();
				return;
			}
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier block for barrier set!");
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Now type in the points a player will receive per repair stage of the barrier.");
			state = 2;
		}
		else if(state == 2)
		{
			if(!msg.matches("[0-9]{1,5}"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + msg + " is not a number!");
				return;
			}
			if(save(Integer.parseInt(msg)))
				end();
		}
		else if(state == 0)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + (batch ? "Type done, finish, tall or cancel." : "Type done, tall or cancel."));
		}
	}

	private boolean save(int reward)
	{
		barrier.syncWithWorld();
		barrier.setReward(reward);
		if(!game.barrierManager.addBarrier(barrier))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Could not save the barrier: the arena must not be in a game, and its blocks must not belong to another barrier.");
			return false;
		}
		saved.add(barrier);
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Barrier " + saved.size() + " saved (" + barrier.getBlocks().size() + " block(s), " + reward + " points per stage).");
		return true;
	}

	private void next()
	{
		barrier = new Barrier(game.barrierManager.getNextBarrierNumber(), game);
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Select the next barrier, or type " + Colors.RED + "finish" + Colors.GRAY + ".");
	}

	private void end()
	{
		state = 3;
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier setup complete!" + (batch ? Colors.GRAY + " " + saved.size() + " barrier(s) added." : ""));
		COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
	}

	private void outline()
	{
		if(state == 3 || !player.isOnline() || COMZombies.getPlugin().activeActions.get(player) != this)
			return;
		for(Barrier b : saved)
			Highlight.blocks(player, b.getBlocks(), Highlight.INTACT);
		Highlight.blocks(player, barrier.getBlocks(), Highlight.BARRIER);
		COMZombies.scheduleTask(10, this::outline);
	}
}
