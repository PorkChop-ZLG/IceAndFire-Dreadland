package com.zonlong.iceandfiredreadland.block.entity;

import com.zonlong.iceandfiredreadland.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DreadPortalBlockEntity extends BlockEntity {
    public DreadPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DREAD_PORTAL.get(), pos, state);
    }
}
