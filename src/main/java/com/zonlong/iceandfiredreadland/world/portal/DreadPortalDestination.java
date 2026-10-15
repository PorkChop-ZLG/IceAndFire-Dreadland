package com.zonlong.iceandfiredreadland.world.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 传送目标解析结果。
 *
 * <p>刻意不提供“随便给个坐标兜底”的工厂方法：解析失败必须是显式失败，
 * 调用方据此留在源维度并反馈，而不是被丢到未经验证的位置。
 */
public final class DreadPortalDestination {

    /** 解析失败原因，用于区分可区分的失败分支。 */
    public enum Failure {
        /** 目标维度不存在或未加载。 */
        DIMENSION_UNAVAILABLE,
        /** 搜索预算内没有找到任何候选点。 */
        NO_CANDIDATE,
        /** 找到候选但全部不安全（空间不足、危险地面、无支撑等）。 */
        NO_SAFE_POSITION,
        /** 候选点数量超出配置预算。 */
        BUDGET_EXCEEDED
    }

    /** 最终把实体放到的位置（传送门内的落点，实体脚部坐标）。 */
    private final BlockPos position;
    /** 迁移过程中先抵达的安全等待点；必须位于写集之外且可站立。 */
    private final BlockPos waitingPosition;
    /** 复用了已有的门（true），还是需要新建出口结构（false）。 */
    private final boolean reusedExistingPortal;
    private final ServerLevel level;

    private DreadPortalDestination(ServerLevel level, BlockPos position, BlockPos waitingPosition,
                                   boolean reusedExistingPortal) {
        this.level = level;
        this.position = position;
        this.waitingPosition = waitingPosition;
        this.reusedExistingPortal = reusedExistingPortal;
    }

    public static DreadPortalDestination of(ServerLevel level, BlockPos position, BlockPos waitingPosition,
                                            boolean reusedExistingPortal) {
        return new DreadPortalDestination(level, position, waitingPosition, reusedExistingPortal);
    }

    public ServerLevel level() {
        return this.level;
    }

    public BlockPos position() {
        return this.position;
    }

    public BlockPos waitingPosition() {
        return this.waitingPosition;
    }

    /**
     * 是否复用了已有传送门。
     *
     * <p>为 true 时调用方不得写入任何结构：只需把实体移到 {@link #position()}。
     * 为 false 时表示需要走 T3 的受保护放置流程。
     */
    public boolean reusedExistingPortal() {
        return this.reusedExistingPortal;
    }

    /** 解析结果：要么成功，要么带明确原因失败。 */
    public static final class Result {
        private final DreadPortalDestination destination;
        private final Failure failure;

        private Result(DreadPortalDestination destination, Failure failure) {
            this.destination = destination;
            this.failure = failure;
        }

        public static Result success(DreadPortalDestination destination) {
            return new Result(destination, null);
        }

        public static Result failure(Failure failure) {
            return new Result(null, failure);
        }

        public boolean isSuccess() {
            return this.destination != null;
        }

        /** 仅在 {@link #isSuccess()} 为 true 时非空。 */
        public DreadPortalDestination destination() {
            return this.destination;
        }

        /** 仅在失败时非空。 */
        public Failure failure() {
            return this.failure;
        }
    }

    private DreadPortalDestination() {
        throw new AssertionError();
    }
}
