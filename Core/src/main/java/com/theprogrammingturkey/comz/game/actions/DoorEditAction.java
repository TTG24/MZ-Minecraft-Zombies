package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
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
 * Edits an existing door in place. Chat: "blocks" (click blocks with the wooden sword to add or remove them), "spawns"
 * (click spawn point frames to add or remove them from the room the door opens), "barriers" (click barriers to add or
 * remove them from that room: zombies only go for them once the door is open), "price <n>", "power on|off", "done"
 * (save) and "cancel" (undo everything). The door being edited is outlined in white.
 */
public class DoorEditAction extends BaseAction
{
	private enum Mode
	{
		BLOCKS, SPAWNS, BARRIERS
	}

	private final Door door;
	private final Map<Block, Material> savedBlocks;
	private final List<SpawnPoint> savedSpawns;
	private final List<String> savedBarriers;
	private final int savedPrice;
	private final boolean savedPower;
	private final boolean savedAuto;
	private final boolean savedLink;
	private Mode mode = Mode.BLOCKS;
	private boolean finished = false;

	public DoorEditAction(Player player, Game game, Door door)
	{
		super(player, game);
		this.door = door;
		// Every block has to be there to be edited, so the door is closed first
		door.closeDoor();
		savedBlocks = door.copyBlockMaterials();
		savedSpawns = new ArrayList<>(door.getSpawnsInRoomDoorLeadsTo());
		savedBarriers = door.getRoomBarriers();
		savedPrice = door.getCost();
		savedPower = door.requiresPower();
		savedAuto = door.opensWithPower();
		savedLink = door.opensWhenLinked();

		game.showSpawnLocations();
		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "------" + Colors.RED + "Edit Door " + door.doorID + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mode: BLOCKS (outlined in white). Click blocks with the wooden sword to add them to / remove them from the door.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Type: " + Colors.RED + "blocks" + Colors.GRAY + " | " + Colors.RED + "spawns" + Colors.GRAY + " (click spawn frames to add/remove) | " + Colors.RED + "barriers" + Colors.GRAY + " (click the room's barriers to add/remove) | " + Colors.RED + "price <n>" + Colors.GRAY + " (now " + savedPrice + ") | " + Colors.RED + "power on/off/auto" + Colors.GRAY + " (now " + (savedAuto ? "auto" : savedPower ? "on" : "off") + ") | " + Colors.RED + "link on/off" + Colors.GRAY + " (now " + (savedLink ? "on" : "off") + ") | " + Colors.RED + "done" + Colors.GRAY + " | " + Colors.RED + "cancel");
		// One tick later: the command only stores this action after the constructor returns, and the loop stops if it is not stored
		COMZombies.scheduleTask(1, this::outline);
	}

	@Override
	public void cancelAction()
	{
		if(finished)
			return;
		finished = true;
		door.restore(savedBlocks, savedSpawns, savedPrice, savedPower);
		door.setRoomBarriers(savedBarriers);
		door.setOpensWithPower(savedAuto);
		door.setOpensWhenLinked(savedLink);
		door.closeDoor();
		game.resetSpawnLocationBlocks();
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Door edit canceled, nothing was changed.");
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
			if(door.hasDoorLoc(clicked))
			{
				door.removeDoorBlock(clicked.getLocation());
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Block removed from the door! (" + door.getBlocks().size() + " left)");
			}
			else
			{
				door.addDoorBlock(clicked.getLocation());
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Block added to the door! (" + door.getBlocks().size() + ")");
			}
			draw();
		}
		else if(mode == Mode.BARRIERS)
		{
			Barrier barrier = game.barrierManager.getBarrier(clicked.getLocation());
			if(barrier == null)
				return;
			event.setCancelled(true);
			boolean added = door.toggleRoomBarrier(barrier);
			CommandUtil.sendMessageToPlayer(player, added
					? Colors.DARK_GREEN + "Barrier " + barrier.getID() + " added to the door's room (" + door.getRoomBarriers().size() + ")."
					: Colors.RED + "Barrier " + barrier.getID() + " removed from the door's room (" + door.getRoomBarriers().size() + " left).");
			draw();
		}
		else if(clicked.getType() == Material.END_PORTAL_FRAME)
		{
			SpawnPoint point = game.spawnManager.getSpawnPoint(clicked.getLocation());
			if(point == null)
				return;
			event.setCancelled(true);
			if(door.hasRoomSpawn(point))
			{
				door.removeSpawnPoint(point);
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Spawn point " + point.getID() + " removed from the door's room.");
			}
			else
			{
				door.addSpawnPoint(point);
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Spawn point " + point.getID() + " added to the door's room.");
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
		if(msg.equals("blocks"))
		{
			mode = Mode.BLOCKS;
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mode: BLOCKS. The door's " + door.getBlocks().size() + " block(s) are outlined in white. Click blocks to add/remove them.");
			draw();
		}
		else if(msg.equals("spawns"))
		{
			mode = Mode.SPAWNS;
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mode: SPAWNS. The room's spawns are green (" + door.getSpawnsInRoomDoorLeadsTo().size() + "), the others red. Click a spawn frame to add/remove it.");
			draw();
		}
		else if(msg.equals("barriers"))
		{
			mode = Mode.BARRIERS;
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mode: BARRIERS. The room's barriers are green (" + door.getRoomBarriers().size() + "), the others red. Click a barrier to add/remove it. Zombies only go for the room's barriers once this door is open.");
			draw();
		}
		else if(msg.startsWith("price"))
		{
			String num = msg.substring(5).trim();
			if(!num.matches("[0-9]{1,5}"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Use: price <number>");
				return;
			}
			door.setPrice(Integer.parseInt(num));
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Door price set to " + num + ".");
		}
		else if(msg.startsWith("link"))
		{
			String value = msg.substring(4).trim();
			if(!value.equals("on") && !value.equals("off"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Use: link on (opens once every teleporter is linked) or link off");
				return;
			}
			door.setOpensWhenLinked(value.equals("on"));
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + (value.equals("on") ? "This door now opens once every teleporter is linked to the mainframe." : "This door is bought normally again."));
		}
		else if(msg.startsWith("power"))
		{
			String value = msg.substring(5).trim();
			if(!value.equals("on") && !value.equals("off") && !value.equals("auto"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Use: power on, power off, or power auto (opens by itself when the power turns on)");
				return;
			}
			if(value.equals("auto"))
			{
				door.setPowerRequired(true);
				door.setOpensWithPower(true);
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "This door now opens by itself when the power turns on.");
				return;
			}
			door.setOpensWithPower(false);
			door.setPowerRequired(value.equals("on"));
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Power " + (value.equals("on") ? "is now required" : "is no longer required") + " to open this door.");
		}
		else if(msg.equals("done"))
		{
			int dropped = door.syncWithWorld();
			if(dropped > 0)
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + dropped + " selected spot(s) are air and will stay open (placed temp blocks removed).");
			if(!door.hasDoorBlocks())
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "A door needs at least 2 blocks!");
				return;
			}
			finished = true;
			door.closeDoor();
			game.resetSpawnLocationBlocks();
			GameManager.INSTANCE.saveAllGames();
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Door " + door.doorID + " saved: " + door.getBlocks().size() + " block(s), "
					+ door.getSpawnsInRoomDoorLeadsTo().size() + " room spawn(s), " + door.getRoomBarriers().size() + " room barrier(s), price " + door.getCost() + (door.opensWhenLinked() ? ", opens when the teleporters are linked." : door.opensWithPower() ? ", opens with the power." : door.requiresPower() ? ", needs power." : "."));
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Type blocks, spawns, barriers, price <n>, power on/off/auto, link on/off, done or cancel.");
		}
	}

	private void outline()
	{
		if(finished || !player.isOnline() || COMZombies.getPlugin().activeActions.get(player) != this)
			return;
		draw();
		COMZombies.scheduleTask(10, this::outline);
	}

	/**
	 * BLOCKS mode shows the door's blocks; SPAWNS mode shows every spawn point and BARRIERS mode every barrier (the room's
	 * ones green, the rest red).
	 */
	private void draw()
	{
		if(mode == Mode.BLOCKS)
		{
			Highlight.blocks(player, door.getBlocks(), Highlight.DOOR_EDITING);
		}
		else if(mode == Mode.BARRIERS)
		{
			Highlight.blocks(player, door.getBlocks(), Highlight.DOOR_EDITING);
			for(Barrier b : game.barrierManager.getBarriers())
				Highlight.blocks(player, b.getBlocks(), door.hasRoomBarrier(b) ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		}
		else
		{
			for(SpawnPoint sp : game.spawnManager.getPoints())
				Highlight.box(player, sp.getLocation(), door.hasRoomSpawn(sp) ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		}
	}
}
