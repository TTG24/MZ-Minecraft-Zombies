package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class DoorSetupAction extends BaseAction
{
	private final Door door;

	private int state = 0;

	public DoorSetupAction(Player player, Game game, Door door)
	{
		super(player, game);
		this.door = door;

		game.showSpawnLocations();
		COMZombies.scheduleTask(1, this::outline);

		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-------" + Colors.RED + "Door Setup" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-------");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Select each block individually to be the door using the wooden sword.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Once you have this complete, type done, go into the room the door opens to and click on any ender portal frame (spawn point) that is in there with the sword (click it again to take it back off).");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Type done, then click the barriers (windows) of that room the same way. Zombies only go for them once this door is open.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Then type done and enter the door price in chat (POWER:PRICE if power is required). No sign is needed.");
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Type cancel to cancel this operation.");
	}

	public void cancelAction()
	{
		game.resetSpawnLocationBlocks();
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Door creation operation has been canceled!");
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		Block clickedBlock = event.getClickedBlock();
		if(clickedBlock != null && clickedBlock.getType().equals(Material.END_PORTAL_FRAME))
		{
			if(state != 1)
				return;

			SpawnPoint point = game.spawnManager.getSpawnPoint(clickedBlock.getLocation());

			if(point == null)
				return;

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
			event.setCancelled(true);
		}

		if(state == 2 && clickedBlock != null && (event.getAction().equals(Action.LEFT_CLICK_BLOCK) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK)))
		{
			Barrier barrier = game.barrierManager.getBarrier(clickedBlock.getLocation());
			if(barrier == null)
				return;
			event.setCancelled(true);
			boolean added = door.toggleRoomBarrier(barrier);
			CommandUtil.sendMessageToPlayer(player, added
					? Colors.DARK_GREEN + "Barrier " + barrier.getID() + " added to the door's room (" + door.getRoomBarriers().size() + ")."
					: Colors.RED + "Barrier " + barrier.getID() + " removed from the door's room (" + door.getRoomBarriers().size() + " left).");
			return;
		}

		if(state == 0 && clickedBlock != null && (event.getAction().equals(Action.LEFT_CLICK_BLOCK) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK)))
		{
			if(!door.hasDoorLoc(clickedBlock))
			{
				door.addDoorBlock(clickedBlock.getLocation());
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Block added!");
			}
			else
			{
				door.removeDoorBlock(clickedBlock.getLocation());
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Block removed!");
			}
			event.setCancelled(true);
		}
	}

	@Override
	public void onChatMessage(String message)
	{
		if(message.equalsIgnoreCase("done"))
		{
			if(state == 0)
			{
				int dropped = door.syncWithWorld();
				if(dropped > 0)
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + dropped + " selected spot(s) are air and will stay open (placed temp blocks removed).");
				if(door.hasDoorBlocks())
				{
					CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Door points for door set!");
					CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Now select any spawn points in the room the door leads to.");
					state++;
				}
			}
			else if(state == 1)
			{
				if(door.getSpawnsInRoomDoorLeadsTo().isEmpty())
					door.addSpawnPoint(null);

				COMZombies.scheduleTask(1, game::resetSpawnLocationBlocks);

				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Spawn points for door set!");
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Now click the barriers (windows) of the room the door leads to (green = the room's, red = others), then type done. None? Just type done.");
				state++;
			}
			else if(state == 2)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + door.getRoomBarriers().size() + " barrier(s) set for the door's room!");
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Now type in a price for the door:");
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "  " + Colors.RED + "1500" + Colors.GRAY + " = normal door, " + Colors.RED + "POWER:1500" + Colors.GRAY + " = power door (bought once the power is on), " + Colors.RED + "POWER:AUTO" + Colors.GRAY + " = power door that opens by itself when the power turns on, " + Colors.RED + "LINK" + Colors.GRAY + " = opens once every teleporter is linked to the mainframe.");
				state++;
			}
		}
		else if(state == 3)
		{
			if(!message.matches("[0-9]{1,5}") && !message.matches("(?i)POWER:[0-9]{1,5}") && !message.equalsIgnoreCase("POWER:AUTO") && !message.equalsIgnoreCase("LINK"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + message + " is not valid! Use a number, POWER:number, POWER:AUTO or LINK.");
				return;
			}

			if(message.equalsIgnoreCase("LINK"))
			{
				door.setOpensWhenLinked(true);
				message = "0";
			}
			else if(message.equalsIgnoreCase("POWER:AUTO"))
			{
				door.setPowerRequired(true);
				door.setOpensWithPower(true);
				message = "0";
			}
			else if(message.toUpperCase().startsWith("POWER:"))
			{
				door.setPowerRequired(true);
				message = message.substring(6);
			}

			int price = Integer.parseInt(message);

			door.setPrice(price);
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Door setup complete!");
			door.closeDoor();
			game.doorManager.addDoor(door);
			GameManager.INSTANCE.saveAllGames();
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
	}

	private void outline()
	{
		if(!player.isOnline() || COMZombies.getPlugin().activeActions.get(player) != this)
			return;
		Highlight.blocks(player, door.getBlocks(), Highlight.DOOR_EDITING);
		if(state == 1)
			for(SpawnPoint sp : game.spawnManager.getPoints())
				Highlight.box(player, sp.getLocation(), door.hasRoomSpawn(sp) ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		if(state == 2)
			for(Barrier b : game.barrierManager.getBarriers())
				Highlight.blocks(player, b.getBlocks(), door.hasRoomBarrier(b) ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		COMZombies.scheduleTask(10, this::outline);
	}
}
