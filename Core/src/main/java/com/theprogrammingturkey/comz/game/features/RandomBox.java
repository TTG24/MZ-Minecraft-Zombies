package com.theprogrammingturkey.comz.game.features;

import java.util.Collections;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.signs.SignText;
import com.theprogrammingturkey.comz.game.weapons.Weapon;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class RandomBox
{
	private int activeTask = -1;
	private int teddyVelocityTask = -1;
	private final Location boxLoc;
	private final BlockFace facing;
	private Location chestLocation = null;
	private final Game boxGame;
	private final String boxId;
	private final int boxCost;

	private Player openedBy;

	private boolean running;
	private boolean gunSelected;
	private boolean isTeddyBear;
	private Weapon weapon;
	private Item item;
	private ArmorStand namePlate;


	public RandomBox(Location loc, BlockFace facing, Game game, String boxId, int cost)
	{
		boxLoc = loc;
		this.facing = facing;
		boxGame = game;
		this.boxId = boxId;
		boxCost = cost;
		this.running = false;
		this.gunSelected = false;
		this.isTeddyBear = false;
	}

	public void Start(Player player, int pointsNeeded)
	{
		if(boxGame == null)
			return;

		if(!(GameManager.INSTANCE.isPlayerInGame(player)))
			return;

		if(!PointManager.INSTANCE.canBuy(player, pointsNeeded))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You don't have enough points!");
			return;
		}

		chestLocation = null;
		for(BlockFace face : BlockFace.values())
		{
			if(boxLoc.clone().add(face.getModX(), face.getModY(), face.getModZ()).getBlock().getType().equals(Material.CHEST))
			{
				chestLocation = boxLoc.clone().add(face.getModX(), face.getModY(), face.getModZ());
				break;
			}
		}

		openedBy = player;

		if(chestLocation != null)
			COMZombies.nmsUtil.playChestAction(chestLocation, true);

		running = true;
		weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
		Location itemLoc;

		if(chestLocation != null)
			itemLoc = chestLocation.clone().add(.5, 1, .5);
		else
			itemLoc = boxLoc.clone().add(.5, .2, .5);

		item = player.getWorld().dropItem(itemLoc, weapon.getStack());
		namePlate = (ArmorStand) player.getWorld().spawnEntity(itemLoc.clone().add(0, -1.7, 0), EntityType.ARMOR_STAND);
		namePlate.setVisible(false);
		namePlate.setGravity(false);
		namePlate.setAI(false);
		namePlate.customName(Colors.legacy(weapon.getName()));
		namePlate.setCustomNameVisible(true);

		PointManager.INSTANCE.takePoints(player, pointsNeeded);
		PointManager.INSTANCE.notifyPlayer(player);

		int taskID = COMZombies.scheduleTask(0, 10, new Runnable()
		{
			int time = 10;

			@Override
			public void run()
			{
				item.setTicksLived(5960);
				item.setPickupDelay(1000);
				item.setVelocity(new Vector(0, 0, 0));
				if(boxGame.getStatus() == Game.GameStatus.INGAME)
				{
					if(time > 0)
					{
						weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
						item.setItemStack(weapon.getStack());
						namePlate.customName(Colors.legacy(weapon.getName()));
						player.getWorld().playSound(boxLoc, Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 1f);
					}
					else if(time == 0)
					{
						if(!boxGame.isFireSale() && !boxGame.boxManager.isMultiBox() && boxGame.getTeddyBearPercent() != 0 && COMZombies.rand.nextInt(boxGame.getTeddyBearPercent()) == 0)
						{
							CommandUtil.sendMessageToPlayer(player, Colors.RED + "Teddy Bear!!!!!!");
							item.setItemStack(new ItemStack(Material.TOTEM_OF_UNDYING));
							namePlate.customName(Component.empty());
							namePlate.setCustomNameVisible(false);
							item.setGravity(false);
							item.customName(Colors.legacy("Teddy Bear"));
							item.setCustomNameVisible(true);
							isTeddyBear = true;

							boxGame.scheduleMatchTask(40, () ->
							{
								teddyVelocityTask = COMZombies.scheduleTask(0, 5, () -> item.setVelocity(new Vector(0, 0.1, 0)));

								boxGame.scheduleMatchTask(60, () ->
								{
									boxGame.boxManager.teddyBear();
									PointManager.INSTANCE.addPoints(player, pointsNeeded);
									reset();
								});
							});
						}
						else
						{
							player.getWorld().playSound(boxLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
							gunSelected = true;
						}
					}
					else if(time == -20)
					{
						reset();
					}
					else if(!isTeddyBear)
					{
							namePlate.customName(Colors.legacy(weapon.getName() + " (" + (20 + time) + ")"));
					}
					time--;
				}
			}
		});

		activeTask = taskID;
	}

	public boolean canActivate()
	{
		return !this.running;
	}

	public boolean canPickWeapon(Player player)
	{
		return player.equals(openedBy) && this.gunSelected;
	}

	public void pickUpWeapon(Player player)
	{
		boxGame.getPlayersWeapons(player).addWeapon(weapon);
		player.getLocation().getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
		reset();
	}

	public void reset()
	{
		if(teddyVelocityTask != -1) Bukkit.getScheduler().cancelTask(teddyVelocityTask);
		teddyVelocityTask = -1;
		gunSelected = false;
		isTeddyBear = false;
		if(item != null)
			item.remove();
		if(namePlate != null)
			namePlate.remove();
		if(chestLocation != null)
			COMZombies.nmsUtil.playChestAction(chestLocation, false);
		if(activeTask != -1)
		{
			Bukkit.getScheduler().cancelTask(activeTask);
			activeTask = -1;
		}
		running = false;

		if(!boxGame.boxManager.isMultiBox() && !boxGame.isFireSale() && boxGame.boxManager.getCurrentbox() != this)
			this.removeBox();
	}

	public void loadBox()
	{
		if(boxLoc == null)
		{
			Bukkit.getServer().broadcast(Colors.legacy("Mystery box " + this.getId() + " is broken and has no location!"));
			return;
		}

		updateSign();
	}

	public void removeBox()
	{
		if(!this.running)
			BlockUtils.setBlockToAir(boxLoc);
	}

	public Location getLocation()
	{
		return boxLoc;
	}

	public BlockFace getFacing()
	{
		return facing;
	}

	public String getId()
	{
		return boxId;
	}


	public int getCost()
	{
		return boxGame.isFireSale() ? 10 : boxCost;
	}

	public void updateSign()
	{
		Block block = boxLoc.getBlock();
		block.setType(Material.OAK_WALL_SIGN);
		BlockData blockData = block.getBlockData();
		((Directional) blockData).setFacing(facing);
		block.setBlockData(blockData);
		SignText.apply((Sign) block.getState(), SignText.render("mystery_box", Collections.singletonMap("price", String.valueOf(getCost()))));
	}
}
