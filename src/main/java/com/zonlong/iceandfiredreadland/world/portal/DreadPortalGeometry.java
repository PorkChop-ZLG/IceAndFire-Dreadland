package com.zonlong.iceandfiredreadland.world.portal;

import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * 传送门几何辅助。
 *
 * <p>放在公共端（而非客户端渲染器内），使面剔除的几何判断也能在服务端的 GameTest 中直接验证，
 * 不需要启动客户端。
 */
public final class DreadPortalGeometry {

    private DreadPortalGeometry() {
    }

    /**
     * 一个门方块需要提交的四边形数量。
     *
     * <p>与相邻门块共享的面不可见，因此剔除；其余面保留。返回 0–6。
     * 这是剔除效果的权威定义，渲染器与测试都以此为准。
     */
    public static int submittedFaceCount(BlockGetter level, BlockPos pos) {
        int faces = 0;
        for (Direction direction : Direction.values()) {
            if (!isPortal(level, pos, direction)) {
                faces++;
            }
        }
        return faces;
    }

    /** 未做任何剔除时的面数，恒为 6；用于对照说明剔除比例。 */
    public static int unclippedFaceCount() {
        return Direction.values().length;
    }

    /** 相邻方块是否同为悚域传送门。 */
    public static boolean isPortal(BlockGetter level, BlockPos pos, Direction direction) {
        if (level == null) {
            return false;
        }
        return level.getBlockState(pos.relative(direction)).is(ModBlocks.DREAD_PORTAL.get());
    }
}
