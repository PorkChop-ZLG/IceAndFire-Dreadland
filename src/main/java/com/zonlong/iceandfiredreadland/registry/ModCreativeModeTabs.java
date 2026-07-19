package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IceAndFireDreadLand.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS = REGISTRY.register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + IceAndFireDreadLand.MODID + ".blocks"))
                    .icon(() -> new ItemStack(ModItems.DREAD_PORTAL_ITEM.get()))
                    .displayItems((parameters, output) -> ModItems.REGISTRY.getEntries()
                            .forEach(item -> output.accept(item.get())))
                    .build());
}
