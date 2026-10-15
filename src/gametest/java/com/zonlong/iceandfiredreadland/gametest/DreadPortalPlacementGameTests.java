package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalExitPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T3：受保护的出口放置计划与回滚（DP-002）。
 *
 * <p>坐标约定同 T2：{@code helper.setBlock/getBlockState} 用模板相对坐标，
 * 面向 level 的调用必须经 {@code helper.absolutePos(...)} 转换。
 *
 * <p>测试平台为 12×10×12 纯空气；出口模板为 6×7×6，因此放置原点取 (3,1,3) 时
 * 写集完全落在平台内部。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalPlacementGameTests {

    /** 抵达方块（模板相对坐标）。写集覆盖 X/Z 1..6、Y 0..6。 */
    private static final BlockPos ARRIVAL_REL = new BlockPos(3, 1, 3);
    /** 安全等待点，必须位于写集之外。 */
    private static final BlockPos WAITING_REL = new BlockPos(10, 1, 10);

    private DreadPortalPlacementGameTests() {
    }

    /**
     * 铺石头地面，但<b>刻意避开写集</b>。
     *
     * <p>出口模板覆盖 X/Z {@code 1..6}、Y {@code 0..6}。写集内部若已有实心方块，
     * 预检会（正确地）判定为受保护内容而拒绝，因此地面只铺在写集之外，
     * 让「干净区域」用例真正测的是可写入路径。
     */
    private static void layGroundOutsideWriteSet(GameTestHelper helper) {
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                boolean insideWriteSet = x >= 1 && x <= 6 && z >= 1 && z <= 6;
                if (!insideWriteSet) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
        }
    }

    private static BlockPos originRel() {
        return DreadPortalExitPlacement.originFor(ARRIVAL_REL);
    }

    private static int countPortalBlocks(GameTestHelper helper) {
        int count = 0;
        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 10; y++) {
                for (int z = 0; z < 12; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.DREAD_PORTAL.get())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 出口模板必须能被加载，并且预检能得到非空写集。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void exit_template_loads_and_plans(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ARRIVAL_REL);

        var result = DreadPortalExitPlacement.plan(
                helper.getLevel(), zombie,
                helper.absolutePos(originRel()),
                helper.absolutePos(WAITING_REL));

        helper.assertTrue(result.isSuccess(),
                "空旷平台上的出口预检应成功，实际失败原因=" + result.failure());

        var plan = result.plan();
        helper.assertTrue(plan.size() > 0, "写集不应为空");
        helper.assertTrue(plan.portalBlockCount() > 0,
                "写集中应包含传送门方块，实际 " + plan.portalBlockCount());

        zombie.discard();
        helper.succeed();
    }

    /**
     * 正常路径：预检通过后写入成功，且平台内确实出现传送门方块。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void placement_writes_portal_blocks(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ARRIVAL_REL);
        helper.assertTrue(countPortalBlocks(helper) == 0, "写入前平台内不应有传送门方块");

        var result = DreadPortalExitPlacement.plan(
                helper.getLevel(), zombie,
                helper.absolutePos(originRel()),
                helper.absolutePos(WAITING_REL));
        helper.assertTrue(result.isSuccess(), "预检应成功，实际=" + result.failure());

        boolean applied = DreadPortalExitPlacement.apply(result.plan());
        helper.assertTrue(applied, "写入应成功");
        helper.assertTrue(result.plan().isApplied(), "计划应被标记为已写入");
        helper.assertTrue(countPortalBlocks(helper) > 0, "写入后平台内应出现传送门方块");

        zombie.discard();
        helper.succeed();
    }

    /**
     * DP-002 核心：写集内有容器时预检必须失败，且不得改写任何方块。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void container_in_write_set_is_rejected(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        // 在写集内部放一个带物品的箱子。
        BlockPos chestRel = new BlockPos(4, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        var chest = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(chestRel));
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ARRIVAL_REL);
        var result = DreadPortalExitPlacement.plan(
                helper.getLevel(), zombie,
                helper.absolutePos(originRel()),
                helper.absolutePos(WAITING_REL));

        helper.assertFalse(result.isSuccess(),
                "写集内含容器时预检必须失败");
        helper.assertTrue(result.failure() == DreadPortalExitPlacement.Failure.PROTECTED_BLOCK,
                "失败原因应为 PROTECTED_BLOCK，实际=" + result.failure());

        // 预检不得产生任何写入。
        helper.assertTrue(countPortalBlocks(helper) == 0, "预检失败后不应出现传送门方块");
        helper.assertTrue(helper.getBlockState(chestRel).is(Blocks.CHEST),
                "箱子方块必须保持原样");
        var after = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chestRel));
        helper.assertTrue(after.getItem(0).getCount() == 7,
                "箱子内物品必须保持原样，实际=" + after.getItem(0).getCount());

        zombie.discard();
        helper.succeed();
    }

    /**
     * DP-002 核心：写集内有实心建筑时预检必须失败。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void solid_building_in_write_set_is_rejected(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        // 在写集内部砌一堵石砖墙。
        for (int y = 1; y <= 3; y++) {
            for (int z = 3; z <= 6; z++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE_BRICKS);
            }
        }

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ARRIVAL_REL);
        var result = DreadPortalExitPlacement.plan(
                helper.getLevel(), zombie,
                helper.absolutePos(originRel()),
                helper.absolutePos(WAITING_REL));

        helper.assertFalse(result.isSuccess(), "写集内含实心建筑时预检必须失败");
        helper.assertTrue(result.failure() == DreadPortalExitPlacement.Failure.PROTECTED_BLOCK,
                "失败原因应为 PROTECTED_BLOCK，实际=" + result.failure());

        // 建筑必须完好。
        for (int y = 1; y <= 3; y++) {
            for (int z = 3; z <= 6; z++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(5, y, z)).is(Blocks.STONE_BRICKS),
                        "石砖必须保持原样 @" + new BlockPos(5, y, z));
            }
        }

        zombie.discard();
        helper.succeed();
    }

    /**
     * 不可破坏方块（基岩）在写集内同样必须被拒绝。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void unbreakable_block_is_rejected(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        helper.setBlock(new BlockPos(4, 1, 5), Blocks.BEDROCK);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ARRIVAL_REL);
        var result = DreadPortalExitPlacement.plan(
                helper.getLevel(), zombie,
                helper.absolutePos(originRel()),
                helper.absolutePos(WAITING_REL));

        helper.assertFalse(result.isSuccess(), "写集内含基岩时预检必须失败");
        helper.assertTrue(result.failure() == DreadPortalExitPlacement.Failure.PROTECTED_BLOCK,
                "失败原因应为 PROTECTED_BLOCK，实际=" + result.failure());
        helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 5)).is(Blocks.BEDROCK),
                "基岩必须保持原样");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 回滚：写入后回滚必须把写集恢复为写入前状态，且不留残余门方块。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void rollback_restores_original_state(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ARRIVAL_REL);

        var result = DreadPortalExitPlacement.plan(
                helper.getLevel(), zombie,
                helper.absolutePos(originRel()),
                helper.absolutePos(WAITING_REL));
        helper.assertTrue(result.isSuccess(), "预检应成功，实际=" + result.failure());

        var plan = result.plan();
        // 记录写集在写入前的状态。
        var before = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (BlockPos abs : plan.positions()) {
            before.put(abs, helper.getLevel().getBlockState(abs));
        }

        helper.assertTrue(DreadPortalExitPlacement.apply(plan), "写入应成功");
        helper.assertTrue(countPortalBlocks(helper) > 0, "写入后应有门方块");

        DreadPortalExitPlacement.rollback(plan);

        for (var entry : before.entrySet()) {
            helper.assertTrue(helper.getLevel().getBlockState(entry.getKey()).equals(entry.getValue()),
                    "回滚后应恢复原状态 @" + entry.getKey());
        }
        helper.assertTrue(countPortalBlocks(helper) == 0, "回滚后不应残留门方块");
        helper.assertFalse(plan.isApplied(), "回滚后计划不应标记为已写入");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 等待点落在写集内时必须拒绝：否则失败兜底位置会被结构自身覆盖。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void waiting_position_inside_write_set_is_rejected(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ARRIVAL_REL);

        // 故意把等待点设在写集内部。
        var result = DreadPortalExitPlacement.plan(
                helper.getLevel(), zombie,
                helper.absolutePos(originRel()),
                helper.absolutePos(new BlockPos(4, 2, 4)));

        helper.assertFalse(result.isSuccess(), "等待点位于写集内时应拒绝");
        helper.assertTrue(result.failure() == DreadPortalExitPlacement.Failure.WAITING_POSITION_UNSAFE,
                "失败原因应为 WAITING_POSITION_UNSAFE，实际=" + result.failure());

        zombie.discard();
        helper.succeed();
    }

    /**
     * 受保护内容判定：容器与不可破坏方块必须被识别为受保护。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void protected_detection_covers_containers_and_bedrock(GameTestHelper helper) {
        layGroundOutsideWriteSet(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.FURNACE);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);

        helper.assertTrue(DreadPortalExitPlacement.isProtectedBlock(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))), "箱子应受保护");
        helper.assertTrue(DreadPortalExitPlacement.isProtectedBlock(
                helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1))), "基岩应受保护");
        helper.assertTrue(DreadPortalExitPlacement.isProtectedBlock(
                helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1))), "熔炉应受保护");
        helper.assertFalse(DreadPortalExitPlacement.isProtectedBlock(
                helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 1))), "石头不应受保护");

        helper.succeed();
    }
}
