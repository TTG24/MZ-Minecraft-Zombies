package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.game.Game;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Riese style teleporter regions of an arena: any number of link regions (teleporter pads) and one mainframe region.
 * Standing in an unlinked link region activates it; reaching the mainframe region in time links it. A linked region then
 * teleports the players standing in it to the mainframe (or to another link, see config linksToLinks).
 * Saved in the arena as "link_regions".
 */
public class LinkManager
{
	public static final String MAINFRAME = "mainframe";

	public static class LinkRegion
	{
		public final String name;
		public final int minX, minY, minZ, maxX, maxY, maxZ;
		public double ax, ay, az;
		public float yaw, pitch;
		/** Where this link teleports to: null = the mainframe, otherwise another link's name. */
		public String destination;

		public LinkRegion(String name, int x1, int y1, int z1, int x2, int y2, int z2)
		{
			this.name = name;
			minX = Math.min(x1, x2);
			minY = Math.min(y1, y2);
			minZ = Math.min(z1, z2);
			maxX = Math.max(x1, x2);
			maxY = Math.max(y1, y2);
			maxZ = Math.max(z1, z2);
			ax = (minX + maxX) / 2.0 + 0.5;
			ay = minY;
			az = (minZ + maxZ) / 2.0 + 0.5;
		}

		public boolean contains(Location loc)
		{
			int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}

		public void setArrival(Location loc)
		{
			ax = loc.getX();
			ay = loc.getY();
			az = loc.getZ();
			yaw = loc.getYaw();
			pitch = loc.getPitch();
		}

		public Location arrival(World world)
		{
			return new Location(world, ax, ay, az, yaw, pitch);
		}

		public int volume()
		{
			return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		}

		JsonObject save()
		{
			JsonObject json = new JsonObject();
			json.addProperty("name", name);
			json.addProperty("min_x", minX);
			json.addProperty("min_y", minY);
			json.addProperty("min_z", minZ);
			json.addProperty("max_x", maxX);
			json.addProperty("max_y", maxY);
			json.addProperty("max_z", maxZ);
			json.addProperty("arrival_x", ax);
			json.addProperty("arrival_y", ay);
			json.addProperty("arrival_z", az);
			json.addProperty("arrival_yaw", yaw);
			json.addProperty("arrival_pitch", pitch);
			if(destination != null)
				json.addProperty("destination", destination);
			return json;
		}

		static LinkRegion load(JsonObject json)
		{
			LinkRegion r = new LinkRegion(json.get("name").getAsString(), json.get("min_x").getAsInt(), json.get("min_y").getAsInt(),
					json.get("min_z").getAsInt(), json.get("max_x").getAsInt(), json.get("max_y").getAsInt(), json.get("max_z").getAsInt());
			if(json.has("arrival_x"))
			{
				r.ax = json.get("arrival_x").getAsDouble();
				r.ay = json.get("arrival_y").getAsDouble();
				r.az = json.get("arrival_z").getAsDouble();
				r.yaw = json.get("arrival_yaw").getAsFloat();
				r.pitch = json.get("arrival_pitch").getAsFloat();
			}
			if(json.has("destination"))
				r.destination = json.get("destination").getAsString();
			return r;
		}
	}

	private final Game game;
	private final Map<String, LinkRegion> links = new LinkedHashMap<>();
	private LinkRegion mainframe;

	public LinkManager(Game game)
	{
		this.game = game;
	}

	public void load(JsonObject json)
	{
		links.clear();
		mainframe = null;
		if(json.has("mainframe") && json.get("mainframe").isJsonObject())
			mainframe = LinkRegion.load(json.getAsJsonObject("mainframe"));
		if(json.has("links"))
			for(JsonElement e : json.getAsJsonArray("links"))
				if(e.isJsonObject())
				{
					LinkRegion r = LinkRegion.load(e.getAsJsonObject());
					links.put(r.name.toLowerCase(), r);
				}
	}

	public JsonObject save()
	{
		JsonObject json = new JsonObject();
		if(mainframe != null)
			json.add("mainframe", mainframe.save());
		JsonArray arr = new JsonArray();
		for(LinkRegion r : links.values())
			arr.add(r.save());
		json.add("links", arr);
		return json;
	}

	public boolean isEmpty()
	{
		return links.isEmpty() && mainframe == null;
	}

	public List<LinkRegion> getLinks()
	{
		return new ArrayList<>(links.values());
	}

	public LinkRegion getLink(String name)
	{
		return name == null ? null : links.get(name.toLowerCase());
	}

	public LinkRegion getMainframe()
	{
		return mainframe;
	}

	public void setLink(LinkRegion region)
	{
		links.put(region.name.toLowerCase(), region);
	}

	public void setMainframe(LinkRegion region)
	{
		mainframe = region;
	}

	public boolean removeLink(String name)
	{
		boolean removed = links.remove(name.toLowerCase()) != null;
		// Links that went to the removed one go to the mainframe again
		for(LinkRegion r : links.values())
			if(name.equalsIgnoreCase(r.destination))
				r.destination = null;
		return removed;
	}

	public void removeMainframe()
	{
		mainframe = null;
	}

	/** The link region the location is in, or null. */
	public LinkRegion linkAt(Location loc)
	{
		if(loc.getWorld() == null || !loc.getWorld().equals(game.getWorld()))
			return null;
		for(LinkRegion r : links.values())
			if(r.contains(loc))
				return r;
		return null;
	}

	public boolean inMainframe(Location loc)
	{
		return mainframe != null && loc.getWorld() != null && loc.getWorld().equals(game.getWorld()) && mainframe.contains(loc);
	}
}
