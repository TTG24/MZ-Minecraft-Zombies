package com.theprogrammingturkey.comz.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /zombies guns: a short list of every gun in guns.json, one line per class, with the Pack-a-Punched name after each gun.
 */
public class GunsCommand extends SubCommand
{
	public GunsCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.LIST_GUNS.hasPerm(player))
		{
			CommandUtil.noPermission(player, "see the list of guns");
			return true;
		}
		JsonElement json = ConfigManager.getConfig(COMZConfig.GUNS).getJson();
		if(json == null || !json.isJsonObject())
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Could not read guns.json.");
			return true;
		}
		int total = 0;
		List<String> lines = new ArrayList<>();
		for(Map.Entry<String, JsonElement> group : json.getAsJsonObject().entrySet())
		{
			if(!group.getValue().isJsonObject() || !group.getValue().getAsJsonObject().has("guns"))
				continue;
			List<String> names = new ArrayList<>();
			for(JsonElement gunElem : group.getValue().getAsJsonObject().getAsJsonArray("guns"))
			{
				if(!gunElem.isJsonObject())
					continue;
				JsonObject gun = gunElem.getAsJsonObject();
				String name = Colors.WHITE + CustomConfig.getString(gun, "name", "Unnamed");
				if(gun.has("pack_a_punch_gun") && gun.get("pack_a_punch_gun").isJsonObject())
					name += Colors.DARK_PURPLE + " (" + CustomConfig.getString(gun.getAsJsonObject("pack_a_punch_gun"), "name", "?") + ")";
				names.add(name);
				total++;
			}
			if(!names.isEmpty())
				lines.add(Colors.GOLD + group.getKey() + Colors.GRAY + " (" + names.size() + "): " + String.join(Colors.GRAY + ", ", names));
		}
		CommandUtil.sendMessageToPlayer(player, Colors.RED + "Guns" + Colors.GRAY + " - " + total + " in guns.json"
				+ Colors.DARK_PURPLE + " (Pack-a-Punched name)");
		if(lines.isEmpty())
			player.sendMessage(Colors.GRAY + " No guns found.");
		for(String line : lines)
			player.sendMessage(line);
		return true;
	}
}
