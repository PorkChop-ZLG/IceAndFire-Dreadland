package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalOverlayMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T7：HUD 遮罩进度的有界性与淡出行为（DP-008）。
 *
 * <p>0.1.2 用无上限的整数累加，Mixin 只在渲染时钳制显示值，导致在门内停留越久、
 * 离开后遮罩保持满值的时间越长。这里验证修好后的两个关键性质：
 * 进度恒有界，以及淡出总时长与停留时长无关。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalOverlayGameTests {

    private DreadPortalOverlayGameTests() {
    }

    /**
     * 在门内持续很久，进度必须封顶在 1，而不是无限增长。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void progress_is_bounded_while_inside(GameTestHelper helper) {
        float progress = 0.0F;
        int growTicks = 100;

        // 走 10 倍于增长时长的 tick 数。
        for (int i = 0; i < growTicks * 10; i++) {
            progress = DreadPortalOverlayMath.advance(progress, true, growTicks);
            helper.assertTrue(progress >= 0.0F && progress <= 1.0F,
                    "进度必须位于 [0,1]，实际=" + progress + " 第 " + i + " tick");
        }
        helper.assertTrue(progress == 1.0F, "长时间停留后进度应恰好为 1，实际=" + progress);

        helper.succeed();
    }

    /**
     * 增长时长按配置生效：N tick 后进度应为 1。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void progress_reaches_full_after_configured_ticks(GameTestHelper helper) {
        for (int growTicks : new int[]{1, 10, 100, 600}) {
            float progress = 0.0F;
            for (int i = 0; i < growTicks; i++) {
                progress = DreadPortalOverlayMath.advance(progress, true, growTicks);
            }
            helper.assertTrue(Math.abs(progress - 1.0F) < 1.0E-4F,
                    "growTicks=" + growTicks + " 时应达到满进度，实际=" + progress);
        }
        helper.succeed();
    }

    /**
     * N=0（配置下限）不得导致除零或 NaN。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void zero_grow_ticks_is_safe(GameTestHelper helper) {
        float progress = DreadPortalOverlayMath.advance(0.0F, true, 0);

        helper.assertFalse(Float.isNaN(progress), "N=0 不得产生 NaN");
        helper.assertFalse(Float.isInfinite(progress), "N=0 不得产生无穷大");
        helper.assertTrue(progress >= 0.0F && progress <= 1.0F, "N=0 时进度仍须有界，实际=" + progress);

        helper.succeed();
    }

    /**
     * DP-008 核心：淡出总时长与停留时长无关。
     *
     * <p>构造两种停留时长——刚好计满，以及远超计满——先走完各自的停留阶段，
     * 再从各自得到的进度开始淡出。两者的淡出 tick 数必须相同且不超过固定上限。
     * 旧实现因为在门内会无限累加，长停留必然导致更久的遮罩残留。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void fade_out_duration_is_independent_of_dwell_time(GameTestHelper helper) {
        helper.assertTrue(DreadPortalOverlayMath.FADE_OUT_TICKS <= 10,
                "淡出时长不得超过 10 tick，实际=" + DreadPortalOverlayMath.FADE_OUT_TICKS);

        int growTicks = 100;

        // 刚好计满。
        float brief = 0.0F;
        for (int i = 0; i < growTicks; i++) {
            brief = DreadPortalOverlayMath.advance(brief, true, growTicks);
        }

        // 停留时长为其 10 倍。
        float longStay = 0.0F;
        for (int i = 0; i < growTicks * 10; i++) {
            longStay = DreadPortalOverlayMath.advance(longStay, true, growTicks);
        }

        helper.assertTrue(Math.abs(brief - longStay) < 1.0E-4F,
                "进度必须封顶：短停留=" + brief + " 长停留=" + longStay);

        int briefFade = ticksToFadeFrom(brief);
        int longFade = ticksToFadeFrom(longStay);

        helper.assertTrue(briefFade == longFade,
                "淡出时长不应随停留时间变化：短=" + briefFade + " 长=" + longFade);
        helper.assertTrue(longFade == DreadPortalOverlayMath.FADE_OUT_TICKS,
                "满进度淡出应恰好需要 " + DreadPortalOverlayMath.FADE_OUT_TICKS
                        + " tick，实际=" + longFade);

        // 无论从哪个非零进度开始，都不得超出固定淡出时长。
        for (float start : new float[]{0.01F, 0.25F, 0.5F, 0.75F, 1.0F}) {
            int ticks = ticksToFadeFrom(start);
            helper.assertTrue(ticks <= DreadPortalOverlayMath.FADE_OUT_TICKS,
                    "从 " + start + " 淡出用了 " + ticks + " tick，超出固定时长");
        }

        helper.succeed();
    }

    /** 从给定进度出发，模拟离门直到进度归零所需的 tick 数。 */
    private static int ticksToFadeFrom(float start) {
        float progress = start;
        int ticks = 0;
        while (progress > 0.0F && ticks < 1000) {
            progress = DreadPortalOverlayMath.advance(progress, false, 1);
            ticks++;
        }
        return ticks;
    }

    /**
     * 淡出不得把进度压成负数。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void fade_never_goes_negative(GameTestHelper helper) {
        float progress = 0.05F;
        for (int i = 0; i < 100; i++) {
            progress = DreadPortalOverlayMath.advance(progress, false, 1);
            helper.assertTrue(progress >= 0.0F, "进度不应为负，实际=" + progress + " 第 " + i + " tick");
        }
        helper.assertTrue(progress == 0.0F, "长时间离门后应恰好归零，实际=" + progress);

        helper.succeed();
    }

    /**
     * clamp 的边界行为。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void clamp_bounds_are_enforced(GameTestHelper helper) {
        helper.assertTrue(DreadPortalOverlayMath.clamp(-5.0F) == 0.0F, "负值应钳制为 0");
        helper.assertTrue(DreadPortalOverlayMath.clamp(0.0F) == 0.0F, "0 应保持 0");
        helper.assertTrue(DreadPortalOverlayMath.clamp(0.5F) == 0.5F, "中间值应保持不变");
        helper.assertTrue(DreadPortalOverlayMath.clamp(1.0F) == 1.0F, "1 应保持 1");
        helper.assertTrue(DreadPortalOverlayMath.clamp(9.0F) == 1.0F, "超过 1 应钳制为 1");
        helper.succeed();
    }
}
