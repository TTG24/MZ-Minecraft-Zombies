package com.theprogrammingturkey.comz.game.features;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.meta.FireworkMeta;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.WeaponInstance;
import com.theprogrammingturkey.comz.leaderboards.Leaderboard;
import com.theprogrammingturkey.comz.leaderboards.PlayerStats;
import com.theprogrammingturkey.comz.util.Colors;

public class DownedPlayer implements Listener
{
	private final Player player;
	private Player reviver;
	private final Game game;
	private boolean isPlayerDown;
	private boolean isBeingRevived = false;
	private int downTime = 0;

	private final WeaponInstance[] guns = new WeaponInstance[2];

	private int fireWorksTask = -1;
	private int heartbeatTask = -1;
	private int loopTask = -1;
	private int loopStartTask = -1;
	private int reviveTask = -1;
	private int reviveWatchTask = -1;

	public DownedPlayer(Player player, Game game)
	{
		this.player = player;
		this.game = game;
	}

	public void setPlayerDown()
	{
		isPlayerDown = true;
		PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
		stats.setDowns(stats.getDowns() + 1);
		player.sendMessage(Colors.RED + "You have gone down and need to be revived!");
		game.perkManager.clearPlayersPerks(player);
		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		guns[0] = manager.removeWeapon(1);
		guns[1] = manager.removeWeapon(2);
		manager.removeWeapon(3);
		manager.addWeapon(WeaponManager.getGun(game.getStartingGun()).getNewInstance(player, 1));
		player.setInvulnerable(true);
		// Crawl speed while downed (vanilla walking is 0.2). Configurable: config.ReviveSettings.DownedWalkSpeed.
		float crawl = (float) Math.max(0.0, Math.min(0.2, COMZombies.getPlugin().getConfig().getDouble("config.ReviveSettings.DownedWalkSpeed", 0.1)));
		player.setWalkSpeed(crawl);
		scheduleTask();
		startDownedAudio();
	}

	// Last stand: a sting, then a heartbeat loop at once and the last-stand loop once the sting has finished; both
	// repeat until the player is revived or dies. Minecraft cannot loop a sound, so each is replayed at its own length
	// (heartbeat 1.54 s, last-stand loop 17.32 s). The Tactical Movement plugin reads the "mz_downed" marker to switch its
	// low-health sounds off and show the grey screen.
	private void publishDownTime()
	{
		int max = COMZombies.getPlugin().getConfig().getInt("config.ReviveSettings.MaxDownTime");
		player.setMetadata("mz_downed_left", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), Math.max(0, max - downTime)));
		player.setMetadata("mz_downed_max", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), Math.max(1, max)));
	}

	private void startDownedAudio()
	{
		player.setMetadata("mz_downed", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), true));
		publishDownTime();
		playToPlayer("last_stand_sting");
		heartbeatTask = COMZombies.scheduleTask(0, 31, () -> playToPlayer("downed_heartbeat"));
		loopStartTask = COMZombies.scheduleTask(73, () ->
			loopTask = COMZombies.scheduleTask(0, 346, () -> playToPlayer("laststand_loop")));
	}

	private void stopDownedAudio()
	{
		player.removeMetadata("mz_revive_start", COMZombies.getPlugin());
		player.removeMetadata("mz_revive_ms", COMZombies.getPlugin());
		player.removeMetadata("mz_downed", COMZombies.getPlugin());
		player.removeMetadata("mz_downed_left", COMZombies.getPlugin());
		player.removeMetadata("mz_downed_max", COMZombies.getPlugin());
		Bukkit.getScheduler().cancelTask(heartbeatTask);
		Bukkit.getScheduler().cancelTask(loopTask);
		Bukkit.getScheduler().cancelTask(loopStartTask);
		heartbeatTask = loopTask = loopStartTask = -1;
		for(String name : new String[]{"last_stand_sting", "downed_heartbeat", "laststand_loop"})
			player.stopSound("mz:" + name, org.bukkit.SoundCategory.MASTER);
	}

	private void playToPlayer(String name)
	{
		if(player.isOnline())
			player.playSound(player.getLocation(), "mz:" + name, org.bukkit.SoundCategory.MASTER, 1.0F, 1.0F);
	}

	public void clearDownedState()
	{
		isPlayerDown = false;
		isBeingRevived = false;
		Bukkit.getScheduler().cancelTask(reviveWatchTask);
		Bukkit.getScheduler().cancelTask(fireWorksTask);
		stopDownedAudio();
		player.setGameMode(com.theprogrammingturkey.comz.game.Game.playGameMode());
		player.setInvulnerable(false);
		player.setWalkSpeed(0.2F);
		player.setHealth(20);
		reviver = null;
	}

	public void revivePlayer()
	{
		Bukkit.getScheduler().cancelTask(reviveWatchTask);
		player.sendMessage(Colors.DARK_GREEN + "You have been revived!");
		if(reviver != null)
		{
			reviver.sendMessage(Colors.GRAY + "You revived " + Colors.RED + player.getName());
			PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(reviver);
			stats.setRevives(stats.getRevives() + 1);
			game.recordRevive(reviver);
		}
		clearDownedState();
		// Fully revived: the get-up sound (it fades out inside the file), heard only by the revived player.
		playToPlayer("get_up");
		game.downedPlayerManager.downedPlayerRevived(this);
		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		manager.removeWeapon(1);
		manager.addWeapon(guns[0]);
		manager.addWeapon(guns[1]);

		if(reviver != null)
			PointManager.INSTANCE.addPoints(reviver, 10);
	}

	public void cancelRevive()
	{
		player.removeMetadata("mz_revive_start", COMZombies.getPlugin());
		player.removeMetadata("mz_revive_ms", COMZombies.getPlugin());
		this.isBeingRevived = false;
		this.reviver = null;
		Bukkit.getScheduler().cancelTask(reviveTask);
		Bukkit.getScheduler().cancelTask(reviveWatchTask);
		reviveWatchTask = -1;
	}

	public void startRevive(Player reviver)
	{
		this.isBeingRevived = true;
		this.reviver = reviver;
		int reviveTime = ConfigManager.getMainConfig().reviveTimer * 20;
		if (game.perkManager.hasPerk(reviver, PerkType.QUICK_REVIVE))
			reviveTime /= 5;
		reviveTask = COMZombies.scheduleTask(reviveTime, this::revivePlayer);
		player.setMetadata("mz_revive_start", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), System.currentTimeMillis()));
		player.setMetadata("mz_revive_ms", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), reviveTime * 50L));
		// The reviver may move around, but must stay within the revive range of the downed player until it finishes.
		reviveWatchTask = COMZombies.scheduleTask(2, 4, this::checkReviveRange);
	}

	private void checkReviveRange()
	{
		Player current = reviver;
		if(!isBeingRevived || current == null)
		{
			Bukkit.getScheduler().cancelTask(reviveWatchTask);
			return;
		}
		double range = ConfigManager.getMainConfig().reviveRange + 0.5;
		boolean valid = current.isOnline() && current.getWorld().equals(player.getWorld())
				&& !game.downedPlayerManager.isDownedPlayer(current) && game.isPlayerPlaying(current);
		if(valid && current.getLocation().distance(player.getLocation()) <= range)
			return;
		if(current.isOnline())
			current.sendMessage(Colors.RED + "You moved too far away! You are no longer reviving " + player.getName());
		cancelRevive();
	}

	private void scheduleTask()
	{
		fireWorksTask = COMZombies.scheduleTask(0, 20, () ->
		{
			downTime++;
			// Fireworks used to mark a downed player; the Tactical Movement plugin now draws a glow that shifts from green
			// through yellow to red as the time to be revived runs out. It reads these markers.
			publishDownTime();
			player.setHealth(1);
			if(downTime >= COMZombies.getPlugin().getConfig().getInt("config.ReviveSettings.MaxDownTime"))
			{
				player.sendMessage(Colors.RED + "You have died!");
				//game.removePlayer(player);
				clearDownedState();
				game.setDead(player);
				playToPlayer("dead");
				//game.gamePlayers.remove(player);
				PlayerStats stats = Leaderboard.getPlayerStatFromPlayer(player);
				stats.setDeaths(stats.getDeaths() + 1);
				game.recordDeath(player);
			}
		});
	}

	private void displayDown()
	{
		Firework f = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
		FireworkMeta meta = f.getFireworkMeta();
		meta.setPower(1);
		meta.addEffect(getRandomFireworkEffect());
		f.setFireworkMeta(meta);
	}

	private FireworkEffect getRandomFireworkEffect()
	{
		boolean trail = COMZombies.rand.nextBoolean();
		boolean flickr = COMZombies.rand.nextBoolean();

		Color color = Color.fromRGB(COMZombies.rand.nextInt(256), COMZombies.rand.nextInt(256), COMZombies.rand.nextInt(256));
		Type type = Type.values()[COMZombies.rand.nextInt(Type.values().length)];
		return FireworkEffect.builder().trail(trail).flicker(flickr).withColor(color).with(type).build();
	}

	public Player getPlayer()
	{
		return player;
	}

	public boolean isPlayerDown()
	{
		return isPlayerDown;
	}

	public boolean isBeingRevived()
	{
		return isBeingRevived;
	}

	public Player getReviver()
	{
		return reviver;
	}
}