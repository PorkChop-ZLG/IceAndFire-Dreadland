package com.zonlong.iceandfiredreadland.registry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zonlong.iceandfiredreadland.render.DreadPortalBlockEntityRenderer;
import com.zonlong.iceandfiredreadland.render.RenderVariables;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * 悚域传送门使用的渲染类型。
 *
 * <p>DP-007：0.1.2 每次渲染方块实体都调用 {@code RenderType.create(...)}，
 * 而 {@code RenderType.create} 会直接构造新的 {@code CompositeRenderType}，不会按名字复用。
 * {@code MultiBufferSource} 以 RenderType 为键取 buffer，因此新建实例会结束上一个同类型批次，
 * 破坏相邻门块的合批，并产生多余的临时对象。
 *
 * <p>此处把实例缓存为静态常量。Shader 仍通过 {@link RenderStateShard.ShaderStateShard} 的
 * supplier 延迟取值，因此资源重载后注册的新 {@code ShaderInstance} 仍会被正确采用，
 * 缓存 RenderType 不会锁死旧 Shader。
 *
 * <p>本类不再继承 {@code RenderType}：构建所需的 {@code RenderType.create(...)}、
 * {@code CompositeState.builder()} 与各 StateShard 在 1.21.1 中都是公开的，
 * 无需继承即可复用（继承反而会因父类没有可用构造器而无法通过编译）。
 */
public final class ModRenderTypes {

    private static final RenderStateShard.ShaderStateShard DREAD_PORTAL_PROGRAM =
            new RenderStateShard.ShaderStateShard(() -> RenderVariables.DREAD_PORTAL_PROGRAM);

    /**
     * 稳定的悚域传送门渲染类型实例。
     *
     * <p>静态初始化即可：{@code RenderType.create} 只构造对象，不需要 GL 上下文，
     * 而 {@link RenderStateShard.ShaderStateShard} 的 supplier 在真正 render 时才被求值。
     */
    private static final RenderType DREADLANDS_PORTAL = RenderType.create("dreadlands_portal",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(DREAD_PORTAL_PROGRAM)
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(DreadPortalBlockEntityRenderer.DREAD_PORTAL_BACKGROUND, false, false)
                            .add(DreadPortalBlockEntityRenderer.DREAD_PORTAL, false, false)
                            .build())
                    .createCompositeState(false));

    /**
     * 返回缓存的悚域传送门渲染类型。
     *
     * <p>连续调用返回同一实例，便于合批与避免重复分配。
     */
    public static RenderType getDreadlandsPortal() {
        return DREADLANDS_PORTAL;
    }

    private ModRenderTypes() {
    }
}
