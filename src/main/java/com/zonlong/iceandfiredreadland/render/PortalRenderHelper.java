package com.zonlong.iceandfiredreadland.render;

import com.zonlong.iceandfiredreadland.config.ModCommonConfig;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalOverlayMath;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 悚域传送门的 HUD 覆盖层进度。
 *
 * <p>DP-008：0.1.2 在门内每 tick 执行 {@code TICK++}，不受服务端倒计时或 success 状态限制，
 * 离门后每次只减 1，而 Mixin 只在渲染时钳制显示值。结果是在门内停留越久，
 * 离开后遮罩保持满值的时间越长；世界切换也从不清理静态计数。
 *
 * <p>现在改为<b>有界显示进度</b> {@code progress ∈ [0,1]}：
 * <ul>
 *   <li>入门按配置时长增长到 1；</li>
 *   <li>离门以固定时长淡出（不超过 {@link #FADE_OUT_TICKS}），停留时间长短不再影响淡出总时长；</li>
 *   <li>切换世界/维度或玩家对象更换时立即清零。</li>
 * </ul>
 *
 * <p>HUD 只是显示层，绝不作为是否触发服务端传送的依据。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(Dist.CLIENT)
public class PortalRenderHelper {

    /** 离门后的固定淡出时长（tick）。计划要求不超过 10。 */
    public static final int FADE_OUT_TICKS = DreadPortalOverlayMath.FADE_OUT_TICKS;

    /** 当前显示进度，范围 [0,1]。 */
    private static float progress = 0.0F;
    /** 上一 tick 所处的世界，用于检测世界/维度切换。 */
    private static Level lastLevel = null;
    /** 上一 tick 的玩家对象，用于检测重连或对象更换。 */
    private static Player lastPlayer = null;

    /** 当前遮罩不透明度；0 表示不渲染。 */
    public static float getProgress() {
        return progress;
    }

    /** 是否应渲染遮罩。 */
    public static boolean isActive() {
        return progress > 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        Player player = minecraft.player;

        if (level == null || player == null) {
            // 退出世界/断线：必须清零，否则下次进入世界会残留上次的遮罩。
            reset();
            return;
        }
        // 切换世界或维度、玩家对象被替换（重连）：同样清零。
        if (level != lastLevel || player != lastPlayer) {
            lastLevel = level;
            lastPlayer = player;
            progress = 0.0F;
        }

        if (level.getBlockState(player.blockPosition()).is(ModBlocks.DREAD_PORTAL.get())) {
            progress = advanceProgress(progress, true,
                    Math.max(1, ModCommonConfig.INSTANCE.portalTeleportTick.getValue()));
        } else if (progress > 0.0F) {
            progress = advanceProgress(progress, false, 1);
        }
    }

    /**
     * 纯函数形式推进进度，委托给公共端的 {@link DreadPortalOverlayMath}，
     * 使该逻辑可在服务端 GameTest 中验证。
     *
     * @param current   当前进度
     * @param inPortal  本 tick 是否站在门内
     * @param growTicks 入门时从 0 涨到 1 所需的 tick 数；必须大于 0，避免除零
     * @return 新的进度，始终位于 [0,1]
     */
    public static float advanceProgress(float current, boolean inPortal, int growTicks) {
        return DreadPortalOverlayMath.advance(current, inPortal, growTicks);
    }

    private static float clamp(float value) {
        return DreadPortalOverlayMath.clamp(value);
    }

    /** 清零显示状态与生命周期记录。 */
    public static void reset() {
        progress = 0.0F;
        lastLevel = null;
        lastPlayer = null;
    }

    /** 供测试与调试：直接设置进度。 */
    public static void setProgressForTesting(float value) {
        progress = clamp(value);
    }
}
