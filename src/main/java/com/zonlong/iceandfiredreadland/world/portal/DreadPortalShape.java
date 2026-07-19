package com.zonlong.iceandfiredreadland.world.portal;

import com.zonlong.iceandfiredreadland.block.DreadPortalBlock;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.registry.tag.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Predicate;

public final class DreadPortalShape {
    private static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private int numPortalBlocks;
    @Nullable
    private BlockPos bottomLeft;
    private int height;
    private final int width;

    public static Optional<DreadPortalShape> findEmptyPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        return findPortalShape(level, pos, shape -> shape.isValid() && shape.numPortalBlocks == 0, axis);
    }

    public static Optional<DreadPortalShape> findPortalShape(
            LevelAccessor level,
            BlockPos pos,
            Predicate<DreadPortalShape> predicate,
            Direction.Axis axis
    ) {
        Optional<DreadPortalShape> shape = Optional.of(new DreadPortalShape(level, pos, axis)).filter(predicate);
        if (shape.isPresent()) {
            return shape;
        }

        Direction.Axis otherAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return Optional.of(new DreadPortalShape(level, pos, otherAxis)).filter(predicate);
    }

    public DreadPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = this.calculateBottomLeft(pos);
        if (this.bottomLeft == null) {
            this.bottomLeft = pos;
            this.width = 1;
            this.height = 1;
        } else {
            this.width = this.calculateWidth();
            if (this.width > 0) {
                this.height = this.calculateHeight();
            }
        }
    }

    @Nullable
    private BlockPos calculateBottomLeft(BlockPos pos) {
        int minimumY = Math.max(this.level.getMinBuildHeight(), pos.getY() - MAX_HEIGHT);

        while (pos.getY() > minimumY && isEmpty(this.level.getBlockState(pos.below()))) {
            pos = pos.below();
        }

        Direction leftDir = this.rightDir.getOpposite();
        int distanceToLeftEdge = this.getDistanceUntilEdgeAboveFrame(pos, leftDir) - 1;
        return distanceToLeftEdge < 0 ? null : pos.relative(leftDir, distanceToLeftEdge);
    }

    private int calculateWidth() {
        int width = this.getDistanceUntilEdgeAboveFrame(this.bottomLeft, this.rightDir);
        return width >= MIN_WIDTH && width <= MAX_WIDTH ? width : 0;
    }

    private int getDistanceUntilEdgeAboveFrame(BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int distance = 0; distance <= MAX_WIDTH; distance++) {
            cursor.set(pos).move(direction, distance);
            BlockState state = this.level.getBlockState(cursor);
            if (!isEmpty(state)) {
                if (isFrame(state)) {
                    return distance;
                }
                break;
            }

            cursor.move(Direction.DOWN);
            if (!isFrame(this.level.getBlockState(cursor))) {
                break;
            }
        }

        return 0;
    }

    private int calculateHeight() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int height = this.getDistanceUntilTop(cursor);
        return height >= MIN_HEIGHT && height <= MAX_HEIGHT && this.hasTopFrame(cursor, height) ? height : 0;
    }

    private boolean hasTopFrame(BlockPos.MutableBlockPos cursor, int distanceToTop) {
        for (int offset = 0; offset < this.width; offset++) {
            cursor.set(this.bottomLeft).move(Direction.UP, distanceToTop).move(this.rightDir, offset);
            if (!isFrame(this.level.getBlockState(cursor))) {
                return false;
            }
        }

        return true;
    }

    private int getDistanceUntilTop(BlockPos.MutableBlockPos cursor) {
        for (int distance = 0; distance < MAX_HEIGHT; distance++) {
            cursor.set(this.bottomLeft).move(Direction.UP, distance).move(this.rightDir, -1);
            if (!isFrame(this.level.getBlockState(cursor))) {
                return distance;
            }

            cursor.set(this.bottomLeft).move(Direction.UP, distance).move(this.rightDir, this.width);
            if (!isFrame(this.level.getBlockState(cursor))) {
                return distance;
            }

            for (int offset = 0; offset < this.width; offset++) {
                cursor.set(this.bottomLeft).move(Direction.UP, distance).move(this.rightDir, offset);
                BlockState state = this.level.getBlockState(cursor);
                if (!isEmpty(state)) {
                    return distance;
                }
                if (state.is(ModBlocks.DREAD_PORTAL.get())) {
                    this.numPortalBlocks++;
                }
            }
        }

        return MAX_HEIGHT;
    }

    private static boolean isFrame(BlockState state) {
        return state.is(ModBlockTags.DREADLAND_PORTAL_FRAME);
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(ModBlocks.DREAD_PORTAL.get());
    }

    public boolean isValid() {
        return this.bottomLeft != null
                && this.width >= MIN_WIDTH
                && this.width <= MAX_WIDTH
                && this.height >= MIN_HEIGHT
                && this.height <= MAX_HEIGHT;
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }

    public void createPortalBlocks() {
        BlockState portalState = ModBlocks.DREAD_PORTAL.get().defaultBlockState()
                .setValue(DreadPortalBlock.AXIS, this.axis)
                .setValue(DreadPortalBlock.FRAME_BOUND, true);
        BlockPos topRight = this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1);
        BlockPos.betweenClosed(this.bottomLeft, topRight)
                .forEach(pos -> this.level.setBlock(pos, portalState, 18));
    }
}
