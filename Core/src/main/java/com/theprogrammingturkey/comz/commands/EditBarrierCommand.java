package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.actions.BarrierEditAction;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** /zombies editbarrier <arena> [barrierId]: edit a barrier's blocks, spawns and reward in place. Defaults to the nearest barrier. */
public class EditBarrierCommand extends SubCommand
{
	public EditBarrierCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.ADD_BARRIER.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit a barrier");
			return true;
		}
		if(COMZombies.getPlugin().activeActions.containsKey(player))
		{
			CommandUtil.sendMessageToPlayer(player, "You are currently performing another action and cannot edit a barrier right now!");
			return true;
		}
		if(args.length < 2)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z editbarrier <arena> [barrierId] (or stand near the barrier). See /z barriers <arena> for ids.");
			return true;
		}
		Game game = GameManager.INSTANCE.getGame(args[1]);
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "That is not a valid arena!");
			return true;
		}
		if(game.getStatus() != GameStatus.DISABLED && game.getStatus() != GameStatus.WAITING)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barriers can only be edited while the arena is not in a game.");
			return true;
		}

		Barrier target = null;
		if(args.length > 2)
		{
			target = game.barrierManager.getBarrier(args[2]);
		}
		else
		{
			double best = Double.MAX_VALUE;
			for(Barrier b : game.barrierManager.getBarriers())
				for(Block block : b.getBlocks())
				{
					if(!block.getWorld().equals(player.getWorld()))
						continue;
					double d = block.getLocation().distanceSquared(player.getLocation());
					if(d < best)
					{
						best = d;
						target = b;
					}
				}
		}
		if(target == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Barrier not found. Use /z barriers " + args[1] + " to list them.");
			return true;
		}

		COMZombies.getPlugin().activeActions.put(player, new BarrierEditAction(player, game, target));
		return true;
	}
}
