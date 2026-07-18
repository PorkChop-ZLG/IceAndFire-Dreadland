package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.data.component.PortalData;
import com.zonlong.iceandfiredreadland.util.attachment.IafEntityAttachment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

@EventBusSubscriber
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IceAndFireDreadLand.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PortalData>> PORTAL_DATA = REGISTRY.register("portal_data",
            () -> AttachmentType.builder(PortalData::new).serialize(PortalData.CODEC).sync(PortalData.PACKET_CODEC).copyOnDeath().build());

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            tickAndSync(ModAttachments.PORTAL_DATA, living);
        }
    }

    private static <T extends Entity, A extends IafEntityAttachment<T>> void tickAndSync(Supplier<AttachmentType<A>> type, T entity) {
        A attachment = entity.getData(type);
        attachment.tick(entity);
        if (attachment.isDirty()) entity.syncData(type);
    }
}
