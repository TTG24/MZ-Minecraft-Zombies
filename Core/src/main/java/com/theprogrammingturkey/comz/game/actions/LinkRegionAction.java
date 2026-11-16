package com.theprogrammingturkey.comz.game.actions;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.LinkManager;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Selects the box of a link region (/z linkadd) or of the mainframe (/z mainlink): left-click a block with the wooden sword
 * for the first corner, right-click for the second, or type "we" to use the WorldEdit selection. "done" saves it, and the
 * place the player stands on is where teleported players arrive (they arrive in the middle when standing outside it).
 */
public class LinkRegionAction extends BaseAction
{
	public static final Color LINK = Color.fromRGB(0, 220, 255);
	public static final Color MAINFRAME = Color.fromRGB(255, 60, 200);

	private final String name;
	private final boolean mainframe;
	private Location pos1;
	private Location pos2;
	private boolean finished = false;

	public LinkRegionAction(Player player, Game game, String name, boolean mainframe)
	{
		super(player, game);
		this.name = name;
		this.mainframe = mainframe;
		if(!player.getInventory().contains(Material.WOODEN_SWORD))
			player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

		String what = mainframe ? "the mainframe" : "link " + name;
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----" + Colors.RED + (mainframe ? "Mainframe region" : "Link region " + name) + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "-----");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Select the area of " + what + ": " + Colors.RED + "left-click" + Colors.GRAY + " a block with the wooden sword for corner 1, "
				+ Colors.RED + "right-click" + Colors.GRAY + " for corner 2, or type " + Colors.RED + "we" + Colors.GRAY + " to use your WorldEdit selection.");
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Then stand where teleported players should arrive and type " + Colors.RED + "done" + Colors.GRAY + ". " + Colors.RED + "cancel" + Colors.GRAY + " to stop.");
		COMZombies.scheduleTask(1, this::outline);
	}

	@Override
	public void cancelAction()
	{
		finished = true;
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Region selection canceled.");
	}

	@Override
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		Block clicked = event.getClickedBlock();
		if(clicked == null || finished || event.getHand() != EquipmentSlot.HAND)
			return;
		if(event.getAction() == Action.LEFT_CLICK_BLOCK)
		{
			pos1 = clicked.getLocation();
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Corner 1 set (" + pos1.getBlockX() + ", " + pos1.getBlockY() + ", " + pos1.getBlockZ() + ")" + size());
		}
		else if(event.getAction() == Action.RIGHT_CLICK_BLOCK)
		{
			pos2 = clicked.getLocation();
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Corner 2 set (" + pos2.getBlockX() + ", " + pos2.getBlockY() + ", " + pos2.getBlockZ() + ")" + size());
		}
		else
			return;
		event.setCancelled(true);
	}

	private String size()
	{
		if(pos1 == null || pos2 == null)
			return ".";
		return Colors.GRAY + " - " + (Math.abs(pos1.getBlockX() - pos2.getBlockX()) + 1) + "x" + (Math.abs(pos1.getBlockY() - pos2.getBlockY()) + 1)
				+ "x" + (Math.abs(pos1.getBlockZ() - pos2.getBlockZ()) + 1) + ".";
	}

	@Override
	public void onChatMessage(String message)
	{
		if(finished)
			return;
		String msg = message.trim().toLowerCase();
		if(msg.equals("we") || msg.equals("worldedit"))
		{
			if(!fromWorldEdit())
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "No WorldEdit selection found. Select both corners with //wand (or //pos1 //pos2) first.");
		}
		else if(msg.equals("done"))
		{
			if(pos1 == null || pos2 == null)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Set both corners first!");
				return;
			}
			LinkManager.LinkRegion region = new LinkManager.LinkRegion(name, pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
					pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
			Location here = player.getLocation();
			boolean inside = region.contains(here) || region.contains(here.clone().add(0, -0.5, 0));
			if(inside)
				region.setArrival(here);
			else
				region.ay = region.minY + 1;
			if(mainframe)
				game.linkManager.setMainframe(region);
			else
			{
				LinkManager.LinkRegion old = game.linkManager.getLink(name);
				if(old != null)
					region.destination = old.destination;
				game.linkManager.setLink(region);
			}
			GameManager.INSTANCE.saveAllGames();
			finished = true;
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + (mainframe ? "Mainframe" : "Link " + name) + " saved" + size()
					+ (inside ? Colors.GRAY + " Players arrive where you are standing." : Colors.YELLOW + " You were outside it, so players arrive in its middle."));
			COMZombies.scheduleTask(1, () -> COMZombies.getPlugin().activeActions.remove(player));
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Click the corners, or type we, done or cancel.");
		}
	}

	private boolean fromWorldEdit()
	{
		try
		{
			com.sk89q.worldedit.entity.Player wePlayer = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player);
			com.sk89q.worldedit.LocalSession session = com.sk89q.worldedit.WorldEdit.getInstance().getSessionManager().get(wePlayer);
			com.sk89q.worldedit.regions.Region region = session.getSelection(wePlayer.getWorld());
			com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
			com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
			pos1 = new Location(player.getWorld(), min.x(), min.y(), min.z());
			pos2 = new Location(player.getWorld(), max.x(), max.y(), max.z());
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Using your WorldEdit selection" + size());
			return true;
		}
		catch(Exception | LinkageError e)
		{
			return false;
		}
	}

	private void outline()
	{
		if(finished || !player.isOnline() || COMZombies.getPlugin().activeActions.get(player) != this)
			return;
		Color color = mainframe ? MAINFRAME : LINK;
		if(pos1 != null && pos2 != null)
			drawBox(player, pos1, pos2, color, 0);
		else if(pos1 != null)
			Highlight.box(player, pos1, color);
		else if(pos2 != null)
			Highlight.box(player, pos2, color);
		COMZombies.scheduleTask(10, this::outline);
	}

	/** Outlines the edge blocks of a box (not its inside). */
	public static void drawBox(Player player, Location a, Location b, Color color, int layer)
	{
		int x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX());
		int y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY());
		int z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ());
		drawBox(player, a.getWorld(), x1, y1, z1, x2, y2, z2, color, layer);
	}

	public static void drawBox(Player player, org.bukkit.World world, int x1, int y1, int z1, int x2, int y2, int z2, Color color, int layer)
	{
		for(int x = x1; x <= x2; x++)
			for(int y = y1; y <= y2; y++)
				for(int z = z1; z <= z2; z++)
				{
					int edges = (x == x1 || x == x2 ? 1 : 0) + (y == y1 || y == y2 ? 1 : 0) + (z == z1 || z == z2 ? 1 : 0);
					if(edges >= 2)
						Highlight.box(player, new Location(world, x, y, z), color, layer);
				}
	}
}
