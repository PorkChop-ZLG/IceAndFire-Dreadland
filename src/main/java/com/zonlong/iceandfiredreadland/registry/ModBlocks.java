package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.block.DreadPortalBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(IceAndFireDreadLand.MODID);

    public static final DeferredBlock<Block> DREAD_PORTAL = register("dread_portal", DreadPortalBlock::new);

    public static <T extends Block> DeferredBlock<T> register(String name, Supplier<T> block) {
        DeferredBlock<T> r = REGISTRY.register(name, block);
        ModItems.registerBlock(name, () -> new BlockItem(r.get(), new Item.Properties()));
        return r;
    }
}
