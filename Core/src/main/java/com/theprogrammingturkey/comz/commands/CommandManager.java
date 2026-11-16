package com.theprogrammingturkey.comz.commands;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class CommandManager implements TabExecutor
{
	public static final CommandManager INSTANCE = new CommandManager();
	private final HashMap<String, SubCommand> commandList = new HashMap<>();
	private final HashMap<Player, ZombiesHelpCommand> helpCommand = new HashMap<>();

	public CommandManager()
	{
		load();
	}

	private void load()
	{
		// Creates an arena:
		// Perm
		// - zombies.createarena
		commandList.put("createarena", new CreateArenaCommand(COMZPermission.CREATE_ARENA));
		commandList.put("instances", new InstancesCommand(COMZPermission.CREATE_ARENA));
		commandList.put("ca", commandList.get("createarena"));
		commandList.put("addarena", commandList.get("createarena"));
		commandList.put("newarena", commandList.get("createarena"));

		// Reloads the plugin:
		// Perm
		// - zombies.reload
		commandList.put("r", new ReloadCommand(COMZPermission.RELOAD));
		commandList.put("reload", commandList.get("r"));

		// Removes the given arena:
		// Perm
		// - zombies.removearena
		commandList.put("ra", new RemoveArenaCommand(COMZPermission.REMOVE_ARENA));
		commandList.put("removearena", commandList.get("ra"));
		commandList.put("delarena", commandList.get("ra"));

		// Disables the given arena:
		// Perm
		// - zombies.disable
		commandList.put("d", new DisableCommand(COMZPermission.DISABLE_ARENA));
		commandList.put("disable", commandList.get("d"));

		// Force starts the given arena:
		// Perm
		// - zombies.forcestart
		commandList.put("start", new StartCommand(COMZPermission.FORCE_START));
		commandList.put("s", commandList.get("start"));
		commandList.put("forcestart", commandList.get("start"));

		// Force ends the given arena:
		// Perm
		// - zombies.forceend
		commandList.put("end", new EndCommand(COMZPermission.FORCE_END));
		commandList.put("forceend", commandList.get("end"));

		// Enables the given arena:
		// Perm
		// - zombies.enable
		commandList.put("e", new EnableCommand(COMZPermission.ENABLE_ARENA));
		commandList.put("enable", commandList.get("e"));
		commandList.put("enablearena", commandList.get("e"));

		// Puts you if applicable in the arena specified or not specified:
		// Perm
		// - zombies.join
		// - zombies.user
		commandList.put("join", new JoinCommand(COMZPermission.JOIN_ARENA));
		commandList.put("j", commandList.get("join"));
		commandList.put("joinarena", commandList.get("join"));

		// Spins up a private single-player copy of an arena and drops you in:
		// Perm
		// - zombies.solo
		// - zombies.user
		commandList.put("solo", new JoinSoloCommand(COMZPermission.SOLO_ARENA));
		commandList.put("js", commandList.get("solo"));

		// Base leaderboard command:
		// Perm
		// - zombies.leaderboards
		// - zombies.user
		commandList.put("leaderboard", new LeaderboardsCommand(COMZPermission.LEADERBOARDS));
		commandList.put("lead", commandList.get("leaderboard"));
		commandList.put("leaders", commandList.get("leaderboard"));

		// Leave the given arena:
		// Perm
		// - zombies.leave
		// - zombies.user
		commandList.put("leave", new LeaveCommand(COMZPermission.JOIN_ARENA));
		commandList.put("l", commandList.get("leave"));

		// Rejoin a game in your previous arena:
		// Perm
		// - zombies.rejoin
		commandList.put("rejoin", new RejoinCommand(COMZPermission.REJOIN_ARENA));

		// Cancel an operation:
		// Perm -
		// zombies.cancel
		commandList.put("cancel", new CancelCommand(COMZPermission.CANCEL));
		commandList.put("c", commandList.get("cancel"));

		// Shows a list of all the arenas:
		// Perm
		// - zombies.listarenas
		// - zombies.user
		commandList.put("listarenas", new ArenaListCommand(COMZPermission.LIST_ARENAS));
		commandList.put("arenalist", commandList.get("listarenas"));
		commandList.put("la", commandList.get("listarenas"));

		// Information on the given arena:
		// Perm
		// - zombies.info
		commandList.put("info", new InfoCommand(COMZPermission.INFO));
		commandList.put("information", commandList.get("info"));

		// Edit spawn operation:
		// Perm
		// - zombies.editspawns
		commandList.put("es", new EditZSpawnCommand(COMZPermission.EDIT_ZOMBIE_SPAWNS));
		commandList.put("editspawns", commandList.get("es"));
		commandList.put("spawns", commandList.get("es"));

		// Kicks a player from the given arena:
		// Perm
		// - zombies.kick
		commandList.put("kick", new KickCommand(COMZPermission.KICK));
		commandList.put("k", commandList.get("kick"));

		// Puts the player into arena creation mode:
		// Perm -
		// zombies.editarena
		commandList.put("edit", new EditCommand(COMZPermission.EDIT_ARENA));
		commandList.put("editarena", commandList.get("edit"));

		// Puts the given player into door creation mode:
		// Perm
		// - zombies.adddoor
		commandList.put("adddoor", new AddDoorCommand(COMZPermission.ADD_DOOR));
		commandList.put("ad", commandList.get("adddoor"));

		// Edits an existing door in place:
		// Perm
		// - zombies.adddoor
		commandList.put("editdoor", new EditDoorCommand(COMZPermission.ADD_DOOR));
		commandList.put("ed", commandList.get("editdoor"));

		// Shows the list of every gun:
		// Perm
		// - zombies.listguns
		commandList.put("listguns", new GunListCommand(COMZPermission.LIST_GUNS));
		commandList.put("lg", commandList.get("listguns"));
		commandList.put("guns", new GunsCommand(COMZPermission.LIST_GUNS));

		// Removes a door from the given arena:
		// Perm
		// - zombies.removedoor
		commandList.put("rd", new RemoveDoorCommand(COMZPermission.REMOVE_DOOR));
		commandList.put("removedoors", commandList.get("rd"));
		commandList.put("removedoor", commandList.get("rd"));

		// adds a barrier from the given arena:
		// Perm
		// - zombies.addbarrier
		commandList.put("addbarrier", new AddBarrier(COMZPermission.ADD_BARRIER));
		commandList.put("ab", commandList.get("addbarrier"));

		// removes a barrier from the given arena:
		// Perm
		// - zombies.removebarrier
		commandList.put("removebarrier", new RemoveBarrierCommand(COMZPermission.REMOVE_BARRIER));
		commandList.put("rb", commandList.get("removebarrier"));

		// disables power for the given arena:
		// Perm
		// - zombies.disablepower
		commandList.put("disablepower", new DisablePowerCommand(COMZPermission.DISABLE_POWER));
		commandList.put("dp", commandList.get("disablepower"));

		// removes a barrier from the given arena:
		// Perm
		// - zombies.spectate
		// - zombies.user
		commandList.put("spec", new SpectateCommand(COMZPermission.SPECTATE));
		commandList.put("spectate", commandList.get("spec"));

		// adds a teleporter the given arena:
		// Perm
		// - zombies.addteleporter
		commandList.put("addteleporter", new AddTeleporterCommand(COMZPermission.ADD_TELEPORTER));
		commandList.put("at", commandList.get("addteleporter"));

		// removes a teleporter from the given arena:
		// Perm
		// - zombies.removeteleporter
		commandList.put("removeteleporter", new RemoveTeleporterCommand(COMZPermission.REMOVE_TELEPORTER));
		commandList.put("rt", commandList.get("removeteleporter"));

		// lists the available perks:
		// Perm
		// - zombies.perks
		commandList.put("perks", new PerksCommand(COMZPermission.PERKS));

		// enters into a helpful debug mods:
		// Perm
		// - zombies.zombies.debug
		commandList.put("debug", new DebugCommand(COMZPermission.DEBUG));

		// Debug: jumps the running game to a round:
		// Perm
		// - zombies.debug
		commandList.put("round", new RoundCommand(COMZPermission.DEBUG));

		// Debug: add, take or set a player's points in a game:
		// Perm
		// - zombies.debug
		commandList.put("points", new PointsCommand(COMZPermission.DEBUG));

		// Marks a zombie spawn as always active (never locked by doors):
		// Perm
		// - zombies.editzspawns
		commandList.put("alwaysspawn", new AlwaysSpawnCommand(COMZPermission.EDIT_ZOMBIE_SPAWNS));
		commandList.put("as", commandList.get("alwaysspawn"));

		// Permanently outlines every spawn point (toggle), also during setup commands:
		// Perm
		// - zombies.editzspawns
		commandList.put("showspawns", new ShowSpawnsCommand(COMZPermission.EDIT_ZOMBIE_SPAWNS));
		commandList.put("ss", commandList.get("showspawns"));

		// Permanently outlines every door (toggle), also during setup commands:
		// Perm
		// - zombies.adddoor
		commandList.put("showdoors", new ShowDoorsCommand(COMZPermission.ADD_DOOR));
		commandList.put("sd", commandList.get("showdoors"));

		// Outlines everything set up in an arena at once (toggle): barriers, spawns, doors, boxes, signs, teleporters, warps, corners:
		// Perm
		// - zombies.editarena
		commandList.put("showall", new ShowAllCommand(COMZPermission.EDIT_ARENA));
		commandList.put("sa", commandList.get("showall"));
		commandList.put("showeverything", commandList.get("showall"));

		// Adds/removes chest mystery boxes by clicking chests:
		// Perm
		// - zombies.editarena
		commandList.put("mysterybox", new MysteryBoxCommand(COMZPermission.EDIT_ARENA));
		commandList.put("mb", commandList.get("mysterybox"));
		// Hides (removes) or puts back game signs by category, or changes their wood; they keep working by the interact key:
		// Perm
		// - zombies.editarena
		commandList.put("signs", new SignsCommand(COMZPermission.EDIT_ARENA, null));
		commandList.put("perksigns", new SignsCommand(COMZPermission.EDIT_ARENA, "perk"));
		commandList.put("ps", commandList.get("perksigns"));

		commandList.put("showmysterybox", new ShowMysteryBoxCommand(COMZPermission.EDIT_ARENA));
		commandList.put("showmysteryboxes", commandList.get("showmysterybox"));
		commandList.put("smb", commandList.get("showmysterybox"));

		// Der Riese teleporter regions:
		// Perm
		// - zombies.editarena
		commandList.put("linkadd", new LinkCommand(COMZPermission.EDIT_ARENA, LinkCommand.Mode.ADD));
		commandList.put("addlink", commandList.get("linkadd"));
		commandList.put("mainlink", new LinkCommand(COMZPermission.EDIT_ARENA, LinkCommand.Mode.MAINFRAME));
		commandList.put("linkremove", new LinkCommand(COMZPermission.EDIT_ARENA, LinkCommand.Mode.REMOVE));
		commandList.put("removelink", commandList.get("linkremove"));
		commandList.put("links", new LinkCommand(COMZPermission.EDIT_ARENA, LinkCommand.Mode.SHOW));
		commandList.put("showlinks", commandList.get("links"));
		commandList.put("linkdest", new LinkCommand(COMZPermission.EDIT_ARENA, LinkCommand.Mode.DESTINATION));

		// Lists and highlights the spawns a door unlocks:
		// Perm
		// - zombies.editzspawns
		commandList.put("doorspawns", new DoorSpawnsCommand(COMZPermission.EDIT_ZOMBIE_SPAWNS));

		// Lists and highlights an arena's barriers:
		// Perm
		// - zombies.editzspawns
		commandList.put("barriers", new BarriersCommand(COMZPermission.EDIT_ZOMBIE_SPAWNS));
		commandList.put("showbarriers", commandList.get("barriers"));
		commandList.put("sb", commandList.get("barriers"));

		// Edits an existing barrier in place:
		// Perm
		// - zombies.addbarrier
		commandList.put("editbarrier", new EditBarrierCommand(COMZPermission.ADD_BARRIER));
		commandList.put("eb", commandList.get("editbarrier"));

		// Links an arena to a WorldGuard region for non-box containment:
		// Perm
		// - zombies.setregion
		commandList.put("setregion", new SetRegionCommand(COMZPermission.SET_REGION));
		commandList.put("region", commandList.get("setregion"));

		// Toggles the deep crouch stance (visual pose + slower movement):
		// Perm
		// - zombies.crouch
		commandList.put("crouch", new CrouchCommand(COMZPermission.CROUCH));
	}

	/** Setup/edit commands whose first argument is the arena: it may be left out when standing in the arena. */
	private static final java.util.Set<String> ARENA_COMMANDS = java.util.Set.of(
			"es", "editspawns", "spawns", "edit", "editarena", "adddoor", "ad", "editdoor", "ed", "rd", "removedoor", "removedoors",
			"addbarrier", "ab", "removebarrier", "rb", "editbarrier", "eb", "alwaysspawn", "as", "showspawns", "ss", "showdoors", "sd", "showall", "sa", "showeverything",
			"mysterybox", "mb", "showmysterybox", "showmysteryboxes", "smb", "doorspawns", "barriers", "showbarriers", "sb", "disablepower", "dp", "setregion", "region", "info", "information",
			"d", "disable", "e", "enable", "enablearena",
			"linkadd", "addlink", "mainlink", "linkremove", "removelink", "links", "showlinks", "linkdest", "perksigns", "ps", "signs");

	/**
	 * When the arena name is left out of a setup command, puts in the arena the player is standing in (its WorldGuard
	 * region when one is linked with /z setregion, otherwise its corner box), or failing that the nearest one within 48
	 * blocks. A real arena name typed as the first argument is always kept.
	 */
	private static String[] withDetectedArena(Player player, String[] args)
	{
		if(!ARENA_COMMANDS.contains(args[0].toLowerCase()))
			return args;
		if(args.length > 1)
			for(Game g : GameManager.INSTANCE.getGames())
				if(g.getName().equalsIgnoreCase(args[1]))
					return args;
		Game game = detectArena(player);
		if(game == null)
			return args;
		String[] out = new String[args.length + 1];
		out[0] = args[0];
		out[1] = game.getName();
		System.arraycopy(args, 1, out, 2, args.length - 1);
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Using arena " + Colors.RED + game.getName() + Colors.GRAY + " (where you are).");
		return out;
	}

	public static Game detectArena(Player player)
	{
		Game nearest = null;
		double best = 48 * 48;
		for(Game g : GameManager.INSTANCE.getGames())
		{
			if(g.isInstance() || g.arena.getWorld() == null || !g.arena.getWorld().equals(player.getWorld()))
				continue;
			if(g.arena.containsBlock(player.getLocation()))
				return g;
			if(!g.arena.areMinAndMaxSet())
				continue;
			org.bukkit.Location min = g.arena.getMin(), max = g.arena.getMax();
			org.bukkit.Location p = player.getLocation();
			double dx = Math.max(0, Math.max(Math.min(min.getX(), max.getX()) - p.getX(), p.getX() - Math.max(min.getX(), max.getX())));
			double dy = Math.max(0, Math.max(Math.min(min.getY(), max.getY()) - p.getY(), p.getY() - Math.max(min.getY(), max.getY())));
			double dz = Math.max(0, Math.max(Math.min(min.getZ(), max.getZ()) - p.getZ(), p.getZ() - Math.max(min.getZ(), max.getZ())));
			double d = dx * dx + dy * dy + dz * dz;
			if(d < best)
			{
				best = d;
				nearest = g;
			}
		}
		return nearest;
	}

	public void onRemoteCommand(Player player, String[] args)
	{
		SubCommand sub = args.length == 0 ? null : this.commandList.get(args[0].toLowerCase());
		if(sub != null)
			sub.onCommand(player, args);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args)
	{
		String command = cmd.getName();
		if(command.equalsIgnoreCase("zombies"))
		{
			if(!(sender instanceof Player))
			{
				COMZombies.log.info("You must be in a game to use this command!");
				return true;
			}

			Player player = (Player) sender;

			if(args.length == 0)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "MinecraftZombies" + Colors.GRAY + " by " + Colors.RED + "TTG24" + Colors.GRAY + ", based on Call of Minecraft: Zombies by " + Colors.RED + "IModZombies4Fun, Turkey2349 and Smeths" + Colors.GRAY + "!");
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Type /zombies help for a list of commands!");
				return true;
			}
			else if(args[0].equalsIgnoreCase("setround"))
			{
				if(!player.isOp())
					return true;
				// (missing or bad arguments used to throw; a good run also fell through to "No such command!")
				if(args.length < 3 || !args[2].matches("[0-9]{1,4}"))
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /zombies setround <arena> <round>");
					return true;
				}
				Game arena = GameManager.INSTANCE.getGame(args[1]);
				if(arena == null)
				{
					CommandUtil.sendMessageToPlayer(player, Colors.RED + args[1] + " is not a valid arena!");
					return true;
				}

				for(int i = 0; i < Integer.parseInt(args[2]); i++)
					arena.nextWave();
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Setting wave to: " + Colors.RED + args[2]);
				return true;
			}
			else if(args[0].equalsIgnoreCase("version"))
			{
				CommandUtil.sendMessageToPlayer(player, COMZombies.getPlugin().getPluginMeta().getVersion());
				return true;
			}

			if(args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("h"))
			{
				ZombiesHelpCommand help;
				if(helpCommand.get(player) == null)
				{
					help = new ZombiesHelpCommand(this, player);
					helpCommand.put(player, help);
					help.commandIssued(args);
				}
				else
				{
					help = helpCommand.get(player);
					help.commandIssued(args);
				}
				return true;
			}

			if(commandList.containsKey(args[0].toLowerCase()))
				this.commandList.get(args[0].toLowerCase()).onCommand(player, withDetectedArena(player, args));
			else
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "No such command! Type /zombies help for a list of commands!");
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args)
	{
		if(args.length == 1)
		{
			Player player = (sender instanceof Player) ? (Player) sender : null;
			return commandList.entrySet().stream().filter(entry ->
			{
				String commandName = entry.getKey().toLowerCase();
				SubCommand subCommand = entry.getValue();
				return commandName.startsWith(args[0]) && (player == null || subCommand.permission == null || subCommand.permission.hasPerm(player));
			}).map(entry -> entry.getKey()).collect(Collectors.toList());
		}
		else if(args.length > 1)
		{
			// The console has no arena-specific suggestions (this used to throw for it)
			if(!(sender instanceof Player player))
				return null;
			SubCommand sub = this.commandList.get(args[0].toLowerCase());
			List<String> list = sub != null ? sub.onTabComplete(player, args) : null;
			if(list == null)
				return GameManager.INSTANCE.getArenas().stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
			else
				return list;
		}
		else
		{
			return null;
		}
	}
}
