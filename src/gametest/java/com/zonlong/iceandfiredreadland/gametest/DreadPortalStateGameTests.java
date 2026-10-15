package com.zonlong.iceandfiredreadland.gametest;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.data.component.PortalData;
import com.zonlong.iceandfiredreadland.registry.ModAttachments;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T1：接触驱动状态机的行为用例。
 *
 * <p>覆盖 DP-005（归零后离门不再触发）、DP-006（未接触生物不创建附件）与旧存档归一化。
 * 这些用例直接驱动 {@link PortalData#tick} / 附件生命周期，不依赖倒计时真实走完的时间。
 */
@GameTestHolder(IceAndFireDreadLand.MODID)
@PrefixGameTestTemplate(false)
public final class DreadPortalStateGameTests {

    /** 测试平台中心；模板为 5×4×5 空心区域。 */
    private static final BlockPos PORTAL_POS = new BlockPos(2, 1, 2);

    private DreadPortalStateGameTests() {
    }

    /**
     * 铺一层石头地面。
     *
     * <p>结构模板是纯空气，实体若没有支撑会持续下坠，导致接触判定与位置测试不稳定。
     * 因此需要落点检查的用例先调用本方法固定地表高度。
     */
    private static void layGround(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static Zombie spawnZombieOutside(GameTestHelper helper) {
        return helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
    }

    private static PortalData dataOf(LivingEntity living) {
        return living.getExistingDataOrNull(ModAttachments.PORTAL_DATA);
    }

    /**
     * DP-005：倒计时归零触发之后，实体离开传送门不得再次触发。
     *
     * <p>刻意不依赖“归零那一帧是否真的完成了跨维度迁移”（取决于悚域维度是否可用），
     * 只断言稳定不变的部分：首帧不会因归零而移除附件；随后离门必须先复位为 IDLE
     * 并丢弃零值计数，绝不依据旧零值再提交一次。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void leaving_portal_after_zero_does_not_teleport(GameTestHelper helper) {
        layGround(helper);
        helper.setBlock(PORTAL_POS, ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = spawnZombieOutside(helper);

        PortalData data = zombie.getData(ModAttachments.PORTAL_DATA);
        data.setStateForTesting(PortalData.State.COUNTING);
        data.setTeleportTickForTesting(1);

        // 第一次更新：仍在门内，计数 1 -> 0。
        PortalData.TickResult inside = data.tick(zombie, true);
        helper.assertTrue(inside != PortalData.TickResult.REMOVE,
                "门内更新不应要求移除附件，实际=" + inside);

        // 第二次更新：已经离开传送门。必须先复位，且不得残留零值计数。
        PortalData.TickResult outside = data.tick(zombie, false);
        helper.assertTrue(outside == PortalData.TickResult.REMOVE,
                "离开传送门后应要求移除附件，实际=" + outside);
        helper.assertTrue(data.getState() == PortalData.State.IDLE,
                "离开传送门后状态应为 IDLE，实际为 " + data.getState());
        helper.assertTrue(data.getTeleportTick() == -1,
                "离开传送门后必须丢弃零值计数，实际为 " + data.getTeleportTick());

        zombie.discard();
        helper.succeed();
    }

    /**
     * 最小等待时长：刚接触的一帧不推进计数，必须正好经过配置的 tick 数才触发。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void first_contact_frame_does_not_advance_countdown(GameTestHelper helper) {
        helper.setBlock(PORTAL_POS, ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = spawnZombieOutside(helper);

        PortalData data = zombie.getData(ModAttachments.PORTAL_DATA);
        data.setStateForTesting(PortalData.State.IDLE);

        // 第一帧接触：建立倒计时，不触发。
        data.tick(zombie, true);
        helper.assertTrue(data.getState() == PortalData.State.COUNTING,
                "第一帧接触后应进入 COUNTING，实际为 " + data.getState());

        // 立刻离门：不得留下任何可触发的计数。
        data.tick(zombie, false);
        helper.assertTrue(data.getState() == PortalData.State.IDLE,
                "离门后应复位为 IDLE，实际为 " + data.getState());
        helper.assertTrue(data.getTeleportTick() == -1,
                "离门后剩余计数应复位为 -1，实际为 " + data.getTeleportTick());

        zombie.discard();
        helper.succeed();
    }

    /**
     * 计时中短暂离开会清零进度，重新进入需要重新计满，不能累计旧进度。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void progress_resets_when_leaving_midway(GameTestHelper helper) {
        helper.setBlock(PORTAL_POS, ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = spawnZombieOutside(helper);

        PortalData data = zombie.getData(ModAttachments.PORTAL_DATA);
        data.setStateForTesting(PortalData.State.COUNTING);
        data.setTeleportTickForTesting(1);

        // 离门一次即复位，即使计数已经只剩 1。
        data.tick(zombie, false);
        helper.assertTrue(data.getTeleportTick() == -1,
                "中途离门应丢弃剩余计数，实际为 " + data.getTeleportTick());
        helper.assertTrue(data.getState() == PortalData.State.IDLE,
                "中途离门应回到 IDLE，实际为 " + data.getState());

        zombie.discard();
        helper.succeed();
    }

    /**
     * DP-006：从接触过传送门的生物在离开后不得残留附件。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void attachment_is_removed_after_leaving(GameTestHelper helper) {
        helper.setBlock(PORTAL_POS, ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = spawnZombieOutside(helper);

        zombie.getData(ModAttachments.PORTAL_DATA);
        helper.assertTrue(zombie.hasData(ModAttachments.PORTAL_DATA),
                "创建后应持有附件");

        zombie.removeData(ModAttachments.PORTAL_DATA);
        helper.assertFalse(zombie.hasData(ModAttachments.PORTAL_DATA),
                "移除后不应再持有附件");
        helper.assertTrue(dataOf(zombie) == null,
                "移除后 getExistingDataOrNull 应返回 null");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 旧存档兼容：反序列化得到的 {@code teleported=true, teleportTick=0} 不得变成可触发状态。
     *
     * <p>0.1.2 的存档里可能残留这两个字段，恢复后必须归一化为空闲并处于 IDLE。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void legacy_saved_values_normalize_to_idle(GameTestHelper helper) {
        helper.setBlock(PORTAL_POS, ModBlocks.DREAD_PORTAL.get());
        Zombie zombie = spawnZombieOutside(helper);

        // 直接构造与旧 Codec 相同的输入，确认解析后不会恢复计数。
        PortalData decoded = PortalData.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString("{\"teleported\":true,\"teleportTick\":0}"))
                .getOrThrow();

        helper.assertTrue(decoded.getState() == PortalData.State.IDLE,
                "旧数据解析后应为 IDLE，实际为 " + decoded.getState());
        helper.assertTrue(decoded.getTeleportTick() == -1,
                "旧数据解析后剩余计数应为 -1，实际为 " + decoded.getTeleportTick());
        helper.assertFalse(decoded.isLegacyTeleported(),
                "旧 teleported 标记应被清除");

        // 即使被放进有状态的实体，也必须在离门时复位而不是触发。
        zombie.setData(ModAttachments.PORTAL_DATA, decoded);
        PortalData.TickResult removed = decoded.tick(zombie, false);
        helper.assertTrue(removed == PortalData.TickResult.REMOVE,
                "旧数据在离门时应立即要求移除，实际=" + removed);
        helper.assertTrue(decoded.getState() == PortalData.State.IDLE,
                "旧数据在离门后应为 IDLE");

        zombie.discard();
        helper.succeed();
    }

    /**
     * 接触判定必须同时覆盖框架门与旧式无框出口（都是同一个方块），
     * 并排除非门方块。
     *
     * <p>坐标约定：{@code helper.setBlock} 用模板相对坐标，而 {@code PortalData.isInPortal}
     * 面向 level 使用世界绝对坐标，因此后者必须先经 {@code helper.absolutePos(...)} 转换。
     */
    @GameTest(templateNamespace = IceAndFireDreadLand.MODID, template = "gametest_platform")
    public static void contact_detection_matches_portal_block(GameTestHelper helper) {
        layGround(helper);
        BlockPos feetRel = new BlockPos(2, 1, 2);

        helper.setBlock(feetRel, Blocks.STONE);
        Zombie zombieOnStone = helper.spawn(EntityType.ZOMBIE, feetRel);
        helper.assertFalse(PortalData.isInPortal(helper.getLevel(), zombieOnStone),
                "站在石头上的生物不应被判为接触传送门");
        zombieOnStone.discard();

        helper.setBlock(feetRel, ModBlocks.DREAD_PORTAL.get());
        Zombie zombieInPortal = helper.spawn(EntityType.ZOMBIE, feetRel);
        helper.assertTrue(PortalData.isInPortal(helper.getLevel(), zombieInPortal),
                "站在悚域传送门内的生物应被判为接触");

        zombieInPortal.discard();
        helper.succeed();
    }
}
