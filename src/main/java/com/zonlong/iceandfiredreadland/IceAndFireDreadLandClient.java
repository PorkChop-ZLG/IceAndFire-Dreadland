package com.zonlong.iceandfiredreadland;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

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
}
