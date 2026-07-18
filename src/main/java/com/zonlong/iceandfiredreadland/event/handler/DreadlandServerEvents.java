package com.zonlong.iceandfiredreadland.event.handler;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.registry.ModDimensions;
import com.zonlong.iceandfiredreadland.registry.tag.ModBiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = IceAndFireDreadLand.MODID)
public final class DreadlandServerEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (creeper.level().isClientSide()) return;
        if (!creeper.level().dimension().equals(ModDimensions.DREADLAND)) return;
        if (!creeper.level().getBiome(creeper.blockPosition()).is(ModBiomeTags.IS_LIGHTNING)) return;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(creeper.level());
        if (bolt != null) {
            bolt.moveTo(creeper.position());
            creeper.level().addFreshEntity(bolt);
        }
    }
}
