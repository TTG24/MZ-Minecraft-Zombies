package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.signs.JoinSign;
import com.theprogrammingturkey.comz.game.signs.SignRegistry;
import com.theprogrammingturkey.comz.game.signs.SignText;
import com.theprogrammingturkey.comz.util.BlockUtils;
import org.bukkit.Location;
import org.bukkit.block.Sign;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class SignManager
{
	public Set<Location> gameSigns = new HashSet<>();

	private final Game game;

	public SignManager(Game game)
	{
		this.game = game;

		JsonElement jsonElement = ConfigManager.getConfig(COMZConfig.SIGNS).getJson();
		if(jsonElement.isJsonNull())
		{
			COMZombies.log.log(Level.SEVERE, "Failed to load in the signs for the arena: " + game.getName());
			return;
		}
		JsonObject jsonObject = jsonElement.getAsJsonObject();
		if(jsonObject.has(game.getName()))
			load(jsonObject.getAsJsonArray(game.getName()));
	}

	private void load(JsonArray signsJson)
	{
		for(JsonElement signElem : signsJson)
		{
			if(!signElem.isJsonObject())
				continue;
			JsonObject signJson = signElem.getAsJsonObject();
			Location loc = CustomConfig.getLocation(signJson, "");

			if(loc == null)
			{
				COMZombies.log.log(Level.SEVERE, "Could not load the sign with json: " + signJson.toString());
				continue;
			}
			gameSigns.add(loc);
		}
		enable();
	}

	private void save()
	{
		CustomConfig config = ConfigManager.getConfig(COMZConfig.SIGNS);
		JsonElement jsonElement = config.getJson();
		if(jsonElement.isJsonNull())
		{
			COMZombies.log.log(Level.SEVERE, "Failed to save in the signs for the arena: " + game.getName());
			return;
		}
		JsonObject jsonObject = jsonElement.getAsJsonObject();
		// Rebuilt each time. Adding to the saved list duplicated every sign on each save.
		JsonArray signsArray = new JsonArray();
		jsonObject.add(game.getName(), signsArray);

		for(Location loc : gameSigns)
			signsArray.add(CustomConfig.locationToJson(loc));

		config.saveConfig(jsonObject);
	}

	public void updateGame()
	{
		COMZombies.scheduleTask(20, () ->
		{
			for(Location loc : gameSigns)
			{
				if(!BlockUtils.isSign(loc.getBlock()))
					continue;
				SignText.apply((Sign) loc.getBlock().getState(), JoinSign.getText(game));
			}
		});
	}

	public void enable()
	{
		updateGame();
	}

	public void addSign(Location loc)
	{
		gameSigns.add(loc);
		save();
	}

	public void removeSign(Location loc)
	{
		gameSigns.remove(loc);
		save();
	}

	public boolean isSign(Location loc)
	{
		return gameSigns.contains(loc);
	}

	public void removeAllSigns()
	{
		for(Location loc : gameSigns)
		{
			if(!BlockUtils.isSign(loc.getBlock()))
				continue;
			Sign sign = (Sign) loc.getBlock().getState();
			SignRegistry.INSTANCE.remove(loc);
			sign.setLine(0, "");
			sign.setLine(1, "");
			sign.setLine(2, "");
			sign.setLine(3, "");
			sign.update();
		}
		gameSigns.clear();
		save();
	}
}
