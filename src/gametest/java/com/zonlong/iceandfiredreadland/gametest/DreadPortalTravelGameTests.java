package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.data.component.PortalData;
import com.zonlong.iceandfiredreadland.registry.ModAttachments;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.registry.ModDimensions;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalTravelService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T4：旅行服务接线、失败处理与实体资格（DP-001 / DP-002 / DP-004 / DP-005 / DP-009）。
 *
 * <h2>环境限制（务必先读）</h2>
 * GameTestServer 用 {@code WorldPresets.FLAT} 构建世界，实测其注册的 level stem 只有
 * {@code minecraft:overworld}、{@code minecraft:the_end}、{@code minecraft:the_nether}，
 * <b>不含</b>本模组的 {@code iceandfire_dreadland:dreadland}。因此 {@code server.getLevel(DREADLAND)}
 * 在这里恒为 null，<b>无法在本环境自动化验证真实的跨维度往返</b>。
 *
 * <p>本类因此聚焦两件确实可验证的事：
 * <ol>
 *   <li>资格与失败路径的<b>无副作用保证</b>：被拒绝的实体不移动、不拆乘，且不写入任何结构；</li>
 *   <li>维度不可用时返回明确原因而不是抛异常或乱传送。</li>
 * </ol>
 *
 * <p>真实跨维度成功路径（玩家与普通生物抵达、出口生成与回滚、返程）需要在正式客户端/服务端
 * 实测，无法在本套用例中声称通过。坐标约定同前：面向 level 的调用必须经
 * {@code helper.absolutePos(...)}。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalTravelGameTests {

    private DreadPortalTravelGameTests() {
    }

    /** 前置：本环境应有主世界，且悚域维度确实缺席（后续用例据此断言）。 */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void environment_precondition(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getLevel(Level.OVERWORLD) != null, "主世界应存在");
        ServerLevel dreadland = server.getLevel(ModDimensions.DREADLAND);
        helper.assertTrue(dreadland == null,
                "GameTestServer 使用 FLAT 预设，预期不加载悚域维度；若此处不为 null，"
                        + "说明环境已变化，应补充真实跨维度用例");
        helper.succeed();
    }

    /**
     * DP-004：目标维度缺失时必须返回明确原因，不得抛异常，也不得移动实体。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void missing_dimension_reports_unavailable(GameTestHelper helper) {
        BlockPos feetRel = new BlockPos(2, 1, 2);
        helper.setBlock(feetRel, ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, feetRel);
        BlockPos before = zombie.blockPosition();

        DreadPortalTravelService.Outcome outcome = DreadPortalTravelService.attemptTravel(zombie);

        helper.assertTrue(outcome == DreadPortalTravelService.Outcome.DIMENSION_UNAVAILABLE,
                "悚域维度缺席时应返回 DIMENSION_UNAVAILABLE，实际=" + outcome);
        helper.assertTrue(zombie.blockPosition().equals(before),
                "失败后实体不应移动，实际=" + zombie.blockPosition() + " 期望=" + before);
        helper.assertTrue(zombie.isAlive(), "失败后实体应仍然存活");

        zombie.discard();
        helper.succeed();
    }

    /**
     * DP-009：乘客必须被拒绝，且不得因 changeDimension 的 unRide 被拆乘。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void passenger_is_ineligible_and_not_dismounted(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie vehicle = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie rider = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        rider.startRiding(vehicle, true);

        helper.assertTrue(rider.isPassenger(), "测试前置：骑手应处于骑乘状态");
        BlockPos before = rider.blockPosition();

        var outcome = DreadPortalTravelService.attemptTravel(rider);

        helper.assertTrue(outcome == DreadPortalTravelService.Outcome.INELIGIBLE,
                "乘客应被判为不合格，实际=" + outcome);
        helper.assertTrue(rider.isPassenger(), "被拒绝后不得拆乘");
        helper.assertTrue(rider.blockPosition().equals(before), "被拒绝后实体不应移动");

        rider.stopRiding();
        rider.discard();
        vehicle.discard();
        helper.succeed();
    }

    /**
     * DP-009：载有乘客的实体本身也必须被拒绝，避免把载具和乘客一起送走。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void entity_carrying_passengers_is_ineligible(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie vehicle = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Zombie rider = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        rider.startRiding(vehicle, true);

        var outcome = DreadPortalTravelService.attemptTravel(vehicle);

        helper.assertTrue(outcome == DreadPortalTravelService.Outcome.INELIGIBLE,
                "载客实体应被判为不合格，实际=" + outcome);
        helper.assertTrue(vehicle.isVehicle(), "被拒绝后乘客不应被移除");

        rider.stopRiding();
        rider.discard();
        vehicle.discard();
        helper.succeed();
    }

    /**
     * 已死亡的实体不应被传送。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void dead_entity_is_ineligible(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.kill();

        var outcome = DreadPortalTravelService.attemptTravel(zombie);

        helper.assertTrue(outcome == DreadPortalTravelService.Outcome.INELIGIBLE,
                "死亡实体应被判为不合格，实际=" + outcome);

        zombie.discard();
        helper.succeed();
    }

    /**
     * N=0 的配置必须在归零的那一帧就提交迁移，不额外多等一帧。
     *
     * <p>不直接改 Jupiter 配置项（其 entry 无普通 setter），而是把内部计数置为 0
     * 后驱动一次 tick，断言「0 值不会被留在 COUNTING 继续空转」。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void zero_count_commits_without_extra_wait(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        PortalData data = zombie.getData(ModAttachments.PORTAL_DATA);

        data.setStateForTesting(PortalData.State.COUNTING);
        data.setTeleportTickForTesting(0);

        PortalData.TickResult result = data.tick(zombie, true);

        // 允许两种结局：迁移成功（SUCCESS_WAIT）或失败后复位重试（COUNTING + TRAVEL_FAILED）。
        // 两者都表示「0 值已在本次更新内被消费」，不会原地空转。
        boolean consumed = data.getState() == PortalData.State.SUCCESS_WAIT
                || result == PortalData.TickResult.TRAVEL_FAILED;
        helper.assertTrue(consumed,
                "计数为 0 时应在本次更新内提交，实际 state=" + data.getState() + " result=" + result);
        helper.assertFalse(data.getTeleportTick() == 0,
                "提交后不应残留 0 值计数，实际=" + data.getTeleportTick());

        zombie.discard();
        helper.succeed();
    }

    /**
     * 配置范围必须是 0–600，且默认值 100。这是计划要求保留的既有配置契约。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void config_contract_is_preserved(GameTestHelper helper) {
        var entry = com.zonlong.iceandfiredreadland.config.ModCommonConfig.INSTANCE.portalTeleportTick;
        helper.assertTrue(entry.getMinValue() == 0,
                "portalTeleportTick 最小值应为 0，实际=" + entry.getMinValue());
        helper.assertTrue(entry.getMaxValue() == 600,
                "portalTeleportTick 最大值应为 600，实际=" + entry.getMaxValue());
        helper.succeed();
    }

    /**
     * DP-005：归零后离开传送门不得触发迁移（状态机层面，不依赖维度可用性）。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void leave_after_zero_does_not_dispatch_travel(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        PortalData data = zombie.getData(ModAttachments.PORTAL_DATA);

        data.setStateForTesting(PortalData.State.COUNTING);
        data.setTeleportTickForTesting(0);

        // 本帧已经离门：必须先复位，而不是依据旧零值提交。
        var result = data.tick(zombie, false);

        helper.assertTrue(result == PortalData.TickResult.REMOVE,
                "离门应要求移除附件，实际=" + result);
        helper.assertTrue(data.getState() == PortalData.State.IDLE,
                "离门后应为 IDLE，实际=" + data.getState());
        helper.assertTrue(data.getTeleportTick() == -1,
                "离门后必须丢弃零值计数，实际=" + data.getTeleportTick());

        zombie.discard();
        helper.succeed();
    }

    /**
     * 迁移中状态在离门时必须被清理，不能卡死在 TRAVELLING。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void travelling_state_is_cleared_when_leaving(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        PortalData data = zombie.getData(ModAttachments.PORTAL_DATA);

        data.setStateForTesting(PortalData.State.TRAVELLING);
        var result = data.tick(zombie, false);

        helper.assertTrue(result == PortalData.TickResult.REMOVE, "应为 REMOVE，实际=" + result);
        helper.assertTrue(data.getState() == PortalData.State.IDLE,
                "应回到 IDLE，实际=" + data.getState());

        zombie.discard();
        helper.succeed();
    }

    /**
     * 无接触时不得推进权威计时，也不得触发迁移。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void no_contact_clears_state_without_travel(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 3, 2));
        PortalData data = zombie.getData(ModAttachments.PORTAL_DATA);
        data.setStateForTesting(PortalData.State.COUNTING);
        data.setTeleportTickForTesting(5);

        helper.assertFalse(PortalData.isInPortal(helper.getLevel(), zombie),
                "测试前置：实体不应在门内");

        var result = data.tick(zombie, false);
        helper.assertTrue(result == PortalData.TickResult.REMOVE, "应为 REMOVE，实际=" + result);
        helper.assertTrue(data.getTeleportTick() == -1, "无接触时必须丢弃计数");

        zombie.discard();
        helper.succeed();
    }
}
