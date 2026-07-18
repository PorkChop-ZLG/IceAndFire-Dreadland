package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.block.entity.DreadPortalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.mojang.datafixers.types.Type;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IceAndFireDreadLand.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DreadPortalBlockEntity>> DREAD_PORTAL =
            REGISTRY.register("dread_portal", () -> BlockEntityType.Builder.of(DreadPortalBlockEntity::new, ModBlocks.DREAD_PORTAL.get()).build((Type<?>) null));
}
