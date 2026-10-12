package com.theprogrammingturkey.comz.game.signs;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class PowerSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "power";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines)
	{
		Map<String, String> data = new HashMap<>();
		if(game != null)
			data.put("arena", game.getName());
		return data;
	}

	@Override
	public void onCreate(Game game, Player player, Location location, Map<String, String> data)
	{
		game.enablePower(player);
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		if(game.hasPower())
		{
			if(game.isPowered())
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "The power is already on!");
				return;
			}
			game.turnOnPower();
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Power on!");
		}
		// Refresh the sign to reflect the new state
		Block block = location.getBlock();
		if(block.getState() instanceof Sign sign)
		{
			Map<String, String> stateData = new HashMap<>();
			stateData.put("arena", game.getName());
			SignText.apply(sign, getText(stateData));
		}
	}

	@Override
	public void onBreak(Game game, Player player, Location location)
	{
		if(game.hasPower())
			game.removePower(player);
	}

	@Override
	public String[] getText(Map<String, String> data)
	{
		String arenaName = data.get("arena");
		if(arenaName == null)
			return SignText.render("power_off");

		Game game = GameManager.INSTANCE.getGame(arenaName);
		if(game != null && game.isPowered())
			return SignText.render("power_on");
		return SignText.render("power_off");
	}

	/**
	 * Re-renders every registered power sign inside the given arena to reflect its current state.
	 * Ownership is worked out from the sign's location (so old signs without stored data are covered).
	 */
	public static void refreshSigns(Game game)
	{
		String[] lines = SignText.render(game.isPowered() ? "power_on" : "power_off");

		for(Location loc : SignRegistry.INSTANCE.findByType("power"))
		{
			if(GameManager.INSTANCE.getGame(loc) != game)
				continue;
			Block block = loc.getBlock();
			if(block.getState() instanceof Sign sign)
				SignText.apply(sign, lines);
		}
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}
}
