package com.zonlong.iceandfiredreadland.registry;

import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE, IceAndFireDreadLand.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAD_PORTAL = REGISTRY.register("dread_portal", () -> new SimpleParticleType(true));
}
