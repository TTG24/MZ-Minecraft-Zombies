package com.theprogrammingturkey.comz.game.managers;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.Accessibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Accessibility glow (/z access glow): the other players of your match glow through walls, in your colour, for you only.
 *
 * <p>Sent as packets to the one viewer (the server's real glowing state is untouched, so other players see nothing):
 * the entity "glowing" flag, and a scoreboard team (the glow takes the team's colour) carrying the player's rank prefix,
 * so the name tag still looks the same. Re-sent every few ticks, because the server's own updates (sneaking, sprinting)
 * would clear the flag again.
 */
public final class GlowManager
{
	public static final GlowManager INSTANCE = new GlowManager();
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

	/** Per viewer: target name -> colour it was last sent in. */
	private final Map<UUID, Map<String, String>> sent = new HashMap<>();
	private int taskId = -1;
	private int counter;
	private boolean broken;

	// Reflection (Mojang names, as Paper runs with them)
	private Method getHandle, send, dataValueCreate, chatFormattingByName, asVanilla, createTeamPacket, createPlayerPacket, setColor, setPrefix;
	private Field connection;
	private Object flagsAccessor, actionAdd;
	private Constructor<?> setDataCtor, teamCtor, scoreboardCtor;

	private GlowManager()
	{
	}

	public void start()
	{
		if(taskId != -1)
			return;
		try
		{
			init();
		} catch(Throwable t)
		{
			broken = true;
			COMZombies.getPlugin().getLogger().log(Level.WARNING, "Accessibility glow is unavailable on this server version", t);
			return;
		}
		taskId = COMZombies.scheduleTask(2, 2, this::tick);
	}

	public boolean isAvailable()
	{
		return !broken;
	}

	private void init() throws Exception
	{
		Class<?> craftPlayer = Bukkit.getServer().getClass().getClassLoader().loadClass(
				Bukkit.getServer().getClass().getPackage().getName() + ".entity.CraftPlayer");
		getHandle = craftPlayer.getMethod("getHandle");
		Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
		connection = serverPlayer.getField("connection");
		Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
		send = connection.getType().getMethod("send", packet);

		Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
		Field flags = entity.getDeclaredField("DATA_SHARED_FLAGS_ID");
		flags.setAccessible(true);
		flagsAccessor = flags.get(null);
		Class<?> accessor = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
		dataValueCreate = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue").getMethod("create", accessor, Object.class);
		setDataCtor = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket").getConstructor(int.class, List.class);

		Class<?> scoreboard = Class.forName("net.minecraft.world.scores.Scoreboard");
		scoreboardCtor = scoreboard.getConstructor();
		Class<?> playerTeam = Class.forName("net.minecraft.world.scores.PlayerTeam");
		teamCtor = playerTeam.getConstructor(scoreboard, String.class);
		Class<?> chatFormatting = Class.forName("net.minecraft.ChatFormatting");
		chatFormattingByName = chatFormatting.getMethod("getByName", String.class);
		setColor = playerTeam.getMethod("setColor", chatFormatting);
		Class<?> component = Class.forName("net.minecraft.network.chat.Component");
		setPrefix = playerTeam.getMethod("setPlayerPrefix", component);
		asVanilla = Class.forName("io.papermc.paper.adventure.PaperAdventure").getMethod("asVanilla", Component.class);
		Class<?> teamPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
		createTeamPacket = teamPacket.getMethod("createAddOrModifyPacket", playerTeam, boolean.class);
		Class<?> action = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket$Action");
		createPlayerPacket = teamPacket.getMethod("createPlayerPacket", playerTeam, String.class, action);
		actionAdd = action.getField("ADD").get(null);
	}

	private void tick()
	{
		counter++;
		Set<UUID> online = new HashSet<>();
		for(Player viewer : Bukkit.getOnlinePlayers())
		{
			online.add(viewer.getUniqueId());
			Accessibility.Settings settings = Accessibility.get(viewer.getUniqueId());
			Game game = GameManager.INSTANCE.getGame(viewer);
			Map<String, String> had = sent.getOrDefault(viewer.getUniqueId(), Map.of());
			Map<String, String> now = new HashMap<>();
			if(settings.glow() && game != null && game.getStatus() == Game.GameStatus.INGAME)
			{
				for(Player target : game.getPlayersInGame())
				{
					if(target == viewer || !target.isOnline() || game.isPlayerSpectating(target) || target.getWorld() != viewer.getWorld())
						continue;
					now.put(target.getName(), settings.color());
					try
					{
						// Team (colour) when new, changed, or every 2 s in case another plugin moved them to its own team
						if(!settings.color().equals(had.get(target.getName())) || counter % 20 == 0)
							sendTeam(viewer, target, settings.color());
						sendFlags(viewer, target, true);
					} catch(Throwable t)
					{
						fail(t);
						return;
					}
				}
			}
			// No longer shown: real flags back, team colour cleared (the rank prefix stays, so the name tag looks the same)
			for(String name : had.keySet())
			{
				if(now.containsKey(name))
					continue;
				Player target = Bukkit.getPlayerExact(name);
				if(target == null)
					continue;
				try
				{
					sendTeam(viewer, target, "reset");
					sendFlags(viewer, target, false);
				} catch(Throwable t)
				{
					fail(t);
					return;
				}
			}
			if(now.isEmpty())
				sent.remove(viewer.getUniqueId());
			else
				sent.put(viewer.getUniqueId(), now);
		}
		sent.keySet().retainAll(online);
	}

	private void fail(Throwable t)
	{
		broken = true;
		if(taskId != -1)
			Bukkit.getScheduler().cancelTask(taskId);
		taskId = -1;
		COMZombies.getPlugin().getLogger().log(Level.WARNING, "Accessibility glow stopped working", t);
	}

	private void sendFlags(Player viewer, Player target, boolean glow) throws Exception
	{
		byte flags = 0;
		if(target.getFireTicks() > 0)
			flags |= 0x01;
		if(target.isSneaking())
			flags |= 0x02;
		if(target.isSprinting())
			flags |= 0x08;
		if(target.isSwimming())
			flags |= 0x10;
		if(target.isInvisible())
			flags |= 0x20;
		if(glow || target.isGlowing())
			flags |= 0x40;
		if(target.isGliding())
			flags |= (byte) 0x80;
		Object value = dataValueCreate.invoke(null, flagsAccessor, flags);
		sendPacket(viewer, setDataCtor.newInstance(target.getEntityId(), List.of(value)));
	}

	private void sendTeam(Player viewer, Player target, String color) throws Exception
	{
		// One team per colour; the name is short and fixed so re-sending replaces it
		Object team = teamCtor.newInstance(scoreboardCtor.newInstance(), "mzglow_" + color);
		Object format = chatFormattingByName.invoke(null, color.toLowerCase(Locale.ROOT));
		if(format != null)
			setColor.invoke(team, format);
		String prefix = prefix(target);
		if(!prefix.isEmpty())
			setPrefix.invoke(team, asVanilla.invoke(null, LEGACY.deserialize(prefix)));
		sendPacket(viewer, createTeamPacket.invoke(null, team, true));
		sendPacket(viewer, createPlayerPacket.invoke(null, team, target.getName(), actionAdd));
	}

	private void sendPacket(Player viewer, Object packet) throws Exception
	{
		send.invoke(connection.get(getHandle.invoke(viewer)), packet);
	}

	private static String prefix(Player player)
	{
		try
		{
			if(Bukkit.getPluginManager().getPlugin("Vault") == null)
				return "";
			RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> rsp = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
			String p = rsp == null ? null : rsp.getProvider().getPlayerPrefix(player);
			return p == null ? "" : p.replace('§', '&');
		} catch(Throwable t)
		{
			return "";
		}
	}
}
