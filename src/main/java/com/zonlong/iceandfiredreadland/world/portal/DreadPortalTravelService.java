package com.zonlong.iceandfiredreadland.world.portal;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * T4：悚域传送门的旅行服务（DP-001 / DP-002 / DP-004 / DP-005 / DP-009）。
 *
 * <p>职责：
 * <ul>
 *   <li>实体资格检查（DP-009）：拒绝乘客、载有乘客的实体，尊重实体自身的跨维度限制；</li>
 *   <li>目标解析（DP-001）：委托 {@link DreadPortalDestinationResolver}；</li>
 *   <li>出口放置（DP-002）：委托 {@link DreadPortalExitPlacement}，失败回滚；</li>
 *   <li>失败与取消处理（DP-004）：区分维度缺失、资格不符、事件取消、放置失败，
 *       取消时保证目标维度零结构写入。</li>
 * </ul>
 *
 * <p>顺序约束（计划 3.4 节）：<b>不能</b>把放置放到 {@code changeDimension()} 之前——
 * 那样旅行被事件取消时仍会留下出口结构。因此流程是：
 * 只读预检 → 正常 changeDimension（可被取消）→ 成功后才应用受检写集。
 */
public final class DreadPortalTravelService {

    /** 旅行结果；调用方据此决定状态机走向与是否提示玩家。 */
    public enum Outcome {
        /** 已成功迁移并（如需）放好出口。 */
        MOVED,
        /** 资格不符（乘客、载客、非存活、维度限制），实体未移动。 */
        INELIGIBLE,
        /** 目标维度不可用，实体未移动。 */
        DIMENSION_UNAVAILABLE,
        /** 搜索预算内找不到安全落点，实体未移动。 */
        NO_SAFE_DESTINATION,
        /** 出口预检失败（写集含建筑/容器等），因此不进行任何写入。 */
        EXIT_PLACEMENT_REJECTED,
        /** NeoForge 旅行事件被取消，实体未移动、目标零写入。 */
        TRAVEL_CANCELLED,
        /** 迁移已发生，但出口写入或核对失败，已回滚。 */
        PLACEMENT_FAILED
    }

    private DreadPortalTravelService() {
    }

    /**
     * 尝试把实体从当前维度送往目标维度。
     *
     * <p>仅供服务端调用。
     *
     * @return 结果摘要；{@link Outcome#MOVED} 表示实体已在新维度就位
     */
    public static Outcome attemptTravel(LivingEntity living) {
        if (living.level().isClientSide()) {
            return Outcome.INELIGIBLE;
        }
        if (!(living.level() instanceof ServerLevel source)) {
            return Outcome.INELIGIBLE;
        }

        // === DP-009：实体资格 ===
        // changeDimension 内部会调用 unRide() 强制拆乘，因此必须在调用前拒绝乘客与载客实体。
        if (living.isPassenger()) {
            return reject(living, Outcome.INELIGIBLE, "entity is a passenger");
        }
        if (living.isVehicle()) {
            return reject(living, Outcome.INELIGIBLE, "entity carries passengers");
        }
        if (!living.canUsePortal(false)) {
            return reject(living, Outcome.INELIGIBLE, "canUsePortal(false) returned false");
        }

        // === 目标维度 ===
        ServerLevel target = resolveTargetLevel(source);
        if (target == null) {
            return reject(living, Outcome.DIMENSION_UNAVAILABLE, "target dimension missing");
        }
        if (!living.canChangeDimensions(source, target)) {
            return reject(living, Outcome.INELIGIBLE, "canChangeDimensions denied by entity");
        }
        if (target.dimension().equals(source.dimension())) {
            return reject(living, Outcome.DIMENSION_UNAVAILABLE, "target dimension equals source");
        }

        // === DP-001：有界安全目标解析 ===
        DreadPortalDestination.Result resolved =
                DreadPortalDestinationResolver.resolve(source, living);
        if (!resolved.isSuccess()) {
            return reject(living, Outcome.NO_SAFE_DESTINATION,
                    "no safe destination: " + resolved.failure());
        }
        DreadPortalDestination destination = resolved.destination();

        // === DP-002：只读预检（需要新建出口时） ===
        DreadPortalExitPlacement.PlanResult planned = null;
        BlockPos arrival;
        if (destination.reusedExistingPortal()) {
            // 复用已有门：零结构写入，直接落到门内。
            arrival = destination.position();
        } else {
            arrival = destination.position();
            BlockPos origin = DreadPortalExitPlacement.originFor(arrival);
            planned = DreadPortalExitPlacement.plan(
                    target, living, origin, destination.waitingPosition());
            if (!planned.isSuccess()) {
                return reject(living, Outcome.EXIT_PLACEMENT_REJECTED,
                        "exit preflight rejected: " + planned.failure() + " at " + origin);
            }
        }

        // === 迁移：走正常 API，保留 NeoForge 旅行事件 ===
        DimensionTransition transition = new DimensionTransition(
                target,
                Vec3.atBottomCenterOf(destination.waitingPosition()),
                Vec3.ZERO,
                living.getYRot(),
                living.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND);

        Entity moved = living.changeDimension(transition);
        if (moved == null) {
            // 事件被取消或实体已移除：目标维度必须保持零结构写入。
            return reject(living, Outcome.TRAVEL_CANCELLED,
                    "travel event cancelled or entity removed; no structure written");
        }
        if (!(moved instanceof LivingEntity movedLiving)) {
            return reject(living, Outcome.TRAVEL_CANCELLED, "unexpected non-living result entity");
        }
        if (!movedLiving.level().dimension().equals(target.dimension())) {
            return reject(living, Outcome.TRAVEL_CANCELLED,
                    "entity did not arrive in target dimension");
        }

        // === 成功之后才应用受检写集 ===
        if (planned != null) {
            if (!DreadPortalExitPlacement.apply(planned.plan())) {
                DreadPortalExitPlacement.rollback(planned.plan());
                // 实体已经在新维度；把它放在写集之外的安全等待点，避免卡在未完成的出口里。
                BlockPos safe = destination.waitingPosition();
                movedLiving.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
                log(living, "exit write failed and was rolled back; entity parked at " + safe);
                return Outcome.PLACEMENT_FAILED;
            }
        }

        // 就位到最终落点（复用门时与等待点相同）。
        movedLiving.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        movedLiving.setDeltaMovement(Vec3.ZERO);
        movedLiving.fallDistance = 0.0F;
        // 明确的成功效果：只在真正完成时播放一次。
        movedLiving.level().playSound(null, movedLiving.blockPosition(),
                net.minecraft.sounds.SoundEvents.PORTAL_TRAVEL,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.0F);

        return Outcome.MOVED;
    }

    @Nullable
    private static ServerLevel resolveTargetLevel(ServerLevel source) {
        var server = source.getServer();
        if (server == null) {
            return null;
        }
        boolean fromDreadland = source.dimension().location()
                .equals(com.zonlong.iceandfiredreadland.registry.ModDimensions.DREADLAND.location());
        // 保持既定方向语义：悚域返回主世界；其他任何维度进入悚域。
        ResourceKey<Level> key = fromDreadland
                ? Level.OVERWORLD
                : com.zonlong.iceandfiredreadland.registry.ModDimensions.DREADLAND;
        return server.getLevel(key);
    }

    private static Outcome reject(LivingEntity living, Outcome outcome, String reason) {
        log(living, reason);
        return outcome;
    }

    private static void log(LivingEntity living, String message) {
        IceAndFireDreadLand.LOGGER.debug("[dread-portal] {} at {}: {}",
                living.getType().toShortString(), living.blockPosition(), message);
    }

    /** 向玩家反馈一次失败（不逐 tick 刷屏；由状态机保证每轮接触只提示一次）。 */
    public static void notifyPlayer(LivingEntity living, Outcome outcome) {
        if (!(living instanceof Player player)) {
            return;
        }
        String key = switch (outcome) {
            case NO_SAFE_DESTINATION -> "message.iceandfire_dreadland.portal.no_safe_destination";
            case EXIT_PLACEMENT_REJECTED -> "message.iceandfire_dreadland.portal.exit_blocked";
            case TRAVEL_CANCELLED -> "message.iceandfire_dreadland.portal.travel_cancelled";
            case PLACEMENT_FAILED -> "message.iceandfire_dreadland.portal.placement_failed";
            case INELIGIBLE -> "message.iceandfire_dreadland.portal.ineligible";
            case DIMENSION_UNAVAILABLE -> "message.iceandfire_dreadland.portal.dimension_unavailable";
            default -> null;
        };
        if (key != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
