package com.zonlong.iceandfiredreadland.registry.tag;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
    public static final TagKey<Block> DREADLAND_PORTAL_FRAME = create("dreadland_portal_frame");

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, name));
    }
}
