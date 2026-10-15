package com.zonlong.iceandfiredreadland.world.portal;

import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * T2：双向安全目标解析（DP-001）。
 *
 * <p>取代 0.1.2 中「把源维度坐标原样当作目标坐标」的做法。两个方向（进入悚域与返回主世界）
 * 都执行同一套安全检查：先尝试复用搜索范围内的已有传送门，再寻找安全地表落点。
 *
 * <p>搜索是有界的：半径、候选数量、额外加载区块数量都有上限，超出预算直接失败，
 * 不扩大为无界搜索，也不返回未经检查的坐标兜底。
 */
public final class DreadPortalDestinationResolver {

    /** 水平搜索半径（格）。投影 X/Z 周围这么远。 */
    public static final int SEARCH_RADIUS = 32;
    /** 候选点数量上限。 */
    public static final int MAX_CANDIDATES = 64;
    /** 单次允许额外同步加载的区块上限（已加载区块不计入）。 */
    public static final int MAX_CHUNKS_TO_LOAD = 25;
    /** 在安全落点周围尝试的 Y 偏移，按由近及远排列。 */
    private static final int[] Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 8, -8, 10, -10, 16, -16};

    private DreadPortalDestinationResolver() {
    }

    /**
     * 解析某个生物从当前位置出发的传送目标。
     *
     * @param source 生物当前所在的维度
     * @param entity 即将迁移的生物
     * @return 成功时给出已验证的落点；失败时给出明确原因
     */
    public static DreadPortalDestination.Result resolve(ServerLevel source, LivingEntity entity) {
        MinecraftServer server = source.getServer();
        boolean fromDreadland = source.dimension().location().equals(ModDimensions.DREADLAND.location());

        ServerLevel target = fromDreadland
                ? server.overworld()
                // 保持既定方向语义：任何非悚域维度都进入悚域。
                : server.getLevel(ModDimensions.DREADLAND);

        if (target == null) {
            return DreadPortalDestination.Result.failure(
                    DreadPortalDestination.Failure.DIMENSION_UNAVAILABLE);
        }

        BlockPos origin = entity.blockPosition();
        // X/Z 保持 1:1 投影作为搜索基准；Y 在目标维度重新计算，不沿用源维度高度。
        double targetX = entity.getX();
        double targetZ = entity.getZ();

        SearchBudget budget = new SearchBudget();
        try {
            // 1) 优先复用附近的已有传送门（框架门与旧式无框出口都是同一个方块）。
            DreadPortalDestination.Result reuse = findReusablePortal(target, entity, targetX, targetZ, budget);
            if (reuse != null) {
                return reuse;
            }

            // 2) 没有可用门：寻找安全地表落点。
            //    悚域可以在此基础上生成受保护的新出口（由 T3 处理）；主世界只找安全落点，不新增结构。
            return findSafeGround(target, entity, targetX, targetZ, fromDreadland, budget);
        } catch (BudgetExceededException e) {
            return DreadPortalDestination.Result.failure(DreadPortalDestination.Failure.BUDGET_EXCEEDED);
        } finally {
            budget.releaseTickets();
        }
    }

    // === 复用已有传送门 ===

    @Nullable
    private static DreadPortalDestination.Result findReusablePortal(ServerLevel level, LivingEntity entity,
                                                                   double targetX, double targetZ,
                                                                   SearchBudget budget) {
        List<BlockPos> portalBlocks = collectNearbyPortalBlocks(level, targetX, targetZ, budget);
        if (portalBlocks.isEmpty()) {
            return null;
        }

        for (BlockPos portalPos : portalBlocks) {
            BlockPos stand = findStandableNextTo(level, entity, portalPos);
            if (stand == null) {
                continue;
            }
            // 复用已有门：等待点就是门内落点本身，不需要任何结构写入。
            return DreadPortalDestination.Result.success(
                    DreadPortalDestination.of(level, stand, stand, true));
        }

        // 半径内存在门方块，但没有任何一个能容纳该实体。这属于「有候选但不安全」，
        // 不应静默退回到地表搜索——那样会在已有门旁边凭空生成新出口。
        if (!portalBlocks.isEmpty()) {
            return DreadPortalDestination.Result.failure(
                    DreadPortalDestination.Failure.NO_SAFE_POSITION);
        }
        return null;
    }

    /**
     * 通过方块实体收集附近的门方块，避免扫描半径内每一列的全部高度。
     */
    private static List<BlockPos> collectNearbyPortalBlocks(ServerLevel level, double targetX, double targetZ,
                                                            SearchBudget budget) {
        int minChunkX = (int) Math.floor((targetX - SEARCH_RADIUS) / 16.0);
        int maxChunkX = (int) Math.floor((targetX + SEARCH_RADIUS) / 16.0);
        int minChunkZ = (int) Math.floor((targetZ - SEARCH_RADIUS) / 16.0);
        int maxChunkZ = (int) Math.floor((targetZ + SEARCH_RADIUS) / 16.0);

        Set<BlockPos> found = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = budget.getLoadedChunk(level, cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = entry.getValue();
                    if (be == null) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos();
                    if (level.getBlockState(pos).is(ModBlocks.DREAD_PORTAL.get())) {
                        found.add(pos.immutable());
                    }
                }
                if (found.size() > MAX_CANDIDATES * 4) {
                    // 门方块成片出现，先截断，后续按距离排序后再取前 MAX_CANDIDATES。
                    break;
                }
            }
        }

        List<BlockPos> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(p -> {
            double dx = p.getX() + 0.5 - targetX;
            double dz = p.getZ() + 0.5 - targetZ;
            return dx * dx + dz * dz;
        }));
        if (sorted.size() > MAX_CANDIDATES) {
            sorted = sorted.subList(0, MAX_CANDIDATES);
        }
        return sorted;
    }

    /**
     * 判断某个门方块附近是否存在可站立的落点。
     *
     * <p>不能仅凭「有一个门方块」就认定可用：必须实际检查实体包围盒与脚下支撑。
     */
    @Nullable
    private static BlockPos findStandableNextTo(ServerLevel level, LivingEntity entity, BlockPos portalPos) {
        // 门方块本身所在格、以及其正上方两格，都是常见落点。
        BlockPos[] probes = {
                portalPos,
                portalPos.above(),
                portalPos.above(2)
        };
        for (BlockPos probe : probes) {
            BlockPos ground = probe.below();
            if (isSafeStandingPosition(level, entity, probe, ground)) {
                return probe;
            }
        }
        return null;
    }

    // === 寻找安全地表落点 ===

    private static DreadPortalDestination.Result findSafeGround(ServerLevel level, LivingEntity entity,
                                                               double targetX, double targetZ,
                                                               boolean allowNewExit, SearchBudget budget) {
        List<Integer> surfaceYs = collectSurfaceCandidates(level, targetX, targetZ, budget);
        if (surfaceYs.isEmpty()) {
            return DreadPortalDestination.Result.failure(DreadPortalDestination.Failure.NO_CANDIDATE);
        }

        int baseX = (int) Math.floor(targetX);
        int baseZ = (int) Math.floor(targetZ);

        // 由近及远尝试：先看投影点自身，再看周围一圈。
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (BlockPos column : ring(baseX, baseZ, radius)) {
                for (int surfaceY : surfaceYs) {
                    for (int dy : Y_OFFSETS) {
                        BlockPos candidate = new BlockPos(column.getX(), surfaceY + dy, column.getZ());
                        if (candidate.getY() <= level.getMinBuildHeight()
                                || candidate.getY() >= level.getMaxBuildHeight() - 2) {
                            continue;
                        }
                        if (!isSafeStandingPosition(level, entity, candidate, candidate.below())) {
                            continue;
                        }
                        // 主世界方向不新增原先不存在的出口结构，落点即等待点。
                        // 悚域方向允许 T3 在落点生成受保护出口，等待点必须先于写集独立存在。
                        return DreadPortalDestination.Result.success(
                                DreadPortalDestination.of(level, candidate, candidate, false));
                    }
                }
            }
        }

        return DreadPortalDestination.Result.failure(DreadPortalDestination.Failure.NO_SAFE_POSITION);
    }

    /** 生成由近及远的方块环，避免一次性构造整块区域。 */
    private static Iterable<BlockPos> ring(int centerX, int centerZ, int radius) {
        if (radius == 0) {
            return List.of(new BlockPos(centerX, 0, centerZ));
        }
        List<BlockPos> out = new ArrayList<>(radius * 8);
        for (int dx = -radius; dx <= radius; dx++) {
            out.add(new BlockPos(centerX + dx, 0, centerZ - radius));
            out.add(new BlockPos(centerX + dx, 0, centerZ + radius));
        }
        for (int dz = -radius + 1; dz <= radius - 1; dz++) {
            out.add(new BlockPos(centerX - radius, 0, centerZ + dz));
            out.add(new BlockPos(centerX + radius, 0, centerZ + dz));
        }
        return out;
    }

    /** 收集投影列的地表高度候选。 */
    private static List<Integer> collectSurfaceCandidates(ServerLevel level, double targetX, double targetZ,
                                                          SearchBudget budget) {
        int cx = (int) Math.floor(targetX) >> 4;
        int cz = (int) Math.floor(targetZ) >> 4;
        if (budget.getLoadedChunk(level, cx, cz) == null) {
            return List.of();
        }
        List<Integer> ys = new ArrayList<>(3);
        for (var heightmap : List.of(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR)) {
            ys.add(level.getHeight(heightmap, (int) Math.floor(targetX), (int) Math.floor(targetZ)));
        }
        return ys;
    }

    // === 安全判定 ===

    /**
     * 判断某格能否作为该生物的落脚点。
     *
     * <p>检查项：实体自身尺寸的空间、脚下支撑、液体、火/岩浆等危险地面、世界边界。
     * 不使用「固定两格空气」这类假设，因为不同生物尺寸差异很大。
     */
    public static boolean isSafeStandingPosition(ServerLevel level, LivingEntity entity, BlockPos feet,
                                                 BlockPos ground) {
        if (feet.getY() <= level.getMinBuildHeight()
                || feet.getY() + Math.ceil(entity.getBbHeight()) >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.hasChunkAt(feet)) {
            return false;
        }

        // 脚下必须有支撑，且不是危险地面。
        BlockState groundState = level.getBlockState(ground);
        if (!isAcceptableSupport(level, ground, groundState)) {
            return false;
        }

        // 实体自身包围盒所在的空间必须无碰撞、无液体、无危险方块。
        double half = entity.getBbWidth() / 2.0;
        AABB box = new AABB(
                feet.getX() + 0.5 - half, feet.getY(), feet.getZ() + 0.5 - half,
                feet.getX() + 0.5 + half, feet.getY() + entity.getBbHeight(), feet.getZ() + 0.5 + half);

        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4))) {
            BlockState state = level.getBlockState(pos);
            if (isHazard(state)) {
                return false;
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                return false;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 可接受的脚下支撑：实心、非液体、非危险方块。 */
    private static boolean isAcceptableSupport(ServerLevel level, BlockPos ground, BlockState groundState) {
        if (!level.hasChunkAt(ground)) {
            return false;
        }
        if (isHazard(groundState)) {
            return false;
        }
        if (!groundState.getFluidState().isEmpty()) {
            return false;
        }
        return !groundState.getCollisionShape(level, ground).isEmpty();
    }

    /** 危险或不应作为落点的方块。对放置预检同样适用，因此公开。 */
    public static boolean isHazard(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.SOUL_FIRE
                || block == Blocks.MAGMA_BLOCK || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE || block == Blocks.CACTUS
                || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POWDER_SNOW;
    }

    /** 该方块是否属于「明确可替换的轻量内容」，供 T3 写入预检使用。 */
    public static boolean isLightweightReplaceable(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.SNOW)
                || state.is(Blocks.VINE) || state.is(Blocks.TORCH)
                || state.is(Blocks.DANDELION) || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID) || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET) || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP) || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP) || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER) || state.is(Blocks.LILY_OF_THE_VALLEY);
    }

    /** 朝向辅助：门块的轴向（用于 T3/T4）。 */
    public static Direction.Axis portalAxis(BlockState state) {
        return state.is(ModBlocks.DREAD_PORTAL.get())
                ? state.getValue(com.zonlong.iceandfiredreadland.block.DreadPortalBlock.AXIS)
                : Direction.Axis.X;
    }

    // === 搜索预算 ===

    private static final class BudgetExceededException extends RuntimeException {
        BudgetExceededException() {
            super(null, null, false, false);
        }
    }

    /**
     * 跟踪本次解析额外加载的区块数量，并在结束时释放强制加载票据。
     */
    private static final class SearchBudget {
        private final Set<Long> loaded = new HashSet<>();
        private int extraLoaded;

        @Nullable
        LevelChunk getLoadedChunk(ServerLevel level, int chunkX, int chunkZ) {
            long key = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
            if (level.hasChunk(chunkX, chunkZ)) {
                return level.getChunk(chunkX, chunkZ);
            }
            if (loaded.contains(key)) {
                return null;
            }
            if (extraLoaded >= MAX_CHUNKS_TO_LOAD) {
                throw new BudgetExceededException();
            }
            loaded.add(key);
            extraLoaded++;
            // getChunk 会同步加载区块；票据由 NeoForge 常规区块生命周期管理。
            return level.getChunk(chunkX, chunkZ);
        }

        /**
         * 释放本次搜索可能持有的强制加载票据。
         *
         * <p>当前实现使用 {@code getChunk} 同步加载，不持有长期票据；保留该钩子以便后续
         * 改为 {@code addRegionTicket} 时集中释放。
         */
        void releaseTickets() {
            this.loaded.clear();
        }
    }
}
