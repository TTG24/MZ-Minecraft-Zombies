package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.kits.Kit;
import com.theprogrammingturkey.comz.kits.KitManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class KitSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "kit";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		// getKit returns a placeholder kit rather than null for unknown names
		Kit kit = KitManager.getKit(lines[2]);
		if(kit == null || !kit.getName().equalsIgnoreCase(lines[2]))
			throw new SignParseException(ChatColor.RED + "" + ChatColor.BOLD + "Kit name is", ChatColor.RED + "" + ChatColor.BOLD + "not a valid", ChatColor.RED + "" + ChatColor.BOLD + "kit!");

		Map<String, String> data = new HashMap<>();
		data.put("kit", kit.getName());
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		Kit kit = KitManager.getKit(data.get("kit"));
		if(COMZPermission.KIT.hasPerm(player, kit.getName()))
		{
			KitManager.addPlayersSelectedKit(player, kit);
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "You have selected the " + kit.getName() + " kit!");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have permission to use that kit!");
		}
	}

	@Override
	public String[] getText(Map<String, String> data)
	{
		return SignText.render(getType(), data);
	}

	@Override
	public boolean requiresGame()
	{
		return false;
	}
}
