package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.registry.tag.ModBlockTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T0 基础设施冒烟测试：确认 GameTest source set 被真正发现并执行，
 * 同时校验模组注册表与数据包标签在专用服务端环境下可用。
 * 本类不含传送门行为逻辑，行为用例由后续任务各自的测试类承担。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalSmokeGameTests {

    private DreadPortalSmokeGameTests() {
    }

    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void portal_block_and_frame_tag_are_registered(GameTestHelper helper) {
        ResourceLocation portalId = ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, "dread_portal");
        helper.assertTrue(
                BuiltInRegistries.BLOCK.getOptional(portalId).isPresent(),
                "dread_portal 未注册到方块注册表: " + portalId);

        helper.assertTrue(
                ModBlocks.DREAD_PORTAL.get().defaultBlockState().is(ModBlocks.DREAD_PORTAL.get()),
                "dread_portal 方块状态自检失败");

        ResourceLocation frameTagId = ResourceLocation.fromNamespaceAndPath(
                IceAndFireDreadLand.MODID, "dreadland_portal_frame");
        helper.assertTrue(
                BuiltInRegistries.BLOCK.getTag(ModBlockTags.DREADLAND_PORTAL_FRAME).isPresent(),
                "框架标签未加载: " + frameTagId);

        helper.succeed();
    }
}
