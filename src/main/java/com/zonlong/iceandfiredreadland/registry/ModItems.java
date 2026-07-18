package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(IceAndFireDreadLand.MODID);

    public static <T extends Item> DeferredItem<T> registerBlock(String name, Supplier<T> item) {
        DeferredItem<T> r = register(name, item);
        ModCreativeModeTabs.BLOCKS_LIST.add(r);
        return r;
    }

    static <T extends Item> DeferredItem<T> register(String name, Supplier<T> item) {
        return REGISTRY.register(name, item);
    }
}
