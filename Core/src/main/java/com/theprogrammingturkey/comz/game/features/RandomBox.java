package com.theprogrammingturkey.comz.game.features;

import java.util.Collections;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
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
	/** Bumped by reset(): a roll step from an earlier spin sees a different value and stops. */
	private int rollToken = 0;
	/**
	 * Ticks between gun swaps while the box rolls: very fast at first, slowing down to the final pick (about 5.3 s in all,
	 * like the old even roll).
	 */
	private static final int[] ROLL_DELAYS = {1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 12, 14};
	private final Location boxLoc;
	private final BlockFace facing;
	private Location chestLocation = null;
	private final Game boxGame;
	private final String boxId;
	private final int boxCost;
	/** A box that is a chest (single or double) instead of a sign: it stays in place and is clicked or used with F directly. */
	private final boolean chestBox;
	private Location partnerLocation = null;

	private Player openedBy;

	private boolean running;
	private boolean gunSelected;
	private boolean isTeddyBear;
	private Weapon weapon;
	/** The gun shown in the box: a display entity, so nothing pushes it about (a dropped item got shoved out of the chest). */
	private ItemDisplay item;
	private ArmorStand namePlate;


	public RandomBox(Location loc, BlockFace facing, Game game, String boxId, int cost)
	{
		this(loc, facing, game, boxId, cost, false);
	}

	public RandomBox(Location loc, BlockFace facing, Game game, String boxId, int cost, boolean chestBox)
	{
		this.chestBox = chestBox;
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
		partnerLocation = null;
		if(chestBox)
		{
			chestLocation = boxLoc;
			Block other = BlockUtils.doubleChestPartner(boxLoc.getBlock());
			if(other != null)
				partnerLocation = other.getLocation();
		}
		else for(BlockFace face : BlockFace.values())
		{
			if(boxLoc.clone().add(face.getModX(), face.getModY(), face.getModZ()).getBlock().getType().equals(Material.CHEST))
			{
				chestLocation = boxLoc.clone().add(face.getModX(), face.getModY(), face.getModZ());
				// A sign box beside a double chest uses the middle of both halves too
				Block other = BlockUtils.doubleChestPartner(chestLocation.getBlock());
				if(other != null)
					partnerLocation = other.getLocation();
				break;
			}
		}

		openedBy = player;

		if(chestLocation != null)
			COMZombies.nmsUtil.playChestAction(chestLocation, true);
		if(partnerLocation != null)
			COMZombies.nmsUtil.playChestAction(partnerLocation, true);

		running = true;
		weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
		Location itemLoc;

		if(chestLocation != null && partnerLocation != null)
			itemLoc = chestLocation.clone().add(partnerLocation).multiply(0.5).add(.5, 1, .5);
		else if(chestLocation != null)
			itemLoc = chestLocation.clone().add(.5, 1, .5);
		else
			itemLoc = boxLoc.clone().add(.5, .2, .5);

		// Lying along the box (a double chest: from one half to the other), a little above it
		float yaw = 0;
		if(chestLocation != null && partnerLocation != null)
		{
			Vector along = partnerLocation.toVector().subtract(chestLocation.toVector());
			yaw = Math.abs(along.getX()) > Math.abs(along.getZ()) ? 90 : 0;
		}
		itemLoc.setYaw(yaw);
		itemLoc.setPitch(0);
		itemLoc.add(0, -0.05, 0);
		item = player.getWorld().spawn(itemLoc, ItemDisplay.class, d ->
		{
			d.setItemStack(weapon.getStack());
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
			d.setBillboard(Display.Billboard.FIXED);
			d.setTeleportDuration(5);
			d.setPersistent(false);
		});
		final Location center = itemLoc.clone();
		namePlate = (ArmorStand) player.getWorld().spawnEntity(itemLoc.clone().add(0, -1.7, 0), EntityType.ARMOR_STAND);
		namePlate.setVisible(false);
		namePlate.setGravity(false);
		namePlate.setAI(false);
		namePlate.customName(Colors.legacy(weapon.getName()));
		namePlate.setCustomNameVisible(true);

		PointManager.INSTANCE.takePoints(player, pointsNeeded);
		PointManager.INSTANCE.notifyPlayer(player);
		// The box opening sound, for everyone near it
		boxLoc.getWorld().playSound(boxLoc, "mz:mysterybox", org.bukkit.SoundCategory.PLAYERS, 1f, 1f);

		// After the roll: the pick (or teddy bear) and the pick-up countdown, every 10 ticks, from time 0
		Runnable afterRoll = () -> activeTask = COMZombies.scheduleTask(0, 10, new Runnable()
		{
			int time = 0;

			@Override
			public void run()
			{
				// The match ended mid-spin: put the box back (this task used to keep running, doing nothing)
				if(boxGame.getStatus() != Game.GameStatus.INGAME)
				{
					reset();
					return;
				}
				// Back to the middle, unless the teddy bear is floating away
				if(!isTeddyBear)
				{
					if(item.getLocation().distanceSquared(center) > 0.0025)
						item.teleport(center);
				}
				if(boxGame.getStatus() == Game.GameStatus.INGAME)
				{
					if(time > 0)
					{
						weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
						item.setItemStack(weapon.getStack());
						namePlate.customName(Colors.legacy(weapon.getName()));
					}
					else if(time == 0)
					{
						if(boxGame.boxManager.rollTeddyBear())
						{
							CommandUtil.sendMessageToPlayer(player, Colors.RED + "Teddy Bear!!!!!!");
							// the bear, turned to whoever looks at it, and the box's move music (heard all round)
							item.setItemStack(teddyBear());
							item.setBillboard(Display.Billboard.VERTICAL);
							boxLoc.getWorld().playSound(boxLoc, "mz:mysterybox_move", org.bukkit.SoundCategory.PLAYERS, 1f, 1f);
							namePlate.customName(Component.empty());
							namePlate.setCustomNameVisible(false);
							namePlate.customName(Colors.legacy("Teddy Bear"));
							namePlate.setCustomNameVisible(true);
							isTeddyBear = true;

							boxGame.scheduleMatchTask(40, () ->
							{
								teddyVelocityTask = COMZombies.scheduleTask(0, 5, () ->
								{
									// floats up out of the box (and its name with it)
									item.teleport(item.getLocation().add(0, 0.25, 0));
									namePlate.teleport(namePlate.getLocation().add(0, 0.25, 0));
								});

								boxGame.scheduleMatchTask(60, () ->
								{
									boxGame.boxManager.teddyBear();
									// Refund only to a player still in this match (one who left would get a stray points entry)
									if(boxGame.getPlayersInGame().contains(player))
										PointManager.INSTANCE.addPoints(player, pointsNeeded);
									reset();
								});
							});
						}
						else
						{
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

		// The roll: a new random gun at each step, the steps further and further apart
		final int token = ++rollToken;
		Runnable[] step = new Runnable[1];
		int[] index = {0};
		step[0] = () ->
		{
			if(token != rollToken || !running)
				return;
			if(boxGame.getStatus() != Game.GameStatus.INGAME)
			{
				reset();
				return;
			}
			if(index[0] >= ROLL_DELAYS.length)
			{
				afterRoll.run();
				return;
			}
			weapon = WeaponManager.getRandomWeapon(false, boxGame.getPlayersWeapons(player));
			item.setItemStack(weapon.getStack());
			namePlate.customName(Colors.legacy(weapon.getName()));
			if(item.getLocation().distanceSquared(center) > 0.0025)
				item.teleport(center);
			activeTask = COMZombies.scheduleTask(ROLL_DELAYS[index[0]++], step[0]);
		};
		activeTask = COMZombies.scheduleTask(ROLL_DELAYS[0], step[0]);
	}

	/** The teddy bear (its model in the guns pack; a totem without the pack). */
	private static ItemStack teddyBear()
	{
		ItemStack stack = new ItemStack(Material.TOTEM_OF_UNDYING);
		org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
		meta.setItemModel(new org.bukkit.NamespacedKey("mzguns", "teddy_bear"));
		stack.setItemMeta(meta);
		return stack;
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
		int slot = boxGame.getPlayersWeapons(player).getCorrectSlot(weapon);
		boxGame.getPlayersWeapons(player).addWeapon(weapon);
		com.theprogrammingturkey.comz.util.GunAnimMessages.pickedUp(player, slot);
		com.theprogrammingturkey.comz.util.MzSounds.purchase(player);
		reset();
	}

	public void reset()
	{
		rollToken++;
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
		if(partnerLocation != null)
			COMZombies.nmsUtil.playChestAction(partnerLocation, false);
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
		// A chest box stays where it is; only the box that is active sells rolls
		if(chestBox)
			return;
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

	public boolean isChestBox()
	{
		return chestBox;
	}

	/** Whether the location is this box: its block, or for a chest box, either half of the double chest. */
	public boolean isAt(Location loc)
	{
		if(boxLoc.equals(loc))
			return true;
		if(!chestBox || loc.getWorld() == null || !loc.getWorld().equals(boxLoc.getWorld()))
			return false;
		Block other = BlockUtils.doubleChestPartner(boxLoc.getBlock());
		return other != null && other.getLocation().equals(loc);
	}

	public void updateSign()
	{
		if(chestBox)
			return;
		Block block = boxLoc.getBlock();
		// Mystery box signs hidden for this arena (/z signs hide mystery_box): no sign, the box is used with the interact key
		if(boxGame != null && com.theprogrammingturkey.comz.game.signs.SignStyles.isHidden(boxGame, "mystery_box"))
		{
			if(com.theprogrammingturkey.comz.util.BlockUtils.isSign(block))
				block.setType(Material.AIR, false);
			return;
		}
		block.setType(boxGame == null ? Material.OAK_WALL_SIGN : com.theprogrammingturkey.comz.game.signs.SignStyles.wallSign(boxGame, "mystery_box"));
		BlockData blockData = block.getBlockData();
		((Directional) blockData).setFacing(facing);
		block.setBlockData(blockData);
		SignText.apply((Sign) block.getState(), SignText.render("mystery_box", Collections.singletonMap("price", String.valueOf(getCost()))));
	}
}
