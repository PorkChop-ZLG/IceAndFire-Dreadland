package com.zonlong.iceandfiredreadland;

import com.zonlong.iceandfiredreadland.particle.DreadPortalParticle;
import com.zonlong.iceandfiredreadland.registry.ModBlockEntities;
import com.zonlong.iceandfiredreadland.registry.ModParticles;
import com.zonlong.iceandfiredreadland.render.DreadPortalBlockEntityRenderer;
import com.zonlong.iceandfiredreadland.render.RenderVariables;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.io.IOException;

/**
 * 客户端专用类
 * 仅在物理客户端加载，不会在专用服务端加载。
 * 所有访问客户端代码（如渲染、模型、GUI）的逻辑都应放在这里。
 */
@Mod(value = IceAndFireDreadLand.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = IceAndFireDreadLand.MODID, value = Dist.CLIENT)
public class IceAndFireDreadLandClient {

    public IceAndFireDreadLandClient(ModContainer container) {
        // 注册模组配置界面——玩家在模组列表点击本模组后可打开配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        IceAndFireDreadLand.LOGGER.info("Ice And Fire - Dread Land 客户端初始化完成");
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.DREAD_PORTAL.get(), DreadPortalBlockEntityRenderer::new);
    }

    @SubscribeEvent
    static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.DREAD_PORTAL.get(), DreadPortalParticle::factory);
    }

    @SubscribeEvent
    static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, "rendertype_dread_portal"),
                        DefaultVertexFormat.POSITION_COLOR),
                program -> RenderVariables.DREAD_PORTAL_PROGRAM = program
        );
    }
}
