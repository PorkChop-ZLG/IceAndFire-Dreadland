package com.zonlong.iceandfiredreadland.registry.tag;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class ModBiomeTags {
    public static final TagKey<Biome> IS_LIGHTNING = create("is_lightning");

    private static TagKey<Biome> create(final String name) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, name));
    }
}
