package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IceAndFireDreadLand.MODID);

    public static final List<Holder<Item>> BLOCKS_LIST = new LinkedList<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS = register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + IceAndFireDreadLand.MODID + ".blocks"))
                    .icon(() -> new ItemStack(ModBlocks.DREAD_PORTAL.get()))
                    .displayItems(BLOCKS_LIST)
                    .build());

    private static DeferredHolder<CreativeModeTab, CreativeModeTab> register(String name, Supplier<CreativeModeTab> group) {
        return REGISTRY.register(name, group);
    }
}
