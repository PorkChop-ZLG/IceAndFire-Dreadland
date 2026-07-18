package com.zonlong.iceandfiredreadland.registry;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zonlong.iceandfiredreadland.render.DreadPortalBlockEntityRenderer;
import com.zonlong.iceandfiredreadland.render.RenderVariables;
import net.minecraft.client.renderer.RenderType;

public final class ModRenderTypes extends RenderType {
    private static final ShaderStateShard DREAD_PORTAL_PROGRAM = new ShaderStateShard(() -> RenderVariables.DREAD_PORTAL_PROGRAM);

    public ModRenderTypes(String nameIn, VertexFormat formatIn, VertexFormat.Mode drawModeIn, int bufferSizeIn, boolean useDelegateIn, boolean needsSortingIn, Runnable setupTaskIn, Runnable clearTaskIn) {
        super(nameIn, formatIn, drawModeIn, bufferSizeIn, useDelegateIn, needsSortingIn, setupTaskIn, clearTaskIn);
    }

    public static RenderType getDreadlandsPortal() {
        return create("dreadlands_portal",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256, false, false,
                CompositeState.builder()
                        .setShaderState(DREAD_PORTAL_PROGRAM)
                        .setTextureState(MultiTextureStateShard.builder()
                                .add(DreadPortalBlockEntityRenderer.DREAD_PORTAL_BACKGROUND, false, false)
                                .add(DreadPortalBlockEntityRenderer.DREAD_PORTAL, false, false)
                                .build())
                        .createCompositeState(false));
    }
}
