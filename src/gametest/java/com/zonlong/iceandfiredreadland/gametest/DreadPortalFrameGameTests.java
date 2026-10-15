package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.block.DreadPortalBlock;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T8：固定框架与旧式无框出口的回归用例。
 *
 * <p>计划要求保留：固定外框 5×7 / 内部 3×5、X 与 Z 两种朝向、可省略四角、混合框架标签方块，
 * 以及 {@code frame_bound=true} 的拆框清理；同时旧式无框出口（{@code frame_bound=false}）
 * 必须继续独立存在。
 *
 * <p>框架方块取自 {@code #iceandfire_dreadland:dreadland_portal_frame} 标签，
 * 实际方块由前置模组 Ice And Fire 提供。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalFrameGameTests {

    /** X 轴门：内部 3 宽 × 5 高，位于 z=3。 */
    private static final int Z_PLANE = 3;

    private DreadPortalFrameGameTests() {
    }

    /** 从标签里取第一个可用的框架方块。 */
    private static Block frameBlock(GameTestHelper helper) {
        var tag = BuiltInRegistries.BLOCK.getTag(
                com.zonlong.iceandfiredreadland.registry.tag.ModBlockTags.DREADLAND_PORTAL_FRAME);
        helper.assertTrue(tag.isPresent(), "框架标签必须存在");
        var entries = tag.get().stream().toList();
        helper.assertTrue(!entries.isEmpty(), "框架标签不得为空");
        return entries.get(0).value();
    }

    private static Block frameBlockAt(GameTestHelper helper, int index) {
        var entries = BuiltInRegistries.BLOCK.getTag(
                com.zonlong.iceandfiredreadland.registry.tag.ModBlockTags.DREADLAND_PORTAL_FRAME)
                .orElseThrow().stream().toList();
        return entries.get(index % entries.size()).value();
    }

    /**
     * 按 X 轴建造一个内部 3×5 的完整框架（含四角），返回内部左下角。
     */
    private static BlockPos buildXFrame(GameTestHelper helper, boolean includeCorners) {
        Block frame = frameBlock(helper);
        int leftX = 1;
        int rightX = leftX + DreadPortalShape.MAX_WIDTH + 1; // 内部宽 3，故右框在 +4
        int bottomY = 1;
        int topY = bottomY + DreadPortalShape.MAX_HEIGHT;    // 内部高 5，故上框在 +5
        int z = Z_PLANE;

        for (int y = bottomY; y <= topY; y++) {
            helper.setBlock(new BlockPos(leftX, y, z), frame);
            helper.setBlock(new BlockPos(rightX, y, z), frame);
        }
        for (int x = leftX; x <= rightX; x++) {
            helper.setBlock(new BlockPos(x, bottomY - 1, z), frame);
            helper.setBlock(new BlockPos(x, topY, z), frame);
        }
        if (!includeCorners) {
            // 四个角可以省略：把角上的框架方块去掉。
            helper.setBlock(new BlockPos(leftX, topY, z), Blocks.AIR);
            helper.setBlock(new BlockPos(rightX, topY, z), Blocks.AIR);
            helper.setBlock(new BlockPos(leftX, bottomY - 1, z), Blocks.AIR);
            helper.setBlock(new BlockPos(rightX, bottomY - 1, z), Blocks.AIR);
        }
        return new BlockPos(leftX + 1, bottomY, z);
    }

    /**
     * 完整框架应被识别为有效，并能创建框架依赖的门方块。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void complete_frame_creates_frame_bound_portal(GameTestHelper helper) {
        BlockPos interiorRel = buildXFrame(helper, true);
        BlockPos interiorAbs = helper.absolutePos(interiorRel);

        var shapeOpt = DreadPortalShape.findEmptyPortalShape(
                helper.getLevel(), interiorAbs, Direction.Axis.X);
        helper.assertTrue(shapeOpt.isPresent(), "内部 3×5 的完整框架应被识别为有效门形");

        shapeOpt.get().createPortalBlocks();

        int created = 0;
        for (int dx = 0; dx < DreadPortalShape.MAX_WIDTH; dx++) {
            for (int dy = 0; dy < DreadPortalShape.MAX_HEIGHT; dy++) {
                BlockState state = helper.getBlockState(interiorRel.offset(dx, dy, 0));
                if (state.is(ModBlocks.DREAD_PORTAL.get())) {
                    created++;
                    helper.assertTrue(state.getValue(DreadPortalBlock.FRAME_BOUND),
                            "框架生成的门方块必须标记 frame_bound=true @" + interiorRel.offset(dx, dy, 0));
                    helper.assertTrue(state.getValue(DreadPortalBlock.AXIS) == Direction.Axis.X,
                            "门方块轴向应为 X");
                }
            }
        }
        helper.assertTrue(created == DreadPortalShape.MAX_WIDTH * DreadPortalShape.MAX_HEIGHT,
                "应创建 " + (DreadPortalShape.MAX_WIDTH * DreadPortalShape.MAX_HEIGHT)
                        + " 个门方块，实际=" + created);

        helper.succeed();
    }

    /**
     * 拆框清理：破坏框架后，依赖框架的门方块必须消失。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void breaking_frame_removes_bound_portal(GameTestHelper helper) {
        BlockPos interiorRel = buildXFrame(helper, true);
        BlockPos interiorAbs = helper.absolutePos(interiorRel);

        var shapeOpt = DreadPortalShape.findEmptyPortalShape(
                helper.getLevel(), interiorAbs, Direction.Axis.X);
        helper.assertTrue(shapeOpt.isPresent(), "测试前置：框架应有效");
        shapeOpt.get().createPortalBlocks();

        helper.assertTrue(helper.getBlockState(interiorRel).is(ModBlocks.DREAD_PORTAL.get()),
                "测试前置：门方块应已创建");

        // 破坏右侧竖直框的一格，触发邻居更新。
        BlockPos frameRel = new BlockPos(1 + DreadPortalShape.MAX_WIDTH + 1, 3, Z_PLANE);
        helper.setBlock(frameRel, Blocks.AIR);
        // 门块通过 updateShape 级联消失，需要触发一次邻居更新。
        helper.getLevel().updateNeighborsAt(helper.absolutePos(frameRel), Blocks.AIR);

        helper.assertFalse(helper.getBlockState(interiorRel).is(ModBlocks.DREAD_PORTAL.get()),
                "拆框后依赖框架的门方块应消失，实际=" + helper.getBlockState(interiorRel));

        helper.succeed();
    }

    /**
     * 旧式无框出口（frame_bound=false）不得被拆框逻辑清理。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void legacy_standalone_portal_survives_frame_break(GameTestHelper helper) {
        BlockPos portalRel = new BlockPos(4, 2, 4);
        BlockState legacy = ModBlocks.DREAD_PORTAL.get().defaultBlockState()
                .setValue(DreadPortalBlock.AXIS, Direction.Axis.X)
                .setValue(DreadPortalBlock.FRAME_BOUND, false);
        helper.setBlock(portalRel, legacy);

        BlockPos neighbourRel = portalRel.east();
        helper.setBlock(neighbourRel, Blocks.STONE);
        helper.setBlock(neighbourRel, Blocks.AIR);
        helper.getLevel().updateNeighborsAt(helper.absolutePos(neighbourRel), Blocks.AIR);

        helper.assertTrue(helper.getBlockState(portalRel).is(ModBlocks.DREAD_PORTAL.get()),
                "旧式无框出口不应被拆框逻辑清理，实际=" + helper.getBlockState(portalRel));

        helper.succeed();
    }

    /**
     * 四角可省略：缺角的框架仍应被识别为有效。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void corners_may_be_omitted(GameTestHelper helper) {
        BlockPos interiorRel = buildXFrame(helper, false);
        BlockPos interiorAbs = helper.absolutePos(interiorRel);

        var shapeOpt = DreadPortalShape.findEmptyPortalShape(
                helper.getLevel(), interiorAbs, Direction.Axis.X);
        helper.assertTrue(shapeOpt.isPresent(), "省略四角的框架仍应有效");

        helper.succeed();
    }

    /**
     * 混合标签方块：同一框架可以混用标签内的不同方块。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void mixed_tag_blocks_form_valid_frame(GameTestHelper helper) {
        int leftX = 1;
        int rightX = leftX + DreadPortalShape.MAX_WIDTH + 1;
        int bottomY = 1;
        int topY = bottomY + DreadPortalShape.MAX_HEIGHT;
        int z = Z_PLANE;

        // 左右竖框交替使用标签内不同方块。
        int index = 0;
        for (int y = bottomY; y <= topY; y++) {
            helper.setBlock(new BlockPos(leftX, y, z), frameBlockAt(helper, index++));
            helper.setBlock(new BlockPos(rightX, y, z), frameBlockAt(helper, index++));
        }
        for (int x = leftX; x <= rightX; x++) {
            helper.setBlock(new BlockPos(x, bottomY - 1, z), frameBlockAt(helper, index++));
            helper.setBlock(new BlockPos(x, topY, z), frameBlockAt(helper, index++));
        }

        var shapeOpt = DreadPortalShape.findEmptyPortalShape(
                helper.getLevel(), helper.absolutePos(new BlockPos(leftX + 1, bottomY, z)),
                Direction.Axis.X);
        helper.assertTrue(shapeOpt.isPresent(), "混用标签内不同方块的框架应仍然有效");

        helper.succeed();
    }

    /**
     * 尺寸不符的框架必须被拒绝：这里内部高度只有 4，不足 5。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void wrong_dimensions_are_rejected(GameTestHelper helper) {
        Block frame = frameBlock(helper);
        int leftX = 1;
        int rightX = leftX + DreadPortalShape.MAX_WIDTH + 1;
        int bottomY = 1;
        // 故意只建到内部高度 4（少一格）。
        int topY = bottomY + DreadPortalShape.MAX_HEIGHT - 1;
        int z = Z_PLANE;

        for (int y = bottomY; y <= topY; y++) {
            helper.setBlock(new BlockPos(leftX, y, z), frame);
            helper.setBlock(new BlockPos(rightX, y, z), frame);
        }
        for (int x = leftX; x <= rightX; x++) {
            helper.setBlock(new BlockPos(x, bottomY - 1, z), frame);
            helper.setBlock(new BlockPos(x, topY, z), frame);
        }

        var shapeOpt = DreadPortalShape.findEmptyPortalShape(
                helper.getLevel(), helper.absolutePos(new BlockPos(leftX + 1, bottomY, z)),
                Direction.Axis.X);
        helper.assertFalse(shapeOpt.isPresent(), "内部高度不足 5 的框架不应被识别为有效");

        helper.succeed();
    }

    /**
     * 保留既有注册 ID：悚域传送门方块必须仍以该 ID 注册。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void portal_registry_id_is_preserved(GameTestHelper helper) {
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(
                IceAndFireDreadLand.MODID, "dread_portal");
        helper.assertTrue(BuiltInRegistries.BLOCK.getOptional(expected).isPresent(),
                "悚域传送门必须以 iceandfire_dreadland:dread_portal 注册");
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(ModBlocks.DREAD_PORTAL.get()).equals(expected),
                "注册 ID 必须保持不变，实际=" + BuiltInRegistries.BLOCK.getKey(ModBlocks.DREAD_PORTAL.get()));
        helper.succeed();
    }
}
