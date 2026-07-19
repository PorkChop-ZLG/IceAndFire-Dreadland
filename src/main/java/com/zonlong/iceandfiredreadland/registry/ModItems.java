package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(IceAndFireDreadLand.MODID);

    public static final DeferredItem<BlockItem> DREAD_PORTAL_ITEM = REGISTRY.registerSimpleBlockItem(ModBlocks.DREAD_PORTAL);

    public static final DeferredItem<Item> FIRELAND_KEY = REGISTRY.registerSimpleItem("fireland_key");
    public static final DeferredItem<Item> ICELAND_KEY = REGISTRY.registerSimpleItem("iceland_key");
    public static final DeferredItem<Item> LIGHTNING_KEY = REGISTRY.registerSimpleItem("lightning_key");
    public static final DeferredItem<Item> DREADLAND_KEY = REGISTRY.registerSimpleItem("dreadland_key");
}
