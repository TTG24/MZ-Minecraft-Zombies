package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.Util;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BoxManager
{
	private final Game game;
	private final List<RandomBox> boxes = new ArrayList<>();
	private RandomBox currentBox;
	private boolean multiBox;
	/** Spins at the current box's spot since it got there (the teddy bear can't turn up in the first few). */
	private int spinsHere;
	/** The box is moving (after a teddy bear): no box can be used until it turns up somewhere else. */
	private boolean moving;
	/** How long the box is away when it moves (ticks, from the bear flying off): the move music's end. */
	private static final int AWAY_TICKS = 177;

	public BoxManager(Game game)
	{
		this.game = game;
	}

	public void loadAllBoxesToGame(JsonArray mystery_boxes, JsonObject arenaSettingsJson)
	{
		multiBox = CustomConfig.getBoolean(arenaSettingsJson, "multiple_mystery_boxes", false);
		boxes.clear();
		for(JsonElement boxElem : mystery_boxes)
		{
			if(!boxElem.isJsonObject())
				continue;
			JsonObject boxJson = boxElem.getAsJsonObject();
			Location loc = CustomConfig.getLocationWithWorld(boxJson, "", game.getWorld());
			String facing = CustomConfig.getString(boxJson, "facing", "");
			int cost = CustomConfig.getInt(boxJson, "cost", 2000);
			String boxId = CustomConfig.getString(boxJson, "id", "MISSING");
			boolean chest = CustomConfig.getBoolean(boxJson, "chest", false);
			RandomBox point = new RandomBox(loc, BlockFace.valueOf(facing), game, boxId, cost, chest);
			boxes.add(point);
		}
	}

	public JsonArray save()
	{
		JsonArray saveJson = new JsonArray();
		for(RandomBox box : boxes)
		{
			JsonObject boxJson = CustomConfig.locationToJsonNoWorld(box.getLocation());
			boxJson.addProperty("facing", box.getFacing().name());
			boxJson.addProperty("cost", box.getCost());
			boxJson.addProperty("id", box.getId());
			if(box.isChestBox())
				boxJson.addProperty("chest", true);
			saveJson.add(boxJson);
		}
		return saveJson;
	}

	public void resetBoxes()
	{
		for(RandomBox box : this.boxes)
			box.reset();
	}

	public RandomBox getBox(String id)
	{
		for(RandomBox b : boxes)
			if(id.equalsIgnoreCase(b.getId()))
				return b;
		return null;
	}

	public RandomBox getBox(Location loc)
	{
		for(RandomBox b : boxes)
			if(b.isAt(loc))
				return b;
		return null;
	}

	public RandomBox getRandomBox(RandomBox exclude)
	{
		if(boxes.isEmpty())
			return null;

		// Just to prevent infinite loop below
		if(boxes.size() == 1)
			return boxes.get(0);

		RandomBox newBox;
		do
			newBox = boxes.get(COMZombies.rand.nextInt(boxes.size()));
		while(newBox == exclude);

		return newBox;
	}

	public void removeBox(Player player, RandomBox box)
	{
		if(boxes.contains(box))
		{
			player.sendMessage(Colors.DARK_GREEN + "MysteryBox removed!");
			// A chest box keeps its chest; a sign box's sign is removed
			if(!box.isChestBox())
				BlockUtils.setBlockToAir(box.getLocation());
			boxes.remove(box);
			GameManager.INSTANCE.saveAllGames();
		}
	}

	public void addBox(RandomBox box)
	{
		if(game.getStatus() == Game.GameStatus.DISABLED || game.getStatus() == Game.GameStatus.WAITING)
		{
			boolean same = false;
			for(RandomBox b : boxes)
			{
				if(b.isAt(box.getLocation()))
				{
					same = true;
					break;
				}
			}
			if(!same)
			{
				boxes.add(box);
				GameManager.INSTANCE.saveAllGames();
			}
		}
	}

	public RandomBox getCurrentbox()
	{
		return currentBox;
	}

	public int getNumBoxes()
	{
		return boxes.size();
	}

	public List<RandomBox> getBoxes()
	{
		return new ArrayList<>(boxes);
	}

	public void setCurrentBox(RandomBox box)
	{
		if(multiBox)
		{
			currentBox = null;
			return;
		}
		if(currentBox != null)
			currentBox.removeBox();
		currentBox = box;
		spinsHere = 0;
		moving = false;
		currentBox.loadBox();
	}

	public void FireSale()
	{
		if(game.isFireSale())
		{
			loadAllBoxes();
		}
		else
		{
			unloadAllBoxes();
			// (none while the box is moving: it turns up when the move is done)
			if(currentBox != null)
				currentBox.loadBox();
		}
	}

	public void unloadAllBoxes()
	{
		for(RandomBox b : boxes)
			b.removeBox();
	}

	public void loadAllBoxes()
	{
		for(RandomBox b : boxes)
			b.loadBox();
	}

	// Finds the closest locations to point loc, it results numToGet amount of
	// spawn points

	public int getTotalBoxes()
	{
		return boxes.size();
	}

	public boolean isMultiBox()
	{
		return multiBox;
	}

	public Game getGame()
	{
		return game;
	}

	public String getNextBoxName()
	{
		return Util.genRandId();
	}

	/**
	 * A spin at the (single) current box: whether it turns up the teddy bear. Never with one box, multiple boxes or in a fire
	 * sale, nor in the first teddy_bear_safe_uses spins at a spot; after that teddy_bear_percent % a spin.
	 */
	public boolean rollTeddyBear()
	{
		if(multiBox || game.isFireSale() || moving || boxes.size() < 2 || game.getTeddyBearPercent() <= 0)
			return false;
		spinsHere++;
		return spinsHere > game.getTeddyBearSafeUses() && COMZombies.rand.nextInt(100) < game.getTeddyBearPercent();
	}

	public boolean isMoving()
	{
		return moving;
	}

	/** The bear has flown off: the box is gone for a while (no box at all), then turns up at another spot. */
	public void teddyBear()
	{
		final RandomBox old = currentBox;
		if(old != null)
			old.removeBox();
		currentBox = null;
		moving = true;
		game.scheduleMatchTask(AWAY_TICKS, () ->
		{
			if(!moving)
				return;
			RandomBox b = getRandomBox(old);
			moving = false;
			spinsHere = 0;
			currentBox = b;
			if(b != null)
			{
				b.loadBox();
				game.sendMessageToPlayers(Colors.GOLD + "The mystery box has moved!");
			}
		});
	}
}
