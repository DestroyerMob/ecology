package com.destroyermob.ecology.item;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;

public class WaxGogglesItem extends ArmorItem {
    public WaxGogglesItem() {
        super(ArmorMaterials.LEATHER, Type.HELMET, new Item.Properties().durability(55));
    }
}
