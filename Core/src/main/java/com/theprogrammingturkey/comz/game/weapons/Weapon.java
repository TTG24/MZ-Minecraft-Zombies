package com.theprogrammingturkey.comz.game.weapons;

import java.util.List;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public class Weapon
{
	private final String name;
	private final WeaponType weaponType;

	public int damage;
	public int totalAmmo;

	public Material material;
	public int modelData;
	/** The item model it is drawn with ("mzguns:m1911": a model in the server's resource pack), or null for the material's own. */
	public org.bukkit.NamespacedKey itemModel;

	public Weapon(String name, WeaponType weaponType)
	{
		this.name = name;
		this.weaponType = weaponType;
	}

	public void loadWeapon(JsonObject json)
	{
		this.totalAmmo = CustomConfig.getInt(json, "total_ammo", 1);
		this.damage = CustomConfig.getInt(json, "damage", 1);
		this.material = Material.getMaterial(CustomConfig.getString(json, "material", ""));
		this.modelData = CustomConfig.getInt(json, "model_data", -1);
		String model = CustomConfig.getString(json, "item_model", "");
		this.itemModel = model.isEmpty() ? null : org.bukkit.NamespacedKey.fromString(model);
	}

	public WeaponType getWeaponType()
	{
		return weaponType;
	}

	public String getName()
	{
		return name;
	}

	public ItemStack getStack()
	{
		ItemStack stack = new ItemStack(material == null ? weaponType.getMaterial() : material);
		if(itemModel != null)
		{
			ItemMeta itemMeta = stack.getItemMeta();
			itemMeta.setItemModel(itemModel);
			stack.setItemMeta(itemMeta);
		}
		if(modelData != -1)
		{
			ItemMeta itemMeta = stack.getItemMeta();
			CustomModelDataComponent cmdc = itemMeta.getCustomModelDataComponent();
			cmdc.setFloats(List.of((float) this.modelData));
			itemMeta.setCustomModelDataComponent(cmdc);
			stack.setItemMeta(itemMeta);
		}
		return stack;
	}

	public WeaponInstance getNewInstance(Player player, int slot)
	{
		return new WeaponInstance(this, player, slot);
	}
}
