package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

public class PerksCommand extends SubCommand
{
	public PerksCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.PERKS.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view the perk info");
			return true;
		}

		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "The following perks are available:\n");
		for(PerkType perk : PerkType.values())
			player.sendMessage(Colors.GRAY + "- " + perk.name());

		return true;
	}
}