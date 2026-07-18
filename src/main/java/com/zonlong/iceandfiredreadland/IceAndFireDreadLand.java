package com.zonlong.iceandfiredreadland;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.ServerConfigManager;
import com.zonlong.iceandfiredreadland.config.ModCommonConfig;
import com.zonlong.iceandfiredreadland.registry.ModAttachments;
import com.zonlong.iceandfiredreadland.registry.ModBlockEntities;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.registry.ModCreativeModeTabs;
import com.zonlong.iceandfiredreadland.registry.ModItems;
import com.zonlong.iceandfiredreadland.registry.ModParticles;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// 模组主类 - 入口点，负责注册系统和事件总线初始化
@Mod(IceAndFireDreadLand.MODID)
public class IceAndFireDreadLand {
    public static final String MODID = "iceandfire_dreadland";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 模组构造函数 - FML 会自动注入 IEventBus 和 ModContainer
    public IceAndFireDreadLand(IEventBus modEventBus, ModContainer modContainer) {
        // 注册 commonSetup 到模组生命周期事件
        modEventBus.addListener(this::commonSetup);

        // 将所有 DeferredRegister 挂载到模组事件总线，使注册生效
        ModAttachments.REGISTRY.register(modEventBus);
        ModBlocks.REGISTRY.register(modEventBus);
        ModBlockEntities.REGISTRY.register(modEventBus);
        ModItems.REGISTRY.register(modEventBus);
        ModParticles.REGISTRY.register(modEventBus);
        ModCreativeModeTabs.REGISTRY.register(modEventBus);

        // 注册自身到游戏事件总线，用于处理服务端启动等游戏事件
        NeoForge.EVENT_BUS.register(this);

        // Jupiter 配置系统
        ConfigManager.getInstance().registerConfigHandler(ModCommonConfig.INSTANCE);
        ServerConfigManager.registerServerConfig(ModCommonConfig.INSTANCE, ServerConfigManager.PermissionChecker.IS_OPERATOR);
    }

    // === 生命周期事件 ===
    // FMLCommonSetupEvent - 模组通用初始化，在注册事件之后、侧边初始化之前触发
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Ice And Fire - Dread Land 正在初始化...");
    }

    // === 游戏事件 ===
    // ServerStartingEvent - 服务端启动时触发
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Ice And Fire - Dread Land 服务端启动完成");
    }
}
