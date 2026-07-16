package com.zonlong.iceandfiredreadland;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置文件
 * 使用 NeoForge 的 ModConfigSpec 系统进行配置管理。
 * 配置文件会自动生成在 config/iceandfire_dreadland-common.toml
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ========================================
    // 在此处添加你的配置项
    // 示例：
    // public static final ModConfigSpec.BooleanValue SOME_FLAG = BUILDER
    //         .comment("这是某个功能开关的描述")
    //         .define("someFlag", true);
    //
    // public static final ModConfigSpec.IntValue SOME_NUMBER = BUILDER
    //         .comment("这是某个数值的描述")
    //         .defineInRange("someNumber", 10, 0, 100);
    // ========================================

    // 构建最终的配置规范（必须放在所有配置项定义之后）
    static final ModConfigSpec SPEC = BUILDER.build();
}
