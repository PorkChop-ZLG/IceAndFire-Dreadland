package com.zonlong.iceandfiredreadland.world.portal;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * T3：受保护的悚域出口放置（DP-002）。
 *
 * <p>0.1.2 直接用空的 {@link StructurePlaceSettings} 调用 {@code placeInWorld()}，
 * 会无差别覆盖目标区域，包括玩家建筑与容器。
 *
 * <p>本类把放置拆成「只读预检计划」与「受检写入 + 失败回滚」两步：
 * <ol>
 *   <li>{@link #plan} 不写任何方块，只计算写集并逐格验证可写性；</li>
 *   <li>{@link #apply} 写入前记录每格原状态，写入后核对结果，失败则按记录回滚。</li>
 * </ol>
 *
 * <p>本实现不复制整套世界结构系统：模板只支持默认放置设置（无旋转/镜像），
 * 与本模组出口模板的实际用法一致。
 */
public final class DreadPortalExitPlacement {

    /** 悚域出口结构模板 ID，保持既有注册路径。 */
    public static final ResourceLocation EXIT_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, "dread_exit_portal");

    /**
     * 模板写集在当前实现中使用的放置原点偏移。
     *
     * <p>0.1.2 的原实现使用 {@code living.blockPosition().subtract(new BlockPos(2, 1, 2))}，
     * 此处保留同一偏移，使出口外观与旧存档一致。模板实测尺寸 6×7×6，
     * 因此写集相对抵达方块覆盖 X/Z {@code -2..+3}、Y {@code -1..+5}。
     */
    public static final BlockPos PLACEMENT_OFFSET = new BlockPos(2, 1, 2);

    /** 由抵达方块坐标计算模板放置原点。 */
    public static BlockPos originFor(BlockPos arrival) {
        return arrival.subtract(PLACEMENT_OFFSET);
    }

    private DreadPortalExitPlacement() {
    }

    /** 预检失败原因。 */
    public enum Failure {
        /** 模板不存在（数据包缺失或未加载）。 */
        TEMPLATE_MISSING,
        /** 写集超出世界建筑高度范围。 */
        OUT_OF_WORLD,
        /** 写集内有容器、流体、不可破坏方块等受保护内容。 */
        PROTECTED_BLOCK,
        /** 写集跨越未加载区块。 */
        CHUNK_NOT_LOADED,
        /** 安全等待点不在写集之外，或不可站立。 */
        WAITING_POSITION_UNSAFE,
        /** 写入后核对失败。 */
        VERIFY_FAILED
    }

    /** 写入计划：由 {@link #plan} 生成后再交给 {@link #apply}。 */
    public static final class Plan {
        private final ServerLevel level;
        private final List<Entry> entries;
        private final BlockPos origin;
        private final BlockPos waitingPosition;
        /** 写入前记录的原状态，供回滚使用；在 apply 开始时填充。 */
        private final List<Entry> rollbackJournal = new ArrayList<>();
        private boolean applied;

        Plan(ServerLevel level, List<Entry> entries, BlockPos origin, BlockPos waitingPosition) {
            this.level = level;
            this.entries = List.copyOf(entries);
            this.origin = origin;
            this.waitingPosition = waitingPosition;
        }

        public ServerLevel level() {
            return this.level;
        }

        public BlockPos origin() {
            return this.origin;
        }

        public BlockPos waitingPosition() {
            return this.waitingPosition;
        }

        /** 写集条目数（含空气格）。 */
        public int size() {
            return this.entries.size();
        }

        /** 计划覆盖的方块坐标（含空气格）。 */
        public List<BlockPos> positions() {
            return this.entries.stream().map(e -> e.pos).toList();
        }

        /** 计划中实际会放置的传送门方块数量。 */
        public long portalBlockCount() {
            return this.entries.stream().filter(e -> e.state.is(ModBlocks.DREAD_PORTAL.get())).count();
        }

        /** 是否已成功写入。 */
        public boolean isApplied() {
            return this.applied;
        }
    }

    private record Entry(BlockPos pos, BlockState state) {
    }

    /** 计划结果：成功给出可写入的计划，失败给出明确原因。 */
    public static final class PlanResult {
        private final Plan plan;
        private final Failure failure;

        private PlanResult(Plan plan, Failure failure) {
            this.plan = plan;
            this.failure = failure;
        }

        public static PlanResult success(Plan plan) {
            return new PlanResult(plan, null);
        }

        public static PlanResult failure(Failure failure) {
            return new PlanResult(null, failure);
        }

        public boolean isSuccess() {
            return this.plan != null;
        }

        public Plan plan() {
            return this.plan;
        }

        public Failure failure() {
            return this.failure;
        }
    }

    /**
     * 只读预检：计算写集并验证可写性。本方法<b>不得</b>写入任何方块、
     * 清理方块实体或产生掉落。
     *
     * @param level           目标维度
     * @param entity          即将抵达的生物（用于校验等待点空间）
     * @param origin          模板放置原点（模板的 min 角）
     * @param waitingPosition 迁移期间先抵达的安全等待点，必须位于写集之外
     */
    public static PlanResult plan(ServerLevel level, LivingEntity entity, BlockPos origin,
                                 BlockPos waitingPosition) {
        var templateOpt = level.getStructureManager().get(EXIT_TEMPLATE);
        if (templateOpt.isEmpty()) {
            return PlanResult.failure(Failure.TEMPLATE_MISSING);
        }
        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings();

        // 需要「未经方块过滤」的完整写集，而公开 API 的 filterBlocks(pos, settings, block)
        // 只返回匹配某个方块的子集（传 null 会 NPE）。因此通过访问转换器
        // （src/main/resources/META-INF/accesstransformer.cfg）读取完整调色板。
        // 使用 palettes（未过滤列表）而不是 getRandomPalette(...)：后者是随机取一个调色板，
        // 对多调色板模板不稳定，预检与写入必须基于同一份确定写集。
        List<Entry> entries = new ArrayList<>();
        for (StructureTemplate.Palette palette : template.palettes) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                BlockState state = info.state();
                if (state == null) {
                    continue;
                }
                entries.add(new Entry(origin.offset(info.pos()).immutable(), state));
            }
        }
        if (entries.isEmpty()) {
            return PlanResult.failure(Failure.TEMPLATE_MISSING);
        }

        // 写集必须完全位于世界建筑高度范围内。
        for (Entry entry : entries) {
            if (entry.pos.getY() <= level.getMinBuildHeight()
                    || entry.pos.getY() >= level.getMaxBuildHeight()) {
                return PlanResult.failure(Failure.OUT_OF_WORLD);
            }
        }

        // 等待点必须在写集之外，否则回滚与失败兜底都不可靠。
        boolean waitingInsideWriteSet = entries.stream().anyMatch(e -> e.pos.equals(waitingPosition));
        if (waitingInsideWriteSet) {
            return PlanResult.failure(Failure.WAITING_POSITION_UNSAFE);
        }

        // 逐格验证可写性。
        for (Entry entry : entries) {
            if (!level.hasChunkAt(entry.pos)) {
                return PlanResult.failure(Failure.CHUNK_NOT_LOADED);
            }
        }
        for (Entry entry : entries) {
            if (!isAcceptableWrite(level, entry.pos, entry.state)) {
                return PlanResult.failure(Failure.PROTECTED_BLOCK);
            }
        }

        // 最终落点必须能让实体站进去。
        // 关键点：此时写集尚未写入，脚下支撑可能是模板本身将要写入的方块，
        // 因此必须按「写入后」的状态校验，否则预检会因当前是空气而误判失败。
        BlockPos finalFeet = findFinalFeet(entries);
        if (finalFeet == null) {
            return PlanResult.failure(Failure.WAITING_POSITION_UNSAFE);
        }
        if (!isSafeAfterWrite(level, entity, finalFeet, entries)) {
            return PlanResult.failure(Failure.WAITING_POSITION_UNSAFE);
        }

        return PlanResult.success(new Plan(level, entries, origin, waitingPosition));
    }

    /**
     * 写入后的有效方块状态：写集内的位置取模板将要写入的状态，写集外取世界当前状态。
     *
     * <p>预检必须在「写入后」的视角下判断空间与支撑，否则会把模板自己写出的支撑误判为缺失。
     */
    private static BlockState effectiveState(ServerLevel level, BlockPos pos, List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.pos.equals(pos)) {
                return entry.state;
            }
        }
        return level.getBlockState(pos);
    }

    /**
     * 按写入后的状态判断某格能否作为该生物的落脚点。
     *
     * <p>把写集内将要写入的方块视为已经存在，其余位置沿用世界当前状态。
     */
    private static boolean isSafeAfterWrite(ServerLevel level, LivingEntity entity, BlockPos feet,
                                            List<Entry> entries) {
        if (feet.getY() <= level.getMinBuildHeight()
                || feet.getY() + Math.ceil(entity.getBbHeight()) >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.hasChunkAt(feet)) {
            return false;
        }

        // 脚下支撑（按写入后状态）。
        BlockPos ground = feet.below();
        BlockState groundState = effectiveState(level, ground, entries);
        if (DreadPortalDestinationResolver.isHazard(groundState) || !groundState.getFluidState().isEmpty()) {
            return false;
        }
        boolean groundInWriteSet = contains(entries, ground);
        if (!groundInWriteSet && groundState.getCollisionShape(level, ground).isEmpty()) {
            return false;
        }

        // 实体自身包围盒所在空间（按写入后状态）。
        double half = entity.getBbWidth() / 2.0;
        AABB box = new AABB(
                feet.getX() + 0.5 - half, feet.getY(), feet.getZ() + 0.5 - half,
                feet.getX() + 0.5 + half, feet.getY() + entity.getBbHeight(), feet.getZ() + 0.5 + half);

        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4))) {
            BlockState state = effectiveState(level, pos, entries);
            if (DreadPortalDestinationResolver.isHazard(state)) {
                return false;
            }
            if (!state.getFluidState().isEmpty()) {
                return false;
            }
            if (contains(entries, pos)) {
                // 写集内：只有空气/无碰撞方块才允许，否则实体写完后会被卡住。
                if (!state.getCollisionShape(level, pos).isEmpty()) {
                    return false;
                }
            } else if (!state.getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(List<Entry> entries, BlockPos pos) {
        for (Entry entry : entries) {
            if (entry.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** 模板中第一个传送门方块作为最终落点候选（门块碰撞箱为空，实体可站入）。 */
    @Nullable
    private static BlockPos findFinalFeet(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.state.is(ModBlocks.DREAD_PORTAL.get())) {
                return entry.pos;
            }
        }
        return null;
    }

    /**
     * 判断某格是否允许被写入。
     *
     * <p>保守白名单：
     * <ul>
     *   <li>该位置已经等于模板要写的方块：允许（幂等重放，例如重复进入同一出口）；</li>
     *   <li>容器、带方块实体、流体、不可破坏方块：一律拒绝，即使模板要写这里；</li>
     *   <li>模板为空气：只允许清空空气或明确可清除的轻量植被；</li>
     *   <li>模板为实体方块：只允许覆盖空气或轻量植被。</li>
     * </ul>
     * 不能只用 {@code canBeReplaced()} 作为白名单：可替换方块里同样可能有不应清理的内容。
     */
    public static boolean isAcceptableWrite(ServerLevel level, BlockPos pos, BlockState templateState) {
        BlockState current = level.getBlockState(pos);

        // 已经与模板一致：幂等，直接允许。
        if (current.equals(templateState)) {
            return true;
        }

        // 受保护内容：容器/方块实体、流体、不可破坏方块。即使用户建筑恰好不是模板方块，
        // 也不能被清除。
        if (isProtected(level, pos, current)) {
            return false;
        }

        if (templateState.isAir()) {
            // 需要清空的格子：只允许已是空气或轻量植被；实心建筑一律拒绝。
            return current.isAir()
                    || DreadPortalDestinationResolver.isLightweightReplaceable(current);
        }

        // 模板为实体方块：只允许覆盖空气或轻量植被，避免误改玩家建筑。
        return current.isAir()
                || DreadPortalDestinationResolver.isLightweightReplaceable(current);
    }

    /** 容器、方块实体、流体、不可破坏方块等受保护内容。 */
    private static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        // 带方块实体（箱子、告示牌、刷怪笼等）一律保护。
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            return true;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return true;
        }
        // 不可破坏：原版硬度为 -1 的方块（基岩、末地传送门框架等）。
        return state.getDestroySpeed(level, pos) < 0.0F;
    }

    /**
     * 应用计划：记录原状态 → 写入 → 核对 → 失败回滚。
     *
     * @return 成功返回 true；失败时已回滚本次写集并返回 false
     */
    public static boolean apply(Plan plan) {
        ServerLevel level = plan.level;
        plan.rollbackJournal.clear();
        plan.applied = false;

        for (Entry entry : plan.entries) {
            plan.rollbackJournal.add(new Entry(entry.pos, level.getBlockState(entry.pos)));
            level.setBlock(entry.pos, entry.state, Block.UPDATE_ALL);
        }

        // 核对实际写入结果：不能只依赖方法返回值。
        for (Entry entry : plan.entries) {
            if (!level.getBlockState(entry.pos).equals(entry.state)) {
                rollback(plan);
                return false;
            }
        }

        if (!verifyPortalPresent(level, plan)) {
            rollback(plan);
            return false;
        }

        plan.applied = true;
        return true;
    }

    /** 写集内是否至少留下了一个传送门方块。 */
    private static boolean verifyPortalPresent(ServerLevel level, Plan plan) {
        return plan.entries.stream()
                .filter(e -> e.state.is(ModBlocks.DREAD_PORTAL.get()))
                .anyMatch(e -> level.getBlockState(e.pos).is(ModBlocks.DREAD_PORTAL.get()));
    }

    /**
     * 仅按本次写集记录回滚，不触碰其他位置。
     *
     * <p>若写入后状态已被外部逻辑另行修改，这里仍会覆盖回原状态；调用方若要避免
     * 覆盖他人新状态，应在回滚前重新核对。本轮不实现该扩展。
     */
    public static void rollback(Plan plan) {
        for (Entry original : plan.rollbackJournal) {
            plan.level.setBlock(original.pos, original.state, Block.UPDATE_ALL);
        }
        plan.rollbackJournal.clear();
        plan.applied = false;
    }

    /** 供测试使用：判断一个写集是否能容纳实体。 */
    public static boolean canFitEntity(ServerLevel level, LivingEntity entity, BlockPos feet) {
        AABB box = entity.getBoundingBox().move(
                feet.getX() + 0.5 - entity.getX(), feet.getY() - entity.getY(), feet.getZ() + 0.5 - entity.getZ());
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4))) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 便捷判断：某方块是否属于受保护内容（供测试与调试）。 */
    public static boolean isProtectedBlock(ServerLevel level, BlockPos pos) {
        return isProtected(level, pos, level.getBlockState(pos));
    }

    /** 常用保护方块列表，便于测试构造。 */
    public static List<Block> protectedExamples() {
        return List.of(Blocks.CHEST, Blocks.BARREL, Blocks.FURNACE, Blocks.BEDROCK, Blocks.SPAWNER);
    }
}
