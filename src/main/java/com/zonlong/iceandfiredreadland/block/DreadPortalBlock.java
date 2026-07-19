package com.zonlong.iceandfiredreadland.block;

import com.mojang.serialization.MapCodec;
import com.zonlong.iceandfiredreadland.block.entity.DreadPortalBlockEntity;
import com.zonlong.iceandfiredreadland.registry.ModParticles;
import com.zonlong.iceandfiredreadland.util.DreadBlock;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class DreadPortalBlock extends BaseEntityBlock implements DreadBlock {
    private static final MapCodec<? extends BaseEntityBlock> CODEC = simpleCodec(s -> new DreadPortalBlock());
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty FRAME_BOUND = BooleanProperty.create("frame_bound");
    private static final VoxelShape X_AXIS_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_AXIS_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public DreadPortalBlock() {
        super(Properties.of()
                .mapColor(MapColor.NONE)
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion()
                .dynamicShape()
                .strength(-1, 100000)
                .lightLevel((state) -> 1)
                .randomTicks());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(FRAME_BOUND, false));
    }

    @Override
    protected @NotNull VoxelShape getShape(
            @NotNull BlockState state,
            @NotNull BlockGetter level,
            @NotNull BlockPos pos,
            @NotNull CollisionContext context
    ) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_SHAPE : X_AXIS_SHAPE;
    }

    @Override
    public void animateTick(@NotNull BlockState state, Level world, @NotNull BlockPos pos, @NotNull RandomSource rand) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof DreadPortalBlockEntity) {
            int i = 3;
            for (int j = 0; j < i; ++j) {
                double d0 = (float) pos.getX() + rand.nextFloat();
                double d1 = (float) pos.getY() + rand.nextFloat();
                double d2 = (float) pos.getZ() + rand.nextFloat();
                double d3 = ((double) rand.nextFloat() - 0.5D) * 0.25D;
                double d4 = ((double) rand.nextFloat()) * -0.25D;
                double d5 = ((double) rand.nextFloat() - 0.5D) * 0.25D;
                int k = rand.nextInt(2) * 2 - 1;
                world.addParticle(ModParticles.DREAD_PORTAL.get(), d0, d1, d2, d3, d4, d5);
            }
        }
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.box(0, 0, 0, 0, 0, 0);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DreadPortalBlockEntity(pos, state);
    }

    @Override
    protected @NotNull BlockState updateShape(
            @NotNull BlockState state,
            @NotNull Direction direction,
            @NotNull BlockState neighborState,
            @NotNull LevelAccessor level,
            @NotNull BlockPos currentPos,
            @NotNull BlockPos neighborPos
    ) {
        if (!state.getValue(FRAME_BOUND)) {
            return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
        }

        Direction.Axis neighborAxis = direction.getAxis();
        Direction.Axis portalAxis = state.getValue(AXIS);
        boolean updateOutsidePortalPlane = portalAxis != neighborAxis && neighborAxis.isHorizontal();
        if (!updateOutsidePortalPlane
                && !neighborState.is(this)
                && !new DreadPortalShape(level, currentPos, portalAxis).isComplete()) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(
                    AXIS,
                    state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X
            );
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FRAME_BOUND);
    }
}
