package com.zonlong.iceandfiredreadland.data.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.config.ModCommonConfig;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalTravelService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 悚域传送门的临时接触状态。
 *
 * <p>本附件只承载“接触 → 计时 → 迁移 → 等待离门”这一段临时过程，不承载长期存档状态。
 * 从未接触传送门的生物不会被分配本附件；实体离开传送门后一律回到 {@link State#IDLE} 并被移除。
 *
 * <p>倒计时归零后由 {@link #commitTravel(LivingEntity)} 调用
 * {@link DreadPortalTravelService} 执行真正的跨维度迁移。
 */
public class PortalData {

    /** 传送门状态机。只有 {@link #IDLE} 表示附件应当被移除。 */
    public enum State {
        /** 处于第一帧接触，尚未建立倒计时。 */
        JUST_ENTERED,
        /** 已接触，正在累计倒计时。 */
        COUNTING,
        /** 倒计时归零，已触发迁移（T4 接线）。 */
        TRAVELLING,
        /** 迁移成功，站在出口传送门内，等待离开后复位。 */
        SUCCESS_WAIT,
        /** 迁移失败，等待本实体离开传送门后清除失败标记。 */
        FAILED_WAIT,
        /** 未接触传送门。 */
        IDLE
    }

    /**
     * 持久化 Codec。
     *
     * <p>字段名与 0.1.2 保持一致（{@code teleported} / {@code teleportTick}），使旧存档依然可以解析，
     * 不会触发 NeoForge 的 “unknown or non-serializable attachment” 错误。旧数据在构造时一律归一化为
     * {@link State#IDLE}，因此旧存档里残留的 {@code teleportTick=0} 不会造成一进游戏就被传送。
     */
    public static final Codec<PortalData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("teleported", false).forGetter(PortalData::isLegacyTeleported),
            Codec.INT.optionalFieldOf("teleportTick", -1).forGetter(PortalData::getTeleportTick)
    ).apply(i, PortalData::new));

    /**
     * 该附件不再声明网络同步：HUD 使用纯客户端的有界进度，服务端状态不需要广播给其他客户端。
     * 保留该 Codec 仅为兼容既有注册形态与后续可能的同步需求。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalData> PACKET_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private State state = State.IDLE;
    /** 剩余接触 tick；仅 {@link State#COUNTING} 有意义。-1 表示未开始计时。 */
    private int teleportTick = -1;
    /** 兼容旧存档字段，运行时不再作为权威状态；序列化时始终写出 false。 */
    private boolean teleported = false;

    public PortalData() {
    }

    private PortalData(boolean legacyTeleported, int legacyTeleportTick) {
        // 反序列化得到的任何计数都不恢复为“可触发”，避免旧存档残留值导致意外传送。
        this.teleported = false;
        this.teleportTick = -1;
        this.state = State.IDLE;
    }

    /**
     * tick 的结果，供事件处理器决定附件生命周期与玩家提示。
     */
    public enum TickResult {
        /** 状态有进展，保留附件。 */
        KEEP,
        /** 回到空闲，应移除附件。 */
        REMOVE,
        /** 本轮触发了传送但失败/被取消，应清理迁移中标记并提示玩家。 */
        TRAVEL_FAILED
    }

    /**
     * 由服务端 tick 驱动。
     *
     * @param inContact 服务端在本 tick 重新核实过的“确实接触传送门”结论
     * @return 调用方据此决定是否移除附件、是否提示玩家
     */
    public TickResult tick(LivingEntity living, boolean inContact) {
        if (!inContact) {
            // 离开传送门：立即复位，绝不在无接触的情况下依据旧计数触发。
            this.reset();
            return TickResult.REMOVE;
        }

        switch (this.state) {
            case IDLE -> {
                // 本 tick 首次确认接触，建立倒计时，但不在同一 tick 内推进，
                // 保证最小等待时长为配置值本身。
                this.state = State.COUNTING;
                this.teleportTick = Math.max(0, ModCommonConfig.INSTANCE.portalTeleportTick.getValue());
                return TickResult.KEEP;
            }
            case JUST_ENTERED -> {
                this.state = State.COUNTING;
                this.teleportTick = Math.max(0, ModCommonConfig.INSTANCE.portalTeleportTick.getValue());
                return TickResult.KEEP;
            }
            case COUNTING -> {
                if (this.teleportTick > 0) {
                    this.teleportTick--;
                }
                if (this.teleportTick > 0) {
                    return TickResult.KEEP;
                }
                // === DP-005：归零的同一 tick 内立即提交，且绝不依据旧零值延迟触发 ===
                this.state = State.TRAVELLING;
                return this.commitTravel(living);
            }
            case TRAVELLING, SUCCESS_WAIT, FAILED_WAIT -> {
                return TickResult.KEEP;
            }
        }
        return TickResult.KEEP;
    }

    /**
     * 倒计时归零时执行真正的跨维度迁移。
     *
     * <p>只在服务端、且已由调用方确认本 tick 仍在门内时进入。失败或被取消时不标记成功，
     * 而是复位倒计时，使实体离开再进入即可重试，避免每 tick 重试刷屏。
     */
    private TickResult commitTravel(LivingEntity living) {
        DreadPortalTravelService.Outcome outcome;
        try {
            outcome = DreadPortalTravelService.attemptTravel(living);
        } catch (RuntimeException e) {
            // 迁移过程中出现异常：回落到可重试状态，并留下可诊断日志。
            IceAndFireDreadLand.LOGGER.error("[dread-portal] travel threw for {} at {}",
                    living.getType().toShortString(), living.blockPosition(), e);
            this.state = State.COUNTING;
            this.teleportTick = Math.max(0, ModCommonConfig.INSTANCE.portalTeleportTick.getValue());
            return TickResult.TRAVEL_FAILED;
        }

        if (outcome == DreadPortalTravelService.Outcome.MOVED) {
            // 实体已在新维度就位；成功状态在下一次更新时随“已离门”自然复位。
            this.state = State.SUCCESS_WAIT;
            this.teleportTick = -1;
            return TickResult.KEEP;
        }

        // 失败或被取消：实体留在原维度，清除迁移中标记并允许重新计时。
        this.state = State.COUNTING;
        this.teleportTick = Math.max(0, ModCommonConfig.INSTANCE.portalTeleportTick.getValue());
        DreadPortalTravelService.notifyPlayer(living, outcome);
        return TickResult.TRAVEL_FAILED;
    }

    private void reset() {
        this.state = State.IDLE;
        this.teleportTick = -1;
        this.teleported = false;
    }

    public State getState() {
        return this.state;
    }

    /** 供测试与调试使用。 */
    public void setStateForTesting(State state) {
        this.state = state;
    }

    public void setTeleportTickForTesting(int tick) {
        this.teleportTick = tick;
    }

    public boolean isLegacyTeleported() {
        return this.teleported;
    }

    public int getTeleportTick() {
        return this.teleportTick;
    }

    // === 接触判定 ===

    /**
     * 服务端与客户端共用的接触判定：实体碰撞箱是否与悚域传送门方块相交。
     *
     * <p>不采用“只取脚下方块”的做法：门块占满整格，而实体可能只与其中一部分相交。
     * 同时兼容旧式无框出口与框架门两种形态——两者都是同一个方块。
     */
    public static boolean isInPortal(Level level, LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        if (box.getSize() <= 0.0) {
            return false;
        }
        // 略作收缩，避免仅因边界贴合被判为接触。
        AABB probe = box.inflate(-1.0E-4);
        if (probe.getSize() <= 0.0) {
            probe = box;
        }
        return level.getBlockStates(probe).anyMatch(PortalData::isPortalBlock);
    }

    private static boolean isPortalBlock(BlockState state) {
        return state.is(ModBlocks.DREAD_PORTAL.get());
    }

    /** 指定方块坐标是否为悚域传送门。 */
    public static boolean isPortalAt(Level level, BlockPos pos) {
        return isPortalBlock(level.getBlockState(pos));
    }
}
