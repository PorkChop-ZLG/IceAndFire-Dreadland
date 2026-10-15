package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.data.component.PortalData;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IceAndFireDreadLand.MODID);

    /**
     * 悚域传送门接触状态。
     *
     * <p>刻意不声明 {@code .sync(...)}：该状态没有网络消费方，HUD 使用纯客户端的有界进度。
     * 声明同步会让每个持有该附件的实体在被客户端追踪时广播完整附件。
     *
     * <p>刻意不声明 {@code .copyOnDeath()}：倒计时是临时数据，不应跨死亡复制。
     *
     * <p>保留 serializer 是为了让旧存档中已经存在的 {@code portal_data} 条目仍能解析，
     * 避免 NeoForge 报 “unknown or non-serializable attachment” 错误。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PortalData>> PORTAL_DATA =
            REGISTRY.register("portal_data", () -> AttachmentType.builder(PortalData::new)
                    .serialize(PortalData.CODEC)
                    .build());

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        // 只由服务端推进权威状态；客户端不参与计时。
        if (living.level().isClientSide()) {
            return;
        }

        // 不接触传送门、且从未持有状态时立即返回：
        // 绝不调用 getData()，因此不会为普通生物创建、持久化或同步附件。
        PortalData existing = living.getExistingDataOrNull(PORTAL_DATA);
        boolean inContact = PortalData.isInPortal(living.level(), living);
        if (existing == null && !inContact) {
            return;
        }

        // 仅在确认接触的这一刻才创建附件。
        PortalData data = existing != null ? existing : living.getData(PORTAL_DATA);

        PortalData.TickResult result = data.tick(living, inContact);
        // TRAVEL_FAILED 时状态机已自行复位倒计时，附件继续保留以便重试。
        if (result == PortalData.TickResult.REMOVE) {
            // 回到空闲：移除附件，避免长期占用内存与存档。
            living.removeData(PORTAL_DATA);
        }
    }

    private ModAttachments() {
    }
}
