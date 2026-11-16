package com.theprogrammingturkey.comz.game.signs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.BlockUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Per-arena look of the game signs, by category (perk, gun, door, ...):
 *  * Hidden categories: their sign blocks are removed. These mechanics still work, because players use them with the
 *    interact key when standing near (that only needs the saved location). A hidden sign's block and text are kept, so
 *    showing the category puts the same sign back. New signs of a hidden category disappear once finished.
 *  * Wood: every sign of a category is changed to that wood (wall/standing/hanging, facing and text kept).
 * Saved to signstyles.json in the plugin folder.
 */
public final class SignStyles
{
	/** The categories that work without their sign (interact key near them). Ammo crates, kits and so on need the click. */
	public static final List<String> CATEGORIES = List.of("perk", "pack_a_punch", "gun", "power", "teleporter", "mainframe", "door",
			"mystery_box");
	public static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
			"pale_oak", "bamboo", "crimson", "warped");
	private static final String[] SUFFIXES = {"_WALL_HANGING_SIGN", "_HANGING_SIGN", "_WALL_SIGN", "_SIGN"};
	/** Key the first version (perk signs only) kept a hidden sign's block under, in the sign registry data. */
	private static final String OLD_HIDDEN = "hiddenSign";

	private static final class ArenaStyle
	{
		Set<String> hidden = new LinkedHashSet<>();
		Map<String, String> wood = new HashMap<>();
	}

	private static final class Stored
	{
		String block;
		String category;
		String[] front;
		String[] back;
		String frontColor, backColor;
		boolean frontGlow, backGlow, waxed;
	}

	private static final class Data
	{
		Map<String, ArenaStyle> arenas = new HashMap<>();
		Map<String, Stored> hidden = new HashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
	private static Data data = new Data();

	private SignStyles()
	{
	}

	private static File file()
	{
		return new File(COMZombies.getPlugin().getDataFolder(), "signstyles.json");
	}

	public static void load()
	{
		data = new Data();
		File f = file();
		if(!f.isFile())
			return;
		try(Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8))
		{
			Data d = GSON.fromJson(r, Data.class);
			if(d != null)
			{
				if(d.arenas == null)
					d.arenas = new HashMap<>();
				if(d.hidden == null)
					d.hidden = new HashMap<>();
				for(ArenaStyle s : d.arenas.values())
				{
					if(s.hidden == null)
						s.hidden = new LinkedHashSet<>();
					if(s.wood == null)
						s.wood = new HashMap<>();
				}
				data = d;
			}
		}
		catch(Exception e)
		{
			COMZombies.log.log(Level.WARNING, "Could not read signstyles.json", e);
		}
	}

	private static void save()
	{
		try
		{
			File f = file();
			f.getParentFile().mkdirs();
			try(Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8))
			{
				GSON.toJson(data, w);
			}
		}
		catch(Exception e)
		{
			COMZombies.log.log(Level.WARNING, "Could not save signstyles.json", e);
		}
	}

	private static String arenaKey(Game game)
	{
		return game.getTemplateName().toLowerCase(Locale.ROOT);
	}

	private static ArenaStyle style(Game game)
	{
		return data.arenas.computeIfAbsent(arenaKey(game), k -> new ArenaStyle());
	}

	private static String key(Location loc)
	{
		return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
	}

	public static boolean isHidden(Game game, String category)
	{
		ArenaStyle s = data.arenas.get(arenaKey(game));
		return s != null && s.hidden.contains(category);
	}

	public static String wood(Game game, String category)
	{
		ArenaStyle s = data.arenas.get(arenaKey(game));
		return s == null ? null : s.wood.get(category);
	}

	/** The wall sign material for a category (the mystery box places its own sign when it moves). */
	public static Material wallSign(Game game, String category)
	{
		String wood = wood(game, category);
		Material m = wood == null ? null : Material.matchMaterial(wood.toUpperCase(Locale.ROOT) + "_WALL_SIGN");
		return m == null ? Material.OAK_WALL_SIGN : m;
	}

	/** Every sign location of a category in the arena (hidden ones included). */
	public static List<Location> locations(Game game, String category)
	{
		List<Location> out = new ArrayList<>();
		switch(category)
		{
			case "door" ->
			{
				for(Door door : game.doorManager.getDoors())
					out.addAll(door.getSignsLocations());
			}
			case "mystery_box" ->
			{
				for(RandomBox box : game.boxManager.getBoxes())
					if(!box.isChestBox())
						out.add(box.getLocation());
			}
			default ->
			{
				for(Location loc : SignRegistry.INSTANCE.findByType(category))
					if(GameManager.INSTANCE.getGame(loc) == game)
						out.add(loc);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ hide / show

	/** Hides or shows every sign of a category in the arena (and remembers it for new signs). Returns how many changed. */
	public static int setHidden(Game game, String category, boolean hide)
	{
		if(hide)
			style(game).hidden.add(category);
		else
			style(game).hidden.remove(category);
		int n = 0;
		for(Location loc : locations(game, category))
			if(hide ? hide(loc, category) : show(loc))
				n++;
		save();
		return n;
	}

	private static boolean hide(Location loc, String category)
	{
		Block block = loc.getBlock();
		if(!BlockUtils.isSign(block))
			return false;
		Stored s = capture(block);
		s.category = category;
		data.hidden.put(key(loc), s);
		block.setType(Material.AIR, false);
		return true;
	}

	private static boolean show(Location loc)
	{
		Stored s = data.hidden.remove(key(loc));
		if(s == null)
			return showOld(loc);
		Block block = loc.getBlock();
		if(!block.getType().isAir())
			return false;
		restore(block, s);
		return true;
	}

	/** A perk sign hidden by the first version (its block kept in the registry data). */
	private static boolean showOld(Location loc)
	{
		SignRegistry.Entry entry = SignRegistry.INSTANCE.get(loc);
		if(entry == null || !entry.data.containsKey(OLD_HIDDEN))
			return false;
		Map<String, String> d = new HashMap<>(entry.data);
		String blockData = d.remove(OLD_HIDDEN);
		SignRegistry.INSTANCE.register(loc, entry.type, d);
		Block block = loc.getBlock();
		if(!block.getType().isAir())
			return false;
		try
		{
			block.setBlockData(Bukkit.createBlockData(blockData), false);
		}
		catch(IllegalArgumentException e)
		{
			block.setType(Material.OAK_WALL_SIGN, false);
		}
		IGameSign logic = GameSigns.fromType(entry.type);
		String[] text = logic == null ? null : logic.getText(d);
		if(text != null && block.getState() instanceof Sign sign)
			SignText.apply(sign, text);
		return true;
	}

	// ------------------------------------------------------------------ wood

	/** Changes every sign of a category in the arena to the wood (and new ones too). Returns how many changed. */
	public static int setWood(Game game, String category, String wood)
	{
		style(game).wood.put(category, wood);
		int n = 0;
		for(Location loc : locations(game, category))
		{
			Stored s = data.hidden.get(key(loc));
			if(s != null)
			{
				s.block = convert(s.block, wood);
				n++;
			}
			else if(changeWood(loc.getBlock(), wood))
				n++;
		}
		save();
		return n;
	}

	private static boolean changeWood(Block block, String wood)
	{
		if(!BlockUtils.isSign(block))
			return false;
		Stored s = capture(block);
		String to = convert(s.block, wood);
		if(to.equals(s.block))
			return false;
		s.block = to;
		restore(block, s);
		return true;
	}

	/** "minecraft:oak_wall_sign[facing=north,...]" to the same kind of sign in another wood. */
	private static String convert(String blockData, String wood)
	{
		int b = blockData.indexOf('[');
		String id = b < 0 ? blockData : blockData.substring(0, b);
		String props = b < 0 ? "" : blockData.substring(b);
		String name = id.substring(id.indexOf(':') + 1).toUpperCase(Locale.ROOT);
		for(String suffix : SUFFIXES)
		{
			if(name.endsWith(suffix))
			{
				Material m = Material.matchMaterial(wood.toUpperCase(Locale.ROOT) + suffix);
				return m == null ? blockData : m.getKey() + props;
			}
		}
		return blockData;
	}

	// ------------------------------------------------------------------ new signs

	/** True when a new sign of this category will disappear once finished. */
	public static boolean hidesNew(Game game, String category)
	{
		return CATEGORIES.contains(category) && (isHidden(game, category) || (category.equals("perk")
				&& COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.hidePerkSigns", true)));
	}

	/** A game sign was just finished: give it its category's wood, and hide it if the category is hidden. */
	public static void onCreated(Game game, String category, Location loc)
	{
		if(game == null || !CATEGORIES.contains(category))
			return;
		String wood = wood(game, category);
		if(wood != null)
			changeWood(loc.getBlock(), wood);
		if(hidesNew(game, category) && hide(loc, category))
			save();
	}

	// ------------------------------------------------------------------ copying a sign

	private static Stored capture(Block block)
	{
		Stored s = new Stored();
		s.block = block.getBlockData().getAsString();
		if(block.getState() instanceof Sign sign)
		{
			SignSide f = sign.getSide(Side.FRONT), k = sign.getSide(Side.BACK);
			s.front = lines(f);
			s.back = lines(k);
			s.frontColor = f.getColor() == null ? null : f.getColor().name();
			s.backColor = k.getColor() == null ? null : k.getColor().name();
			s.frontGlow = f.isGlowingText();
			s.backGlow = k.isGlowingText();
			s.waxed = sign.isWaxed();
		}
		return s;
	}

	private static String[] lines(SignSide side)
	{
		String[] out = new String[4];
		for(int i = 0; i < 4; i++)
			out[i] = LEGACY.serialize(side.line(i));
		return out;
	}

	private static void restore(Block block, Stored s)
	{
		try
		{
			block.setBlockData(Bukkit.createBlockData(s.block), false);
		}
		catch(IllegalArgumentException e)
		{
			block.setType(Material.OAK_WALL_SIGN, false);
		}
		if(!(block.getState() instanceof Sign sign))
			return;
		apply(sign.getSide(Side.FRONT), s.front, s.frontColor, s.frontGlow);
		apply(sign.getSide(Side.BACK), s.back, s.backColor, s.backGlow);
		sign.setWaxed(s.waxed);
		sign.update(true, false);
	}

	private static void apply(SignSide side, String[] lines, String color, boolean glow)
	{
		if(lines != null)
			for(int i = 0; i < 4 && i < lines.length; i++)
				side.line(i, LEGACY.deserialize(lines[i] == null ? "" : lines[i]));
		if(color != null)
		{
			try
			{
				side.setColor(DyeColor.valueOf(color));
			}
			catch(IllegalArgumentException ignored)
			{
			}
		}
		side.setGlowingText(glow);
	}
}
