package com.theprogrammingturkey.comz.game.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.signs.SignText;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.BlockUtils;

public class Door
{
	public String doorID;
	private final Game game;
	private int price = 0;
	private final Map<Block, Material> blocks = new HashMap<>();
	/** Each block's full state (BlockUtils.dataOf), so it is put back exactly as built. Missing: the material alone. */
	private final Map<Block, String> blockData = new HashMap<>();
	private final List<Location> signsLocations = new ArrayList<>();
	private List<SpawnPoint> spawnsInRoomDoorLeadsTo = new ArrayList<>();
	/** Ids of the barriers into the room this door opens: zombies only go for them once the door is open. */
	private List<String> roomBarriers = new ArrayList<>();
	private boolean isOpened = false;
	private boolean powerRequired = false;
	private boolean autoOpen = false;
	private boolean openOnLink = false;

	public Door(Game game, String id, boolean powerRequired)
	{
		this.game = game;
		doorID = id;
		this.powerRequired = powerRequired;
	}

	public boolean canOpen(int moneyHas)
	{
		return price <= moneyHas;
	}

	public void setPrice(int cost)
	{
		price = cost;
	}

	public void loadAll(JsonObject doorJson)
	{
		if(doorJson.has("blocks"))
			loadBlocks(doorJson.get("blocks").getAsJsonArray());
		if(doorJson.has("signs"))
			loadSigns(doorJson.get("signs").getAsJsonArray());
		if(doorJson.has("spawns"))
			loadDoor(doorJson.get("spawns").getAsJsonArray());
		if(doorJson.has("price"))
			price = doorJson.get("price").getAsInt();
		roomBarriers = new ArrayList<>();
		if(doorJson.has("barriers"))
			for(JsonElement e : doorJson.get("barriers").getAsJsonArray())
				roomBarriers.add(e.getAsString());
		autoOpen = doorJson.has("autoOpen") && doorJson.get("autoOpen").getAsBoolean();
		openOnLink = doorJson.has("openOnLink") && doorJson.get("openOnLink").getAsBoolean();
	}

	public JsonObject save()
	{
		JsonObject saveJson = new JsonObject();
		saveJson.addProperty("id", doorID);
		saveJson.addProperty("powerRequired", powerRequired);
		saveJson.addProperty("price", price);
		saveJson.addProperty("autoOpen", autoOpen);
		saveJson.addProperty("openOnLink", openOnLink);

		JsonArray blocksJson = new JsonArray();
		saveJson.add("blocks", blocksJson);
		for(Map.Entry<Block, Material> block : blocks.entrySet())
		{
			JsonObject blockJson = CustomConfig.locationToJsonNoWorld(block.getKey().getLocation());
			blockJson.addProperty("material", block.getValue().getKey().getKey());
			String data = getBlockData(block.getKey());
			if(data != null)
				blockJson.addProperty("data", data);
			blocksJson.add(blockJson);
		}

		JsonArray signsJson = new JsonArray();
		saveJson.add("signs", signsJson);
		for(Location loc : signsLocations)
			signsJson.add(CustomConfig.locationToJsonNoWorld(loc));


		JsonArray spawnsJson = new JsonArray();
		saveJson.add("spawns", spawnsJson);
		for(SpawnPoint spawnPoint : spawnsInRoomDoorLeadsTo)
			if(spawnPoint != null)
				spawnsJson.add(spawnPoint.getID());

		JsonArray barriersJson = new JsonArray();
		saveJson.add("barriers", barriersJson);
		for(String id : roomBarriers)
			barriersJson.add(id);

		return saveJson;
	}

	public int getCost()
	{
		return price;
	}

	private void loadDoor(JsonArray spawnsJson)
	{
		List<SpawnPoint> points = new ArrayList<>();
		for(JsonElement spawnElem : spawnsJson)
		{
			SpawnPoint point = game.spawnManager.getSpawnPoint(spawnElem.getAsString());
			if(point == null)
				continue;
			points.add(point);
		}
		spawnsInRoomDoorLeadsTo = points;
	}

	public void playerDoorOpenSound()
	{
		World world = game.getWorld();
		if(!blocks.isEmpty())
		{
			Block b = blocks.keySet().iterator().next();
			world.playSound(b.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1L, 1L);
		}
	}

	private void loadSigns(JsonArray signs)
	{
		for(JsonElement signElem : signs)
		{
			if(!signElem.isJsonObject())
				continue;
			JsonObject signJson = signElem.getAsJsonObject();
			Location loc = CustomConfig.getLocationWithWorld(signJson, "", game.getWorld());
			if(loc != null)
			{
				Block block = loc.getBlock();
				if(BlockUtils.isSign(block.getType()))
				{
					Sign sign = (Sign) block.getState();
					// Older saves only stored the price on the sign, usually on line 4 but sometimes line 3
					org.bukkit.block.sign.SignSide front = sign.getSide(Side.FRONT);
					String costLine = Colors.plain(front.line(3)).trim();
					if(!costLine.matches("[0-9]{1,9}"))
						costLine = Colors.plain(front.line(2)).trim();
					price = costLine.matches("[0-9]{1,9}") ? Integer.parseInt(costLine) : 750;
					this.signsLocations.add(loc);
				}
			}
			else
			{
				COMZombies.log.log(Level.WARNING, () -> "Failed to load in location for door sign! Json: " + signJson.toString());
			}

		}
	}


	public void addSpawnPoint(SpawnPoint point)
	{
		if(point != null && !hasRoomSpawn(point))
			spawnsInRoomDoorLeadsTo.add(point);
	}

	/** Is this spawn (by id) one of the room's? */
	public boolean hasRoomSpawn(SpawnPoint point)
	{
		return point != null && spawnsInRoomDoorLeadsTo.stream().anyMatch(p -> p != null && p.getID().equalsIgnoreCase(point.getID()));
	}

	/** Takes a spawn (every copy of it, by id) out of the room. */
	public void removeSpawnPoint(SpawnPoint point)
	{
		if(point != null)
			spawnsInRoomDoorLeadsTo.removeIf(p -> p == null || p.getID().equalsIgnoreCase(point.getID()));
	}

	/** Is this barrier one of the room's (the room this door opens)? */
	public boolean hasRoomBarrier(Barrier barrier)
	{
		return barrier != null && roomBarriers.contains(barrier.getID());
	}

	/** Adds the barrier to the room, or takes it back out. @return true if it is in the room now */
	public boolean toggleRoomBarrier(Barrier barrier)
	{
		if(roomBarriers.remove(barrier.getID()))
			return false;
		roomBarriers.add(barrier.getID());
		return true;
	}

	/** The room's barrier ids (a copy). */
	public List<String> getRoomBarriers()
	{
		return new ArrayList<>(roomBarriers);
	}

	public void setRoomBarriers(List<String> ids)
	{
		roomBarriers = new ArrayList<>(ids);
	}

	public boolean hasDoorBlocks()
	{
		return blocks.size() >= 2;
	}

	public void openDoor()
	{
		int interval = 1;
		for(final Block block : blocks.keySet())
		{
			if(block.getType().equals(Material.AIR))
				continue;

			COMZombies.scheduleTask(interval, () -> BlockUtils.setBlockToAir(block));
			interval += 1;
		}
		isOpened = true;
	}

	public boolean isOpened()
	{
		return isOpened;
	}

	public void closeDoor()
	{
		// Only blocks that are missing or wrong are placed (an already-closed door is left as built), each in its saved state
		BlockUtils.restoreBlocks(blocks, blockData);

		for(Location loc : signsLocations)
		{
			if(!BlockUtils.isSign(loc.getBlock()))
				continue;
			SignText.apply((Sign) loc.getBlock().getState(), SignText.render("door", Collections.singletonMap("price", Integer.toString(price))));
		}
		isOpened = false;
	}

	public List<SpawnPoint> getSpawnsInRoomDoorLeadsTo()
	{
		return spawnsInRoomDoorLeadsTo;
	}

	public void addSign(Location loc)
	{
		signsLocations.add(loc);
		GameManager.INSTANCE.saveAllGames();
	}

	public List<Location> getSignsLocations()
	{
		return signsLocations;
	}

	/**
	 * @return the locations of the door's blocks, used as a proximity anchor when the door has no sign
	 */
	public List<Location> getBlockLocations()
	{
		List<Location> locations = new ArrayList<>();
		for(Block block : blocks.keySet())
			if(block.getType() != Material.AIR)
				locations.add(block.getLocation());
		return locations;
	}

	/**
	 * Loads all the blocks to the block list
	 *
	 * @param blocks array to load from
	 */

	private void loadBlocks(JsonArray blocks)
	{
		for(JsonElement blockElem : blocks)
		{
			if(!blockElem.isJsonObject())
				continue;
			JsonObject blockJson = blockElem.getAsJsonObject();

			Location loc = CustomConfig.getLocationWithWorld(blockJson, "", game.getWorld());

			if(loc != null)
			{
				Material mat = BlockUtils.getMaterialFromKey(CustomConfig.getString(blockJson, "material", ""));
				Block block = loc.getBlock();
				this.blocks.put(block, mat);
				String data = CustomConfig.getString(blockJson, "data", null);
				if(data != null)
					blockData.put(block, data);
				else if(mat != null && block.getType() == mat)
					blockData.put(block, BlockUtils.dataOf(block)); // saved before states were kept: take it as it stands
				BlockUtils.restoreBlocks(java.util.Map.of(block, mat == null ? Material.AIR : mat), blockData);
			}
			else
			{
				COMZombies.log.log(Level.WARNING, () -> "Failed to load in location for door block! Json: " + blockJson.toString());
			}
		}
	}

	/**
	 * Precondition - p1 and p2 both have valid worlds.
	 *
	 * @param p1 - Point one for the block locations
	 * @param p2 - Point two for the block locations
	 */
	public void saveBlocks(Location p1, Location p2)
	{
		if(p1 != null && p2 != null)
		{
			int x1 = Math.min(p1.getBlockX(), p2.getBlockX()); // Eg. 5
			int x2 = Math.max(p1.getBlockX(), p2.getBlockX()); // Eg. 6
			int y1 = Math.min(p1.getBlockY(), p2.getBlockY()); // Eg. 89
			int y2 = Math.max(p1.getBlockY(), p2.getBlockY()); // Eg. 90
			int z1 = Math.min(p1.getBlockZ(), p2.getBlockZ()); // Eg. 12
			int z2 = Math.max(p1.getBlockZ(), p2.getBlockZ()); // Eg. 13
			for(int x = 0; x <= x2 - x1; x++)
			{
				for(int y = 0; y <= y2 - y1; y++)
				{
					for(int z = 0; z <= z2 - z1; z++)
					{
						Location loc = new Location(p1.getWorld(), x + x1, y + y1, z + z1);
						Block block = loc.getBlock();
						blocks.put(block, block.getType());
						if(!block.getType().isAir())
							blockData.put(block, BlockUtils.dataOf(block));
					}
				}
			}
		}
		GameManager.INSTANCE.saveAllGames();
	}

	public void addDoorBlock(Location loc)
	{
		Block block = loc.getBlock();
		this.addDoorBlock(block, block.getType());
	}

	/**
	 * Takes the blocks as they are in the world right now, at the end of an edit: a block that was swapped for another one
	 * (a temporary block replaced by the real one) remembers the new material. A spot that is air now (a temporary block
	 * that was only there to click it) stays selected and stays air. Without this the material from the moment the block
	 * was clicked was kept, so temporary blocks came back on every repair and reload.
	 *
	 * @return how many selected spots are air
	 */
	public int syncWithWorld()
	{
		int air = 0;
		for(Map.Entry<Block, Material> e : blocks.entrySet())
		{
			Material now = e.getKey().getType();
			e.setValue(now.isAir() ? Material.AIR : now);
			if(!now.isAir())
				blockData.put(e.getKey(), BlockUtils.dataOf(e.getKey()));
			if(now.isAir())
				air++;
		}
		return air;
	}

	public void addDoorBlock(Block block, Material mat)
	{
		blocks.put(block, mat);
		if(mat != null && block.getType() == mat)
			blockData.put(block, BlockUtils.dataOf(block));
	}

	/** The saved full state of a block (null if none), for saving. */
	public String getBlockData(Block b)
	{
		String d = blockData.get(b);
		return BlockUtils.parseData(d, blocks.get(b)) != null ? d : null;
	}

	public void setBlockData(Block b, String data)
	{
		if(data != null && !data.isEmpty())
			blockData.put(b, data);
	}

	public void removeDoorBlock(Location loc)
	{
		Block block = loc.getBlock();
		blocks.remove(block);
	}

	/** A copy of the block -> original material map, so the door editor can undo. */
	public Map<Block, Material> copyBlockMaterials()
	{
		return new HashMap<>(blocks);
	}

	/** Puts the door back to a state taken earlier (blocks, room spawns, price, power). */
	public void restore(Map<Block, Material> savedBlocks, List<SpawnPoint> savedSpawns, int savedPrice, boolean savedPower)
	{
		blocks.clear();
		blocks.putAll(savedBlocks);
		spawnsInRoomDoorLeadsTo = new ArrayList<>(savedSpawns);
		price = savedPrice;
		powerRequired = savedPower;
	}

	public List<Block> getBlocks()
	{
		return new ArrayList<>(blocks.keySet());
	}

	public boolean hasDoorLoc(Block b)
	{
		return blocks.containsKey(b);
	}

	public void setPowerRequired(boolean powerRequired)
	{
		this.powerRequired = powerRequired;
	}

	/** A door that opens by itself once every teleporter pad is linked at the mainframe (and cannot be bought). */
	public boolean opensWhenLinked()
	{
		return openOnLink;
	}

	public void setOpensWhenLinked(boolean openOnLink)
	{
		this.openOnLink = openOnLink;
	}

	/** A power door that opens by itself the moment the power is turned on, without anyone buying it. */
	public boolean opensWithPower()
	{
		return autoOpen;
	}

	public void setOpensWithPower(boolean autoOpen)
	{
		this.autoOpen = autoOpen;
	}

	public boolean requiresPower()
	{
		return powerRequired;
	}

}
