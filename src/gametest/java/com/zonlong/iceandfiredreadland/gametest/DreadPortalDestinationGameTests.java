package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalDestination;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalDestinationResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T2：双向安全落点解析的用例（DP-001）。
 *
 * <p>坐标约定（实测确认，见下）：{@code helper.setBlock} / {@code helper.getBlockState} /
 * {@code helper.spawn} 接收的都是<b>模板相对坐标</b>；而 {@code helper.getLevel().getBlockState()}
 * 以及被测的解析器接收的是<b>世界绝对坐标</b>。因此所有面向 level 的调用都必须先经
 * {@code helper.absolutePos(...)} 转换。
 *
 * <p>跨维度端到端行为由 T4 的旅行服务测试承担。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalDestinationGameTests {

    /** 测试平台中心（模板相对坐标）。 */
    private static final BlockPos FEET_REL = new BlockPos(2, 1, 2);

    private DreadPortalDestinationGameTests() {
    }

    /** 铺满石头地面，固定地表高度（模板相对坐标）。 */
    private static void layGround(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * 空白平台上的普通落点必须被判为安全：这正是旧实现从不检查的部分。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void plain_ground_is_a_safe_position(GameTestHelper helper) {
        layGround(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, FEET_REL);
        BlockPos feet = helper.absolutePos(FEET_REL);

        helper.assertTrue(
                DreadPortalDestinationResolver.isSafeStandingPosition(
                        helper.getLevel(), zombie, feet, feet.below()),
                "石头地面上的空位应被判为安全落点，feet=" + feet
                        + " ground=" + helper.getLevel().getBlockState(feet.below()));

        zombie.discard();
        helper.succeed();
    }

    /**
     * 没有支撑的位置不安全：避免把实体丢到空中。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void position_without_support_is_unsafe(GameTestHelper helper) {
        // 刻意不铺地面，脚下是空气。
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, FEET_REL);
        BlockPos feet = helper.absolutePos(FEET_REL);

        helper.assertFalse(
                DreadPortalDestinationResolver.isSafeStandingPosition(
                        helper.getLevel(), zombie, feet, feet.below()),
                "脚下无支撑的位置不应被判为安全落点");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 熔岩地面必须被拒绝（对应 DP-001 中「液体与危险地面」的要求）。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void lava_ground_is_unsafe(GameTestHelper helper) {
        layGround(helper);
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.LAVA);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, FEET_REL);
        BlockPos feet = helper.absolutePos(FEET_REL);

        helper.assertFalse(
                DreadPortalDestinationResolver.isSafeStandingPosition(
                        helper.getLevel(), zombie, feet, feet.below()),
                "熔岩上方不应被判为安全落点");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 落点空间被实心方块占据时必须被拒绝，不能把实体塞进方块里。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void blocked_space_is_unsafe(GameTestHelper helper) {
        layGround(helper);
        // 脚部所在格改成实心，实体无法站立。
        helper.setBlock(FEET_REL, Blocks.STONE);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        BlockPos feet = helper.absolutePos(FEET_REL);

        helper.assertFalse(
                DreadPortalDestinationResolver.isSafeStandingPosition(
                        helper.getLevel(), zombie, feet, feet.below()),
                "被实心方块占据的位置不应被判为安全落点");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 水域落点不安全：实体不应被放进水里。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void water_position_is_unsafe(GameTestHelper helper) {
        layGround(helper);
        helper.setBlock(FEET_REL, Blocks.WATER);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 3, 2));
        BlockPos feet = helper.absolutePos(FEET_REL);

        helper.assertFalse(
                DreadPortalDestinationResolver.isSafeStandingPosition(
                        helper.getLevel(), zombie, feet, feet.below()),
                "含液体的位置不应被判为安全落点");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 解析结果必须区分「成功」与各失败原因，且失败时不携带落点。
     *
     * <p>这条约束防止后续实现顺手加回「随便给个坐标兜底」。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void failed_result_never_carries_a_position(GameTestHelper helper) {
        DreadPortalDestination.Result failure =
                DreadPortalDestination.Result.failure(DreadPortalDestination.Failure.NO_SAFE_POSITION);

        helper.assertFalse(failure.isSuccess(), "失败结果不应报告成功");
        helper.assertTrue(failure.destination() == null, "失败结果不应携带落点");
        helper.assertTrue(failure.failure() == DreadPortalDestination.Failure.NO_SAFE_POSITION,
                "失败原因应被保留");

        DreadPortalDestination.Result success = DreadPortalDestination.Result.success(
                DreadPortalDestination.of(helper.getLevel(), BlockPos.ZERO, BlockPos.ZERO, true));
        helper.assertTrue(success.isSuccess(), "成功结果应报告成功");
        helper.assertTrue(success.failure() == null, "成功结果不应携带失败原因");
        helper.assertTrue(success.destination().reusedExistingPortal(),
                "复用门的结果应标记为 reusedExistingPortal");

        helper.succeed();
    }

    /**
     * 搜索预算必须是有限常量：半径 32、候选上限 64、额外加载区块上限 25。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void search_budget_is_bounded(GameTestHelper helper) {
        helper.assertTrue(DreadPortalDestinationResolver.SEARCH_RADIUS == 32,
                "搜索半径应为 32，实际 " + DreadPortalDestinationResolver.SEARCH_RADIUS);
        helper.assertTrue(DreadPortalDestinationResolver.MAX_CANDIDATES == 64,
                "候选上限应为 64，实际 " + DreadPortalDestinationResolver.MAX_CANDIDATES);
        helper.assertTrue(DreadPortalDestinationResolver.MAX_CHUNKS_TO_LOAD == 25,
                "额外加载区块上限应为 25，实际 " + DreadPortalDestinationResolver.MAX_CHUNKS_TO_LOAD);
        helper.succeed();
    }

    /**
     * 悚域传送门方块自身不能充当落脚支撑（碰撞箱为空，会造成悬空）。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void portal_block_is_not_valid_support(GameTestHelper helper) {
        layGround(helper);
        helper.setBlock(new BlockPos(2, 0, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        BlockPos feet = helper.absolutePos(FEET_REL);

        helper.assertFalse(
                DreadPortalDestinationResolver.isSafeStandingPosition(
                        helper.getLevel(), zombie, feet, feet.below()),
                "传送门方块不应被当作可站立支撑");

        zombie.discard();
        helper.succeed();
    }
}
