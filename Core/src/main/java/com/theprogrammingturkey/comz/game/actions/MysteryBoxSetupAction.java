package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * /zombies mb <arena> [cost]: click chests (single or double) with the wooden sword to make them mystery boxes, or click a
 * chest box again to remove it. Every mystery box of the arena is outlined (chest boxes orange, sign boxes purple).
 * Type "cost <n>" to change the price of the next boxes, and "done" to finish.
 */
public class MysteryBoxSetupAction extends BaseAction
{
	private static final Color CHEST_BOX = Color.fromRGB(255, 140, 0);
	private static final Color SIGN_BOX = Color.fromRGB(190, 80, 255);

	private int cost;
	private boolean finished = false;

	public MysteryBoxSetupAction(Player player, Game game, int cost)
	{
		super(player, game);
		this.cost = cost;
		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----" + Colors.RED + "Mystery Boxes" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Click a chest or double chest with the wooden sword to make it a mystery box (" + cost + " points). Click a box again to remove it.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Outlines: " + Colors.GOLD + "orange" + Colors.GRAY + " = chest box, " + Colors.LIGHT_PURPLE + "purple" + Colors.GRAY + " = sign box.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Type: " + Colors.RED + "cost <n>" + Colors.GRAY + " | " + Colors.RED + "done");
		// One tick later: the command only stores this action after the constructor returns
		COMZombies.scheduleTask(1, this::outline);
	}

	@Override
	public void cancelAction()
	{
		finished = true;
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mystery box editing finished.");
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		Block clicked = event.getClickedBlock();
		if(clicked == null || finished)
			return;
		if(event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;
		if(event.getHand() != EquipmentSlot.HAND)
			return;
		event.setCancelled(true);

		if(game.getStatus() != Game.GameStatus.DISABLED && game.getStatus() != Game.GameStatus.WAITING)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Mystery boxes can only be changed while the arena is not in a game.");
			return;
		}

		RandomBox existing = game.boxManager.getBox(clicked.getLocation());
		if(existing != null)
		{
			if(existing.isChestBox())
				game.boxManager.removeBox(player, existing);
			else
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "That is a sign mystery box. Break the sign to remove it.");
			return;
		}
		if(clicked.getType() != Material.CHEST && clicked.getType() != Material.TRAPPED_CHEST)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Click a chest to make it a mystery box.");
			return;
		}
		BlockFace facing = clicked.getBlockData() instanceof Directional d ? d.getFacing() : BlockFace.NORTH;
		RandomBox box = new RandomBox(clicked.getLocation(), facing, game, game.boxManager.getNextBoxName(), cost, true);
		game.boxManager.addBox(box);
		boolean dbl = BlockUtils.doubleChestPartner(clicked) != null;
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Mystery box added (" + (dbl ? "double" : "single") + " chest, " + cost + " points). "
				+ game.boxManager.getNumBoxes() + " box(es) in this arena.");
		draw();
	}

	@Override
	public void onChatMessage(String message)
	{
		if(finished)
			return;
		String msg = message.trim().toLowerCase();
		if(msg.startsWith("cost"))
		{
			String num = msg.substring(4).trim();
			if(!num.matches("[0-9]{1,6}"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Use: cost <number>");
				return;
			}
			cost = Integer.parseInt(num);
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "New boxes will cost " + cost + " points. Boxes already added keep their price (click one twice to re-add it).");
		}
		else if(msg.equals("done"))
		{
			finished = true;
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Mystery boxes saved: " + game.boxManager.getNumBoxes() + " in this arena.");
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Type cost <n> or done.");
		}
	}

	private void outline()
	{
		if(finished || !player.isOnline() || COMZombies.getPlugin().activeActions.get(player) != this)
			return;
		draw();
		COMZombies.scheduleTask(10, this::outline);
	}

	private void draw()
	{
		for(RandomBox box : game.boxManager.getBoxes())
		{
			Color color = box.isChestBox() ? CHEST_BOX : SIGN_BOX;
			Highlight.box(player, box.getLocation(), color);
			if(box.isChestBox())
			{
				Block other = BlockUtils.doubleChestPartner(box.getLocation().getBlock());
				if(other != null)
					Highlight.box(player, other.getLocation(), color);
			}
		}
	}
}
