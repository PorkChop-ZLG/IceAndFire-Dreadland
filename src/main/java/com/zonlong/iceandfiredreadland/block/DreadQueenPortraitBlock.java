package com.zonlong.iceandfiredreadland.block;

import com.mojang.serialization.MapCodec;
import com.zonlong.iceandfiredreadland.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class DreadQueenPortraitBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final int SIZE = 3;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X_OFFSET = IntegerProperty.create("x_offset", 0, SIZE - 1);
    public static final IntegerProperty Y_OFFSET = IntegerProperty.create("y_offset", 0, SIZE - 1);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final MapCodec<DreadQueenPortraitBlock> CODEC = simpleCodec(s -> new DreadQueenPortraitBlock());
    private static final SoundType PAINTING_SOUND_TYPE = new SoundType(
            1.0F, 1.0F,
            SoundEvents.PAINTING_BREAK,
            SoundEvents.WOOL_STEP,
            SoundEvents.PAINTING_PLACE,
            SoundEvents.WOOL_HIT,
            SoundEvents.WOOL_FALL
    );
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    private static final VoxelShape SHAPE_NORTH = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_WEST = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public DreadQueenPortraitBlock() {
        super(Properties.of().sound(PAINTING_SOUND_TYPE).noOcclusion().instabreak().noCollission().pushReaction(PushReaction.DESTROY));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(X_OFFSET, 0)
                .setValue(Y_OFFSET, 0)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, X_OFFSET, Y_OFFSET, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_SOUTH;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos masterPos = getMasterPos(state, pos);
            Direction facing = state.getValue(FACING);
            Direction right = facing.getCounterClockWise();
            if (level.getBlockState(masterPos).is(this)) {
                level.removeBlock(masterPos, false);
            }
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    BlockPos p = masterPos.relative(right, x).below(y);
                    if (level.getBlockState(p).is(this)) {
                        level.removeBlock(p, false);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (isMaster(state)) {
            return List.of(new ItemStack(ModItems.DREAD_QUEEN_PORTRAIT.get()));
        }
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.DREAD_QUEEN_PORTRAIT.get());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        Direction facing = state.getValue(FACING);
        Direction right = facing.getCounterClockWise();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                BlockPos p = masterPos.relative(right, x).below(y);
                BlockState b = level.getBlockState(p);
                if (!b.is(this) || b.getValue(FACING) != facing ||
                        b.getValue(X_OFFSET) != x || b.getValue(Y_OFFSET) != y) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!canSurvive(state, level, pos)) {
            BlockPos masterPos = getMasterPos(state, pos);
            Direction facing = state.getValue(FACING);
            Direction right = facing.getCounterClockWise();
            if (level.getBlockState(masterPos).is(this)) {
                level.destroyBlock(masterPos, true);
            }
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    BlockPos p = masterPos.relative(right, x).below(y);
                    if (level.getBlockState(p).is(this)) {
                        level.removeBlock(p, false);
                    }
                }
            }
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction direction) {
        if (adjacentBlockState.is(this) && adjacentBlockState.getValue(FACING) == state.getValue(FACING)) return true;
        return state.getValue(FACING) == direction || super.skipRendering(state, adjacentBlockState, direction);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    public static boolean isMaster(BlockState state) {
        return state.getValue(X_OFFSET) == 0 && state.getValue(Y_OFFSET) == 0;
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        int x = state.getValue(X_OFFSET);
        int y = state.getValue(Y_OFFSET);
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getClockWise(), x).above(y);
    }
}
