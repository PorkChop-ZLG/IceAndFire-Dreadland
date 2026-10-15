package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T5：门块内部面剔除的几何用例（DP-007）。
 *
 * <p>只覆盖公共端的几何判定（{@link DreadPortalGeometry}）。RenderType 的缓存身份
 * 依赖客户端类（{@code RenderType}/{@code CompositeRenderType}），而 GameTestServer 是
 * 专用服务端环境，加载该客户端类会失败，因此「连续调用返回同一实例」只能在客户端实测确认。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalGeometryGameTests {

    /** 完整门面：X 轴 3 宽 × 5 高，位于 z=2 的平面上。 */
    private static final int WALL_WIDTH = 3;
    private static final int WALL_HEIGHT = 5;
    private static final int WALL_Z = 2;

    private DreadPortalGeometryGameTests() {
    }

    /** 放置一个 X 轴门面，返回实际放置的方块数。 */
    private static int placeWall(GameTestHelper helper) {
        int placed = 0;
        for (int x = 1; x <= WALL_WIDTH; x++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                helper.setBlock(new BlockPos(x, y, WALL_Z), ModBlocks.DREAD_PORTAL.get());
                placed++;
            }
        }
        return placed;
    }

    /**
     * 孤立门块没有任何相邻门块，必须提交全部 6 个面。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void isolated_portal_submits_all_six_faces(GameTestHelper helper) {
        BlockPos rel = new BlockPos(4, 2, 4);
        helper.setBlock(rel, ModBlocks.DREAD_PORTAL.get());
        BlockPos abs = helper.absolutePos(rel);

        helper.assertTrue(
                DreadPortalGeometry.submittedFaceCount(helper.getLevel(), abs) == 6,
                "孤立门块应提交 6 个面，实际="
                        + DreadPortalGeometry.submittedFaceCount(helper.getLevel(), abs));
        helper.assertTrue(DreadPortalGeometry.unclippedFaceCount() == 6,
                "未剔除时的基准面数应为 6");

        helper.succeed();
    }

    /**
     * 非门方块的邻居不得导致剔除。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void non_portal_neighbours_do_not_cull(GameTestHelper helper) {
        BlockPos rel = new BlockPos(4, 2, 4);
        helper.setBlock(rel, ModBlocks.DREAD_PORTAL.get());
        // 六个面全部换成石头。
        for (Direction direction : Direction.values()) {
            helper.setBlock(rel.relative(direction), Blocks.STONE);
        }
        BlockPos abs = helper.absolutePos(rel);

        helper.assertTrue(
                DreadPortalGeometry.submittedFaceCount(helper.getLevel(), abs) == 6,
                "相邻方块不是门时不应剔除任何面，实际="
                        + DreadPortalGeometry.submittedFaceCount(helper.getLevel(), abs));

        helper.succeed();
    }

    /**
     * X 轴门面：3×5 实心墙上每个门块只保留法线方向的 2 个面。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void x_axis_wall_keeps_only_outer_faces(GameTestHelper helper) {
        int placed = placeWall(helper);
        helper.assertTrue(placed == WALL_WIDTH * WALL_HEIGHT,
                "测试前置：应放置 " + (WALL_WIDTH * WALL_HEIGHT) + " 个门块");

        // 墙体内部的方块（x 与 y 都非边缘）应只剩南北两面。
        BlockPos innerRel = new BlockPos(2, 3, WALL_Z);
        BlockPos innerAbs = helper.absolutePos(innerRel);
        int innerFaces = DreadPortalGeometry.submittedFaceCount(helper.getLevel(), innerAbs);
        helper.assertTrue(innerFaces == 2,
                "墙体内部门块应只提交 2 个面，实际=" + innerFaces);

        // 汇总：90 个面被剔除到 46 个（外表面），即剔除 44 个内部面。
        int totalFaces = 0;
        for (int x = 1; x <= WALL_WIDTH; x++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                totalFaces += DreadPortalGeometry.submittedFaceCount(
                        helper.getLevel(), helper.absolutePos(new BlockPos(x, y, WALL_Z)));
            }
        }
        int unclipped = 6 * placed;
        helper.assertTrue(totalFaces == 46,
                "3×5 实心墙应提交 46 个外表面，实际=" + totalFaces);
        helper.assertTrue(unclipped == 90,
                "未剔除时应为 90 个面，实际=" + unclipped);
        helper.assertTrue(unclipped - totalFaces == 44,
                "应剔除 44 个内部面，实际=" + (unclipped - totalFaces));

        helper.succeed();
    }

    /**
     * Z 轴门面必须与 X 轴同样处理，不能只对一种朝向生效。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void z_axis_wall_is_culled_symmetrically(GameTestHelper helper) {
        int placed = 0;
        for (int z = 1; z <= WALL_WIDTH; z++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                helper.setBlock(new BlockPos(4, y, z), ModBlocks.DREAD_PORTAL.get());
                placed++;
            }
        }

        int totalFaces = 0;
        for (int z = 1; z <= WALL_WIDTH; z++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                totalFaces += DreadPortalGeometry.submittedFaceCount(
                        helper.getLevel(), helper.absolutePos(new BlockPos(4, y, z)));
            }
        }

        helper.assertTrue(totalFaces == 46,
                "Z 轴 3×5 实心墙应同样提交 46 个外表面，实际=" + totalFaces);
        helper.assertTrue(6 * placed - totalFaces == 44,
                "Z 轴应同样剔除 44 个内部面，实际=" + (6 * placed - totalFaces));

        helper.succeed();
    }

    /**
     * 剔除是对称的：共享面在两侧都被剔除，因此同一对相邻门块之间的面不会残留。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void shared_face_is_culled_on_both_sides(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 2, 3);
        BlockPos b = a.east();
        helper.setBlock(a, ModBlocks.DREAD_PORTAL.get());
        helper.setBlock(b, ModBlocks.DREAD_PORTAL.get());

        BlockPos absA = helper.absolutePos(a);
        BlockPos absB = helper.absolutePos(b);

        helper.assertTrue(
                DreadPortalGeometry.isPortal(helper.getLevel(), absA, Direction.EAST),
                "a 的东侧应识别为门");
        helper.assertTrue(
                DreadPortalGeometry.isPortal(helper.getLevel(), absB, Direction.WEST),
                "b 的西侧应识别为门");
        helper.assertTrue(DreadPortalGeometry.submittedFaceCount(helper.getLevel(), absA) == 5,
                "相邻一块时 a 应提交 5 个面，实际="
                        + DreadPortalGeometry.submittedFaceCount(helper.getLevel(), absA));
        helper.assertTrue(DreadPortalGeometry.submittedFaceCount(helper.getLevel(), absB) == 5,
                "相邻一块时 b 应提交 5 个面，实际="
                        + DreadPortalGeometry.submittedFaceCount(helper.getLevel(), absB));

        helper.succeed();
    }
}
