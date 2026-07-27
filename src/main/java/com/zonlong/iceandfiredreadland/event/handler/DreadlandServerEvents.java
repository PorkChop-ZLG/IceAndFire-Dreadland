package com.zonlong.iceandfiredreadland.event.handler;

import com.iafenvoy.iceandfire.registry.IafItems;
import com.iafenvoy.iceandfire.util.trade.TradeOfferHelper;
import com.iafenvoy.iceandfire.util.trade.factory.BuyWithPrice;
import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.config.ModCommonConfig;
import com.zonlong.iceandfiredreadland.registry.ModDimensions;
import com.zonlong.iceandfiredreadland.registry.tag.ModBiomeTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = IceAndFireDreadLand.MODID)
public final class DreadlandServerEvents {

    /**
     * 修改冰火传说抄书吏的交易内容，在等级1追加悚域主题交易。
     * 使用 LOWEST 优先级确保在 IafTrades.init() 执行之后运行。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void modifyScribeTrades(FMLCommonSetupEvent event) {
        ResourceLocation scribeKey = ResourceLocation.fromNamespaceAndPath("iceandfire", "scribe");
        VillagerProfession scribe = BuiltInRegistries.VILLAGER_PROFESSION.get(scribeKey);
        if (scribe == null) return;

        float itemForEmeraldMultiplier = 0.05F;
        TradeOfferHelper.registerVillagerOffers(scribe, 1,
                new BuyWithPrice(
                        new ItemStack(IafItems.DREAD_SHARD.get(), 16),
                        new ItemStack(IafItems.DREAD_KEY.get(), 1),
                        16, 2, itemForEmeraldMultiplier
                )
        );
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (creeper.level().isClientSide()) return;
        if (!creeper.level().dimension().equals(ModDimensions.DREADLAND)) return;
        if (!creeper.level().getBiome(creeper.blockPosition()).is(ModBiomeTags.IS_LIGHTNING)) return;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(creeper.level());
        if (bolt == null) return;

        if (ModCommonConfig.INSTANCE.lightningDestructive.getValue()) {
            // 破坏性闪电：全效果原版闪电，产生火焰、破坏方块、摧毁物品
            bolt.moveTo(creeper.position());
            creeper.level().addFreshEntity(bolt);
        } else {
            // 非破坏性闪电：纯视觉 + 手动转化闪电苦力怕，不产生火焰和破坏
            bolt.setVisualOnly(true);
            bolt.moveTo(creeper.position());
            creeper.level().addFreshEntity(bolt);
            if (creeper.level() instanceof ServerLevel serverLevel) {
                creeper.thunderHit(serverLevel, bolt);
            }
        }
    }
}
