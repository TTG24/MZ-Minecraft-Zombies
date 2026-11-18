package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.util.COMZPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clickable chat menus, showing only what the player is allowed to use:
 * <pre>
 *   /z  or  /z menu              the player menu (play, info, your settings)
 *   /z admin [section]           staff menu: arenas, games, barriers, doors and spawns, boxes and signs, teleporters, view
 * </pre>
 * A command with nothing to fill in runs when clicked; one that needs more (an arena, a name...) is put in the chat box.
 */
public class MenuCommand extends SubCommand
{
	/** One command line: what it is, what it needs, and who may see it (null: everyone). */
	private record Entry(String command, String args, String description, COMZPermission permission)
	{
		boolean allowed(Player p)
		{
			return permission == null || permission.hasPerm(p);
		}
	}

	private record Section(String id, String title, String description, List<Entry> entries)
	{
		List<Entry> allowed(Player p)
		{
			List<Entry> out = new ArrayList<>();
			for(Entry e : entries)
				if(e.allowed(p))
					out.add(e);
			return out;
		}
	}

	private static Entry e(String command, String args, String description, COMZPermission permission)
	{
		return new Entry(command, args, description, permission);
	}

	// ------------------------------------------------------------------ player menu

	private static final List<Section> PLAYER = List.of(
			new Section("play", "Play", "", List.of(
					e("join", "[arena]", "Join a game (the arena you stand in, or the one named)", COMZPermission.JOIN_ARENA),
					e("solo", "<arena>", "Play alone in your own private copy of an arena", COMZPermission.SOLO_ARENA),
					e("leave", "", "Leave your game", COMZPermission.JOIN_ARENA),
					e("rejoin", "", "Get back into the game you were in", COMZPermission.REJOIN_ARENA),
					e("spec", "<arena>", "Watch a game", COMZPermission.SPECTATE))),
			new Section("info", "Info", "", List.of(
					e("listarenas", "", "Every arena and what it's doing", COMZPermission.LIST_ARENAS),
					e("leaderboard", "<kills|revives|deaths|downs|gamesPlayed|highestRound|mostPoints> [count]", "The best players", COMZPermission.LEADERBOARDS),
					e("guns", "", "Every gun in the game", COMZPermission.LIST_GUNS),
					e("perks", "", "Every perk", COMZPermission.PERKS))),
			new Section("you", "You", "", List.of(
					e("access", "", "Your accessibility settings", null),
					e("access repair", "<toggle|hold>", "Barriers: press F to start/stop, or hold F to repair", null),
					e("access glow", "<on|off>", "See your teammates glowing through walls", null),
					e("access glow color", "<colour>", "Your glow colour", null),
					e("crouch", "", "Deep crouch stance", COMZPermission.CROUCH))));

	// ------------------------------------------------------------------ staff menu

	private static final List<Section> ADMIN = List.of(
			new Section("arenas", "Arenas", "Create, edit, enable and remove arenas", List.of(
					e("createarena", "<name>", "Make a new arena (select it with the edit wand)", COMZPermission.CREATE_ARENA),
					e("edit", "<arena> [force]", "Edit mode for an arena (force: even with players in it)", COMZPermission.EDIT_ARENA),
					e("info", "<arena> [section]", "Everything about an arena", COMZPermission.INFO),
					e("enable", "<arena>", "Open an arena for play", COMZPermission.ENABLE_ARENA),
					e("disable", "<arena>", "Close an arena", COMZPermission.DISABLE_ARENA),
					e("instances", "<arena> <0-16>", "How many ready copies of the arena to keep (0: off)", COMZPermission.CREATE_ARENA),
					e("setregion", "<arena> [region|none]", "Contain the arena in a WorldGuard region", COMZPermission.SET_REGION),
					e("removearena", "<arena>", "Delete an arena", COMZPermission.REMOVE_ARENA),
					e("cancel", "", "Cancel what you are setting up", COMZPermission.CANCEL),
					e("reload", "", "Reload the plugin's config", COMZPermission.RELOAD))),
			new Section("games", "Games", "Running matches: start, end, kick, debug", List.of(
					e("start", "<arena>", "Start a game now", COMZPermission.FORCE_START),
					e("end", "<arena>", "End a game now", COMZPermission.FORCE_END),
					e("kick", "<player>", "Kick a player out of their game", COMZPermission.KICK),
					e("round", "<number> [arena]", "Jump a running game to a round", COMZPermission.DEBUG),
					e("points", "<add|take|set> <amount> [player]", "Change someone's points", COMZPermission.DEBUG),
					e("debug", "[status]", "Debug mode", COMZPermission.DEBUG))),
			new Section("barriers", "Barriers", "Windows the zombies break through", List.of(
					e("barriers", "[arena]", "List and outline every barrier", COMZPermission.EDIT_ZOMBIE_SPAWNS),
					e("addbarrier", "<arena>", "Add a barrier", COMZPermission.ADD_BARRIER),
					e("editbarrier", "[arena] [id]", "Edit a barrier (or the one you stand by)", COMZPermission.ADD_BARRIER),
					e("removebarrier", "<arena>", "Remove a barrier", COMZPermission.REMOVE_BARRIER))),
			new Section("doors", "Doors & spawns", "Doors, and where zombies come from", List.of(
					e("showdoors", "[arena]", "Outline every door", COMZPermission.ADD_DOOR),
					e("adddoor", "<arena>", "Add a door", COMZPermission.ADD_DOOR),
					e("editdoor", "[arena] [id]", "Edit a door (or the one you stand by)", COMZPermission.ADD_DOOR),
					e("removedoor", "<arena>", "Remove a door", COMZPermission.REMOVE_DOOR),
					e("spawns", "<arena>", "Add or remove zombie spawns", COMZPermission.EDIT_ZOMBIE_SPAWNS),
					e("showspawns", "[arena]", "Outline every zombie spawn", COMZPermission.EDIT_ZOMBIE_SPAWNS),
					e("alwaysspawn", "[arena] [id|show]", "A spawn that is never locked behind a door", COMZPermission.EDIT_ZOMBIE_SPAWNS),
					e("doorspawns", "[arena] [door]", "Which spawns a door opens up", COMZPermission.EDIT_ZOMBIE_SPAWNS))),
			new Section("features", "Boxes, signs & power", "Mystery boxes, game signs and power", List.of(
					e("mysterybox", "<arena> [cost]", "Add or remove chest mystery boxes by clicking chests", COMZPermission.EDIT_ARENA),
					e("showmysterybox", "[arena]", "Outline every mystery box", COMZPermission.EDIT_ARENA),
					e("signs", "[arena] list", "Which sign kinds are hidden, and their wood", COMZPermission.EDIT_ARENA),
					e("signs", "[arena] <hide|show> <kind|all>", "Hide signs of a kind (they still work with F)", COMZPermission.EDIT_ARENA),
					e("signs", "[arena] wood <kind|all> <wood>", "Change the wood of a kind of sign", COMZPermission.EDIT_ARENA),
					e("disablepower", "<arena>", "Arena without power (everything works from the start)", COMZPermission.DISABLE_POWER))),
			new Section("teleporters", "Teleporters & links", "Teleporters, link pads and the mainframe", List.of(
					e("addteleporter", "<name>", "Save a teleporter spot where you stand", COMZPermission.ADD_TELEPORTER),
					e("removeteleporter", "<name>", "Remove a teleporter spot", COMZPermission.REMOVE_TELEPORTER),
					e("links", "[arena]", "List and outline the link pads", COMZPermission.EDIT_ARENA),
					e("linkadd", "[arena] <name>", "Select a link pad region", COMZPermission.EDIT_ARENA),
					e("mainlink", "[arena]", "Select the mainframe region", COMZPermission.EDIT_ARENA),
					e("linkdest", "[arena] <link> <otherLink|mainframe>", "Where a link pad sends you", COMZPermission.EDIT_ARENA),
					e("linkremove", "[arena] <name|mainframe>", "Remove a link pad or the mainframe", COMZPermission.EDIT_ARENA))),
			new Section("view", "View", "See an arena's whole setup at once", List.of(
					e("showall", "[arena]", "Outline everything (run again to hide every outline)", COMZPermission.EDIT_ARENA))));

	private final boolean admin;

	public MenuCommand(boolean admin)
	{
		super(null);
		this.admin = admin;
	}

	/** True when the player may use at least one staff command (shows the staff menu button). */
	public static boolean isStaff(Player p)
	{
		for(Section s : ADMIN)
			if(!s.allowed(p).isEmpty())
				return true;
		return false;
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!admin)
		{
			playerMenu(player);
			return true;
		}
		if(!isStaff(player))
		{
			com.theprogrammingturkey.comz.util.CommandUtil.noPermission(player, "use the staff menu");
			return true;
		}
		String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
		for(Section s : ADMIN)
			if(s.id().equals(id) && !s.allowed(player).isEmpty())
			{
				section(player, s, "/z admin");
				return true;
			}
		adminMenu(player);
		return true;
	}

	@Override
	public List<String> onTabComplete(Player player, String[] args)
	{
		List<String> out = new ArrayList<>();
		if(admin && args.length == 2)
			for(Section s : ADMIN)
				if(s.id().startsWith(args[1].toLowerCase(Locale.ROOT)) && !s.allowed(player).isEmpty())
					out.add(s.id());
		return out;
	}

	// ------------------------------------------------------------------ rendering

	private static void header(Player p, String title)
	{
		p.sendMessage(Component.empty());
		p.sendMessage(Component.text("━━━━━━━━ ", NamedTextColor.DARK_GRAY)
				.append(Component.text("☠ ", NamedTextColor.DARK_RED))
				.append(Component.text(title, NamedTextColor.RED, TextDecoration.BOLD))
				.append(Component.text(" ☠", NamedTextColor.DARK_RED))
				.append(Component.text(" ━━━━━━━━", NamedTextColor.DARK_GRAY)));
	}

	private static void footer(Player p, Component extra)
	{
		TextComponent.Builder line = Component.text().append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
		if(extra != null)
			p.sendMessage(extra);
		p.sendMessage(line.build());
	}

	private static void sectionTitle(Player p, String title)
	{
		p.sendMessage(Component.text(" » ", NamedTextColor.DARK_RED).append(Component.text(title, NamedTextColor.RED, TextDecoration.BOLD)));
	}

	/** "  /z join [arena]  - Join a game": click runs it, or puts it in the chat box when it needs more. */
	private static Component line(Entry e)
	{
		String base = "/z " + e.command();
		boolean needsMore = e.args().contains("<");
		Component cmd = Component.text(base, NamedTextColor.WHITE)
				.append(e.args().isEmpty() ? Component.empty() : Component.text(" " + e.args(), NamedTextColor.GRAY));
		Component hover = Component.text(e.description(), NamedTextColor.GRAY)
				.append(Component.newline())
				.append(Component.text(needsMore ? "Click to fill it in" : "Click to run", needsMore ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
		ClickEvent click = needsMore ? ClickEvent.suggestCommand(base + " ") : ClickEvent.runCommand(base);
		return Component.text("   ")
				.append(cmd.hoverEvent(HoverEvent.showText(hover)).clickEvent(click))
				.append(Component.text("  " + e.description(), NamedTextColor.DARK_GRAY));
	}

	private static Component button(String label, String command, String hover)
	{
		return Component.text("[" + label + "]", NamedTextColor.RED, TextDecoration.BOLD)
				.hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)))
				.clickEvent(ClickEvent.runCommand(command));
	}

	private static void playerMenu(Player p)
	{
		header(p, "MZ Menu");
		boolean any = false;
		for(Section s : PLAYER)
		{
			List<Entry> allowed = s.allowed(p);
			if(allowed.isEmpty())
				continue;
			any = true;
			sectionTitle(p, s.title());
			for(Entry e : allowed)
				p.sendMessage(line(e));
		}
		if(!any)
			p.sendMessage(Component.text("   Nothing here for you yet.", NamedTextColor.GRAY));
		Component extra = isStaff(p) ? Component.text("   ").append(button("Staff menu", "/z admin", "Arena setup and game tools")) : null;
		footer(p, extra);
	}

	private static void adminMenu(Player p)
	{
		header(p, "MZ Staff");
		p.sendMessage(Component.text("   Pick a section:", NamedTextColor.GRAY));
		for(Section s : ADMIN)
		{
			int n = s.allowed(p).size();
			if(n == 0)
				continue;
			p.sendMessage(Component.text(" » ", NamedTextColor.DARK_RED)
					.append(Component.text(s.title(), NamedTextColor.RED, TextDecoration.BOLD)
							.hoverEvent(HoverEvent.showText(Component.text(s.description(), NamedTextColor.GRAY)
									.append(Component.newline()).append(Component.text("Click to open", NamedTextColor.GREEN))))
							.clickEvent(ClickEvent.runCommand("/z admin " + s.id())))
					.append(Component.text("  " + s.description() + " ", NamedTextColor.DARK_GRAY))
					.append(Component.text("(" + n + ")", NamedTextColor.GRAY)));
		}
		footer(p, Component.text("   ").append(button("Player menu", "/z menu", "Back to the player menu")));
	}

	private static void section(Player p, Section s, String back)
	{
		header(p, "MZ Staff: " + s.title());
		p.sendMessage(Component.text("   " + s.description(), NamedTextColor.GRAY));
		for(Entry e : s.allowed(p))
			p.sendMessage(line(e));
		p.sendMessage(Component.text("   [arena] can be left out while you stand in the arena.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
		footer(p, Component.text("   ").append(button("Back", back, "Back to the staff menu")));
	}
}
