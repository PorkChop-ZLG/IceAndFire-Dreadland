package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.item.DreadlandKeyItem;
import com.zonlong.iceandfiredreadland.item.KeyItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(IceAndFireDreadLand.MODID);

    public static final DeferredItem<Item> DREAD_QUEEN_ICON = REGISTRY.registerItem("dread_queen_icon", Item::new, new Item.Properties());
    public static final DeferredItem<BlockItem> DREAD_PORTAL_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.DREAD_PORTAL);

    public static final DeferredItem<KeyItem> FIRELAND_KEY = REGISTRY.registerItem("fireland_key", KeyItem::new, keyProperties());
    public static final DeferredItem<KeyItem> ICELAND_KEY = REGISTRY.registerItem("iceland_key", KeyItem::new, keyProperties());
    public static final DeferredItem<KeyItem> LIGHTNING_KEY = REGISTRY.registerItem("lightning_key", KeyItem::new, keyProperties());
    public static final DeferredItem<DreadlandKeyItem> DREADLAND_KEY = REGISTRY.registerItem("dreadland_key", DreadlandKeyItem::new, keyProperties());

    private static Item.Properties keyProperties() {
        return new Item.Properties().rarity(Rarity.RARE);
    }
}
