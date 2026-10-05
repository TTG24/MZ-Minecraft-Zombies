package com.theprogrammingturkey.comz.game.signs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Remembers every game sign by location, along with its type and data (prices, gun names, etc), so signs are
 * recognised by where they are rather than by their text. Saved to gamesigns.json.
 */
public class SignRegistry
{
	public static final SignRegistry INSTANCE = new SignRegistry();

	private final Map<Location, Entry> signs = new HashMap<>();
	// Signs in worlds that aren't loaded. Kept as-is so saving doesn't drop them.
	private final List<JsonObject> unloadedSigns = new ArrayList<>();

	public static class Entry
	{
		public final String type;
		public final Map<String, String> data;

		public Entry(String type, Map<String, String> data)
		{
			this.type = type;
			this.data = data;
		}
	}

	public void load()
	{
		signs.clear();
		unloadedSigns.clear();

		JsonElement json = ConfigManager.getConfig(COMZConfig.GAME_SIGNS).getJson();
		if(!json.isJsonObject() || !json.getAsJsonObject().has("signs"))
			return;

		for(JsonElement signElem : json.getAsJsonObject().getAsJsonArray("signs"))
		{
			if(!signElem.isJsonObject())
				continue;
			JsonObject signJson = signElem.getAsJsonObject();
			Location loc = CustomConfig.getLocation(signJson, "location");
			if(loc == null)
			{
				unloadedSigns.add(signJson);
				continue;
			}

			Map<String, String> data = new HashMap<>();
			if(signJson.has("data"))
				for(Map.Entry<String, JsonElement> value : signJson.getAsJsonObject("data").entrySet())
					data.put(value.getKey(), value.getValue().getAsString());

			signs.put(loc, new Entry(CustomConfig.getString(signJson, "type", ""), data));
		}
	}

	private void save()
	{
		JsonArray signsJson = new JsonArray();
		for(Map.Entry<Location, Entry> sign : signs.entrySet())
		{
			JsonObject signJson = new JsonObject();
			signJson.add("location", CustomConfig.locationToJson(sign.getKey()));
			signJson.addProperty("type", sign.getValue().type);
			JsonObject dataJson = new JsonObject();
			sign.getValue().data.forEach(dataJson::addProperty);
			signJson.add("data", dataJson);
			signsJson.add(signJson);
		}
		unloadedSigns.forEach(signsJson::add);

		JsonObject json = new JsonObject();
		json.add("signs", signsJson);
		ConfigManager.getConfig(COMZConfig.GAME_SIGNS).saveConfig(json);
	}

	public Entry get(Location loc)
	{
		return signs.get(toKey(loc));
	}

	public void register(Location loc, String type, Map<String, String> data)
	{
		signs.put(toKey(loc), new Entry(type, data));
		save();
	}

	public void remove(Location loc)
	{
		if(signs.remove(toKey(loc)) != null)
			save();
	}

	private static Location toKey(Location loc)
	{
		if(loc.getWorld() == null)
			COMZombies.log.log(Level.WARNING, "Game sign location has no world: " + loc);
		return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
	}
}
