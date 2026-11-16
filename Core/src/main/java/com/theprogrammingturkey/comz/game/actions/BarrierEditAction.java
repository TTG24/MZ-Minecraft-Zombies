package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edits an existing barrier in place. Chat: "blocks" (click blocks with the wooden sword to add or remove them),
 * "reward <n>", "done" (save), "cancel" (undo everything). Spawn links are no longer used (zombies pick the nearest
 * reachable barrier), so old links are left as they are.
 * The barrier being edited is outlined in yellow.
 */
public class BarrierEditAction extends BaseAction
{
	private enum Mode
	{
		BLOCKS, SPAWNS
	}

	private final Barrier barrier;
	private final Map<Block, Material> savedBlocks;
	private final List<SpawnPoint> savedSpawns;
	private final int savedReward;
	private Mode mode = Mode.BLOCKS;
	private boolean finished = false;
	/** Toggled by typing "tall": each click also takes the block above. */
	private boolean tall = false;

	public BarrierEditAction(Player player, Game game, Barrier barrier)
	{
		super(player, game);
		this.barrier = barrier;
		// An editable barrier has to be whole, so every block is a real block again
		barrier.repairFull();
		savedBlocks = barrier.copyBlockMaterials();
		savedSpawns = new ArrayList<>(barrier.getSpawnPoints());
		savedReward = barrier.getReward();

		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "------" + Colors.RED + "Edit Barrier " + barrier.getID() + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mode: BLOCKS (outlined in yellow). Click blocks with the wooden sword to add them to / remove them from the barrier.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Type: " + Colors.RED + "blocks" + Colors.GRAY + " | " + Colors.RED + "reward <n>" + Colors.GRAY + " (now " + savedReward + ") | " + Colors.RED + "tall" + Colors.GRAY + " (also take the block above) | " + Colors.RED + "done" + Colors.GRAY + " | " + Colors.RED + "cancel");
		// One tick later: the command only stores this action after the constructor returns, and the loop stops if it is not stored
		COMZombies.scheduleTask(1, this::outline);
	}

	@Override
	public void cancelAction()
	{
		if(finished)
			return;
		finished = true;
		barrier.restore(savedBlocks, savedSpawns, savedReward);
		game.resetSpawnLocationBlocks();
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier edit canceled, nothing was changed.");
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		Block clicked = event.getClickedBlock();
		if(clicked == null || finished)
			return;
		if(event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;

		if(mode == Mode.BLOCKS)
		{
			event.setCancelled(true);
			Block above = clicked.getRelative(0, 1, 0);
			if(barrier.hasBarrierLoc(clicked))
			{
				boolean both = tall && barrier.hasBarrierLoc(above);
				if(barrier.getBlocks().size() <= (both ? 2 : 1))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "A barrier needs at least one block!");
					return;
				}
				barrier.removeBarrierBlock(clicked);
				if(both)
					barrier.removeBarrierBlock(above);
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Block removed from the barrier! (" + barrier.getBlocks().size() + " left)");
			}
			else
			{
				barrier.addBarrierBlock(clicked.getLocation());
				if(tall && !barrier.hasBarrierLoc(above))
				{
					var other = game.barrierManager.getBarrier(above.getLocation());
					if(other == null || other == barrier)
						barrier.addBarrierBlock(above.getLocation());
				}
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Block added to the barrier! (" + barrier.getBlocks().size() + ")");
			}
			draw();
		}
		else if(clicked.getType() == Material.END_PORTAL_FRAME)
		{
			SpawnPoint point = game.spawnManager.getSpawnPoint(clicked.getLocation());
			if(point == null)
				return;
			event.setCancelled(true);
			if(barrier.hasSpawnPoint(point))
			{
				barrier.removeSpawnPoint(point);
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Spawn point " + point.getID() + " unlinked.");
			}
			else
			{
				barrier.addSpawnPoint(point);
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Spawn point " + point.getID() + " linked.");
			}
			draw();
		}
	}

	@Override
	public void onChatMessage(String message)
	{
		if(finished)
			return;
		String msg = message.trim().toLowerCase();
		if(msg.equals("tall"))
		{
			tall = !tall;
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Tall mode " + (tall ? Colors.DARK_GREEN + "ON" + Colors.GRAY + ": clicking a block also adds/removes the block above it."
					: Colors.RED + "OFF" + Colors.GRAY + ": clicks change one block."));
		}
		else if(msg.equals("blocks"))
		{
			mode = Mode.BLOCKS;
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mode: BLOCKS. The barrier's " + barrier.getBlocks().size() + " block(s) are outlined in yellow. Click blocks to add/remove them.");
			draw();
		}
		else if(msg.startsWith("reward"))
		{
			String num = msg.substring(6).trim();
			if(!num.matches("[0-9]{1,5}"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Use: reward <number>");
				return;
			}
			barrier.setReward(Integer.parseInt(num));
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Reward per repair stage set to " + num + ".");
			if(Barrier.rewardFromConfig())
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Note: barrierRewardFromConfig is on, so every barrier pays " + Barrier.configReward() + " (from the config) until it is turned off.");
		}
		else if(msg.equals("done"))
		{
			int dropped = barrier.syncWithWorld();
			if(dropped > 0)
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + dropped + " selected spot(s) are air and will stay open (placed temp blocks removed).");
			if(barrier.getBlocks().isEmpty())
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "A barrier needs at least one block!");
				return;
			}
			finished = true;
			barrier.repairFull();
			game.resetSpawnLocationBlocks();
			GameManager.INSTANCE.saveAllGames();
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Barrier " + barrier.getID() + " saved: " + barrier.getBlocks().size() + " block(s), reward " + barrier.getReward() + ".");
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Type blocks, reward <n>, tall, done or cancel.");
		}
	}

	private void outline()
	{
		if(finished || !player.isOnline() || COMZombies.getPlugin().activeActions.get(player) != this)
			return;
		draw();
		COMZombies.scheduleTask(10, this::outline);
	}

	/** BLOCKS mode shows the barrier's blocks; SPAWNS mode shows every spawn point (linked ones green, the rest red). */
	private void draw()
	{
		if(mode == Mode.BLOCKS)
		{
			Highlight.blocks(player, barrier.getBlocks(), Highlight.BARRIER);
		}
		else
		{
			for(SpawnPoint sp : game.spawnManager.getPoints())
				Highlight.box(player, sp.getLocation(), barrier.hasSpawnPoint(sp) ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		}
	}
}
