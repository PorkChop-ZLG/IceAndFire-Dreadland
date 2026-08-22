package com.zonlong.iceandfiredreadland.item;

import com.zonlong.iceandfiredreadland.block.DreadQueenPortraitBlock;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;

public class DreadQueenPortraitItem extends Item {
    public DreadQueenPortraitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction facing = context.getClickedFace();
        if (facing.getAxis().isVertical()) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos center = clickedPos.relative(facing);
        Direction right = facing.getCounterClockWise();
        BlockPos master = center.relative(right, -1).above(1);

        if (player != null && !player.mayUseItemAt(center, facing, stack)) {
            return InteractionResult.FAIL;
        }

        for (int x = 0; x < DreadQueenPortraitBlock.SIZE; x++) {
            for (int y = 0; y < DreadQueenPortraitBlock.SIZE; y++) {
                BlockPos p = master.relative(right, x).below(y);
                BlockState current = level.getBlockState(p);
                if (!current.canBeReplaced()) {
                    return InteractionResult.FAIL;
                }
            }
        }

        if (!level.isClientSide) {
            BlockState base = ModBlocks.DREAD_QUEEN_PORTRAIT.get().defaultBlockState()
                    .setValue(DreadQueenPortraitBlock.FACING, facing);
            for (int x = 0; x < DreadQueenPortraitBlock.SIZE; x++) {
                for (int y = 0; y < DreadQueenPortraitBlock.SIZE; y++) {
                    BlockPos p = master.relative(right, x).below(y);
                    boolean waterlogged = level.getBlockState(p).getFluidState().is(Fluids.WATER);
                    level.setBlock(p, base
                            .setValue(DreadQueenPortraitBlock.X_OFFSET, x)
                            .setValue(DreadQueenPortraitBlock.Y_OFFSET, y)
                            .setValue(DreadQueenPortraitBlock.WATERLOGGED, waterlogged), Block.UPDATE_CLIENTS);
                }
            }
            level.gameEvent(GameEvent.BLOCK_PLACE, center, GameEvent.Context.of(player, base));
            level.updateNeighborsAt(center, ModBlocks.DREAD_QUEEN_PORTRAIT.get());
            level.playSound(player, center, SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            stack.consume(1, player);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
