package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.listeners.DiveListener;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

/**
 * Toggles the deep crouch stance on the invoking player: arms the client's animated
 * crouch pose and caps movement (the dive's slower posture is unaffected — the tick
 * task keeps whichever held pose is stricter while both are active).
 *
 * <pre>
 *   /zombies crouch       — enter the crouch stance (toggle off if already in it)
 *   /zombies crouch stand — explicitly stand up (same as toggling off)
 * </pre>
 *
 * The command works outside a running arena so it can be tested in a build area;
 * the slowdown and pose are the same code paths used during a match.
 */
public class CrouchCommand extends SubCommand
{
	public final COMZPermission permission = COMZPermission.CROUCH;

	public CrouchCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.CROUCH.hasPerm(player))
		{
			CommandUtil.noPermission(player, "use the crouch stance");
			return true;
		}
		DiveListener dive = DiveListener.INSTANCE;
		if(dive == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Crouch system is not available.");
			return true;
		}
		java.util.UUID id = player.getUniqueId();
		boolean off = (args.length > 1 && args[1].equalsIgnoreCase("stand"))
			|| dive.isCrouched(id);
		if(off)
		{
			dive.exitCrouch(player);
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Crouch ended.");
		}
		else
		{
			dive.enterCrouch(player);
			CommandUtil.sendMessageToPlayer(player,
				Colors.DARK_GREEN + "Crouching. Move to strafe; "
					+ Colors.GRAY + "/zombies crouch"
					+ Colors.DARK_GREEN + " to stand.");
		}
		return true;
	}

	@Override
	public java.util.List<String> onTabComplete(Player player, String[] args)
	{
		if(args.length == 2)
		{
			return java.util.Arrays.asList("stand");
		}
		return null;
	}
}
