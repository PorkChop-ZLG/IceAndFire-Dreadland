package com.zonlong.iceandfiredreadland.world.portal;

/**
 * 悚域传送门 HUD 遮罩的进度数学（DP-008）。
 *
 * <p>放在公共端而非客户端渲染类中，使这段逻辑能在专用服务端的 GameTest 中直接验证，
 * 不需要启动客户端。
 *
 * <p>进度恒定位于 {@code [0,1]}。入门按配置时长增长；离门以固定时长淡出，
 * 因此「在门内停留多久」不再影响离开后的淡出总时长（这正是 0.1.2 的缺陷）。
 */
public final class DreadPortalOverlayMath {

    /** 离门后的固定淡出时长（tick）。计划要求不超过 10。 */
    public static final int FADE_OUT_TICKS = 10;

    private DreadPortalOverlayMath() {
    }

    /**
     * 推进一 tick 的显示进度。
     *
     * @param current   当前进度
     * @param inPortal  本 tick 是否站在门内
     * @param growTicks 入门时从 0 涨到 1 所需的 tick 数；小于 1 时按 1 处理，避免除零
     * @return 新进度，始终位于 [0,1]
     */
    public static float advance(float current, boolean inPortal, int growTicks) {
        float bounded = clamp(current);
        if (inPortal) {
            int ticks = Math.max(1, growTicks);
            return clamp(bounded + 1.0F / ticks);
        }
        return clamp(bounded - 1.0F / FADE_OUT_TICKS);
    }

    /**
     * 从满进度开始淡出，需要多少 tick 才能归零。
     *
     * <p>刻意与当前进度无关地返回固定值，用于断言「停留时长不影响淡出总时长」。
     */
    public static int fadeOutDurationTicks() {
        return FADE_OUT_TICKS;
    }

    public static float clamp(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return value > 1.0F ? 1.0F : value;
    }
}
