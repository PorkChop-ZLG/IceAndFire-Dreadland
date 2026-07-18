package com.zonlong.iceandfiredreadland.config;

import com.iafenvoy.jupiter.config.container.FileConfigContainer;
import com.iafenvoy.jupiter.config.entry.IntegerEntry;
import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.resources.ResourceLocation;

public class ModCommonConfig extends FileConfigContainer {
    public static final ModCommonConfig INSTANCE = new ModCommonConfig();

    public final IntegerEntry portalTeleportTick = IntegerEntry.builder("config.iceandfire_dreadland.portalTeleportTick", 100)
            .min(0).max(600)
            .key("portalTeleportTick").build();

    public ModCommonConfig() {
        super(ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, "common"),
                "config.iceandfire_dreadland.common.title",
                "./config/iceandfire_dreadland/common.json");
    }

    @Override
    public void init() {
        this.createTab("common", "config.iceandfire_dreadland.category.common")
                .addEntry(this.portalTeleportTick);
    }
}
