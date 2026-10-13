package com.theprogrammingturkey.comz.game.signs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Location;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.COMZConfig;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;

/**
 * Remembers every game sign by location, along with its type and data (prices, gun names, etc), so signs are
 * recognised by where they are rather than by their text. Saved to gamesigns.json.
 */
public class SignRegistry
{
	public static final SignRegistry INSTANCE = new SignRegistry();

	private final Map<Location, Entry> signs = new HashMap<>();
	private final java.util.Set<org.bukkit.World> instanceWorlds = new java.util.HashSet<>();

	public void copyToInstance(com.theprogrammingturkey.comz.game.Game template, com.theprogrammingturkey.comz.game.Game match)
	{
		instanceWorlds.add(match.getWorld());
		for(Map.Entry<Location, Entry> entry : getAll().entrySet())
		{
			if(!template.arena.containsCopySelection(entry.getKey())) continue;
			Location loc = entry.getKey().clone();
			loc.setWorld(match.getWorld());
			Map<String, String> data = new HashMap<>(entry.getValue().data);
			if(template.getName().equalsIgnoreCase(data.get("arena")))
				data.put("arena", match.getName());
			signs.put(loc, new Entry(entry.getValue().type, data));
		}
	}

	public void removeInstanceWorld(org.bukkit.World world)
	{
		signs.keySet().removeIf(loc -> world.equals(loc.getWorld()));
		instanceWorlds.remove(world);
	}
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
			if(instanceWorlds.contains(sign.getKey().getWorld())) continue;
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

	/**
	 * @return all locations of signs with the given type
	 */
	public List<Location> findByType(String type)
	{
		List<Location> result = new ArrayList<>();
		for(Map.Entry<Location, Entry> entry : signs.entrySet())
			if(entry.getValue().type.equals(type))
				result.add(entry.getKey());
		return result;
	}

	/**
	 * @return a snapshot of every registered sign, location to its entry
	 */
	public Map<Location, Entry> getAll()
	{
		return new HashMap<>(signs);
	}

	/**
	 * Re-renders the text of every registered sign from its logic and current data. Used after a reload so
	 * already-placed signs pick up edited config (e.g. sign text).
	 *
	 * @return the number of signs updated
	 */
	public int refreshAllText()
	{
		int updated = 0;
		for(Map.Entry<Location, Entry> entry : signs.entrySet())
		{
			IGameSign logic = GameSigns.fromType(entry.getValue().type);
			if(logic == null)
				continue;
			String[] text = logic.getText(entry.getValue().data);
			if(text == null)
				continue;
			org.bukkit.block.Block block = entry.getKey().getBlock();
			if(block.getState() instanceof org.bukkit.block.Sign sign)
			{
				SignText.apply(sign, text);
				updated++;
			}
		}
		return updated;
	}

	private static Location toKey(Location loc)
	{
		if(loc.getWorld() == null)
			COMZombies.log.log(Level.WARNING, "Game sign location has no world: " + loc);
		return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
	}
}
