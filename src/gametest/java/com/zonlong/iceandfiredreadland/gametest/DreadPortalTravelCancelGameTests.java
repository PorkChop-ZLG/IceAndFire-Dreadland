package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.registry.ModDimensions;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalTravelService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T4 补充：旅行事件被取消时的行为（DP-004）。
 *
 * <h2>这里能验证什么、不能验证什么</h2>
 * 悚域维度在 GameTestServer（FLAT 预设）中不存在，因此
 * {@link DreadPortalTravelService#attemptTravel} 会在解析目标维度时就返回
 * {@code DIMENSION_UNAVAILABLE}，走不到 {@code changeDimension} 那一步，
 * 无法直接构造出服务内部的 {@code TRAVEL_CANCELLED} 分支。
 *
 * <p>但该分支的正确性依赖一条可独立验证的机制：<b>NeoForge 的
 * {@code EntityTravelToDimensionEvent} 一旦被取消，{@code changeDimension} 就返回 null
 * 且实体不移动</b>。这正是「取消时目标维度零写入」的前提——服务在拿到 null 之后
 * 才会放弃放置出口。本类用一个只在测试期间注册的监听器直接验证这条机制，
 * 监听器在用例结束时必定注销，避免影响其他用例。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalTravelCancelGameTests {

    /** 共享的取消开关；只有显式置位时才取消旅行。 */
    private static volatile boolean cancelActive = false;

    private DreadPortalTravelCancelGameTests() {
    }

    /**
     * 事件被取消时，changeDimension 必须返回 null 且实体原地不动。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void cancelled_travel_leaves_entity_untouched(GameTestHelper helper) {
        BlockPos feetRel = new BlockPos(2, 1, 2);
        helper.setBlock(feetRel, ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, feetRel);

        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "测试前置：下界维度在 GameTestServer 中应存在");

        BlockPos before = zombie.blockPosition();
        double beforeX = zombie.getX();
        double beforeZ = zombie.getZ();

        var listener = new Object() {
            @SubscribeEvent
            public void onTravel(EntityTravelToDimensionEvent event) {
                if (cancelActive) {
                    event.setCanceled(true);
                }
            }
        };
        NeoForge.EVENT_BUS.register(listener);

        try {
            cancelActive = true;

            DimensionTransition transition = new DimensionTransition(
                    nether,
                    Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4))),
                    Vec3.ZERO,
                    zombie.getYRot(),
                    zombie.getXRot(),
                    DimensionTransition.DO_NOTHING);

            var moved = zombie.changeDimension(transition);

            helper.assertTrue(moved == null,
                    "旅行事件被取消时 changeDimension 必须返回 null，实际=" + moved);
            helper.assertTrue(zombie.blockPosition().equals(before),
                    "取消后实体不应移动，实际=" + zombie.blockPosition() + " 期望=" + before);
            helper.assertTrue(zombie.getX() == beforeX && zombie.getZ() == beforeZ,
                    "取消后实体坐标不应变化");
            helper.assertTrue(zombie.level().dimension().equals(Level.OVERWORLD),
                    "取消后实体应仍在原维度，实际=" + zombie.level().dimension().location());
        } finally {
            // 必须注销，否则会污染后续用例的旅行行为。
            NeoForge.EVENT_BUS.unregister(listener);
            cancelActive = false;
        }

        zombie.discard();
        helper.succeed();
    }

    /**
     * 资格检查必须在 changeDimension 之前完成，因为后者内部会调用 unRide()。
     * 这里确认载客实体在服务入口就被拒绝，不会走到拆乘。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void eligibility_precedes_unride(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie vehicle = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie rider = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        rider.startRiding(vehicle, true);

        helper.assertTrue(!vehicle.getPassengers().isEmpty(), "测试前置：应有乘客");

        var outcome = DreadPortalTravelService.attemptTravel(vehicle);

        helper.assertTrue(outcome == DreadPortalTravelService.Outcome.INELIGIBLE,
                "载客实体应在服务入口被拒绝，实际=" + outcome);
        helper.assertTrue(!vehicle.getPassengers().isEmpty(),
                "被拒绝后不得发生拆乘");

        rider.stopRiding();
        rider.discard();
        vehicle.discard();
        helper.succeed();
    }

    /** 悚域维度在 GameTest 环境确实缺席，作为上述限制的依据。 */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void dreadland_absent_limits_travel_coverage(GameTestHelper helper) {
        var dreadland = helper.getLevel().getServer().getLevel(ModDimensions.DREADLAND);
        helper.assertTrue(dreadland == null,
                "预期 FLAT 预设不加载悚域维度；若此处不为 null，应补充真实跨维度成功用例");
        helper.succeed();
    }
}
