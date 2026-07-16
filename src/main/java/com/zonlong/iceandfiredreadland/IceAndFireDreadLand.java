package com.zonlong.iceandfiredreadland;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

// 模组主类 - 入口点，负责注册系统和事件总线初始化
@Mod(IceAndFireDreadLand.MODID)
public class IceAndFireDreadLand {
    public static final String MODID = "iceandfire_dreadland";
    public static final Logger LOGGER = LogUtils.getLogger();

    // === 注册器 ===
    // DeferredRegister 是 NeoForge 推荐的类型安全注册方式，用于将方块、物品等注册到游戏中
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 模组构造函数 - FML 会自动注入 IEventBus 和 ModContainer
    public IceAndFireDreadLand(IEventBus modEventBus, ModContainer modContainer) {
        // 注册 commonSetup 到模组生命周期事件
        modEventBus.addListener(this::commonSetup);

        // 将所有 DeferredRegister 挂载到模组事件总线，使注册生效
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // 注册自身到游戏事件总线，用于处理服务端启动等游戏事件
        NeoForge.EVENT_BUS.register(this);

        // 加载模组配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
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
