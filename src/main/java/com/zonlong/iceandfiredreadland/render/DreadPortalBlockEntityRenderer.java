package com.zonlong.iceandfiredreadland.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zonlong.iceandfiredreadland.IceAndFireDreadLand;
import com.zonlong.iceandfiredreadland.block.entity.DreadPortalBlockEntity;
import com.zonlong.iceandfiredreadland.registry.ModBlocks;
import com.zonlong.iceandfiredreadland.registry.ModRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 悚域传送门方块实体的渲染器。
 *
 * <p>DP-007：0.1.2 对每个门方块无条件提交全部六个面。一个完整 3×5 门面共 15 个方块，
 * 会提交 90 个四边形，其中 44 个是相邻门块之间的内部面（按同样的立方体几何，
 * 只保留外表面时应为 46 个）。
 *
 * <p>此处按「相邻方块是否同为悚域传送门」逐面剔除内部面。剔除是对称的：
 * 一个面只要被两侧门块共享就一定不可见，因此按轴区分不会改变画面，只减少提交量。
 * 本轮保持原有立方体几何，不把门块改成薄平面。
 */
public class DreadPortalBlockEntityRenderer<T extends DreadPortalBlockEntity> implements BlockEntityRenderer<T> {
    public static final ResourceLocation DREAD_PORTAL_BACKGROUND = ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, "textures/environment/dread_portal_background.png");
    public static final ResourceLocation DREAD_PORTAL = ResourceLocation.fromNamespaceAndPath(IceAndFireDreadLand.MODID, "textures/environment/dread_portal.png");

    public DreadPortalBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        Matrix4f matrix4f = matrices.last().pose();
        VertexConsumer consumer = vertexConsumers.getBuffer(this.renderType());

        Level level = entity.getLevel();
        BlockPos pos = entity.getBlockPos();

        // 相邻门块会共享这个面，因此不可见，直接跳过。
        // 剔除逻辑与 DreadPortalGeometry 保持一致。
        boolean cullUp = com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry
                .isPortal(level, pos, Direction.UP);
        boolean cullDown = com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry
                .isPortal(level, pos, Direction.DOWN);
        boolean cullNorth = com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry
                .isPortal(level, pos, Direction.NORTH);
        boolean cullSouth = com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry
                .isPortal(level, pos, Direction.SOUTH);
        boolean cullWest = com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry
                .isPortal(level, pos, Direction.WEST);
        boolean cullEast = com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry
                .isPortal(level, pos, Direction.EAST);

        // z = 1（南面）
        if (!cullSouth) {
            consumer.addVertex(matrix4f, 0, 0, 1).setColor(-1);
            consumer.addVertex(matrix4f, 1, 0, 1).setColor(-1);
            consumer.addVertex(matrix4f, 1, 1, 1).setColor(-1);
            consumer.addVertex(matrix4f, 0, 1, 1).setColor(-1);
        }
        // z = 0（北面）
        if (!cullNorth) {
            consumer.addVertex(matrix4f, 0, 0, 0).setColor(-1);
            consumer.addVertex(matrix4f, 0, 1, 0).setColor(-1);
            consumer.addVertex(matrix4f, 1, 1, 0).setColor(-1);
            consumer.addVertex(matrix4f, 1, 0, 0).setColor(-1);
        }
        // x = 0（西面）
        if (!cullWest) {
            consumer.addVertex(matrix4f, 0, 0, 0).setColor(-1);
            consumer.addVertex(matrix4f, 0, 0, 1).setColor(-1);
            consumer.addVertex(matrix4f, 0, 1, 1).setColor(-1);
            consumer.addVertex(matrix4f, 0, 1, 0).setColor(-1);
        }
        // x = 1（东面）
        if (!cullEast) {
            consumer.addVertex(matrix4f, 1, 0, 1).setColor(-1);
            consumer.addVertex(matrix4f, 1, 0, 0).setColor(-1);
            consumer.addVertex(matrix4f, 1, 1, 0).setColor(-1);
            consumer.addVertex(matrix4f, 1, 1, 1).setColor(-1);
        }
        // y = 1（上面）
        if (!cullUp) {
            consumer.addVertex(matrix4f, 0, 1, 0).setColor(-1);
            consumer.addVertex(matrix4f, 0, 1, 1).setColor(-1);
            consumer.addVertex(matrix4f, 1, 1, 1).setColor(-1);
            consumer.addVertex(matrix4f, 1, 1, 0).setColor(-1);
        }
        // y = 0（下面）
        if (!cullDown) {
            consumer.addVertex(matrix4f, 0, 0, 0).setColor(-1);
            consumer.addVertex(matrix4f, 1, 0, 0).setColor(-1);
            consumer.addVertex(matrix4f, 1, 0, 1).setColor(-1);
            consumer.addVertex(matrix4f, 0, 0, 1).setColor(-1);
        }
    }

    /** 相邻方块是否同为悚域传送门（据此剔除共享面）。 */
    protected RenderType renderType() {
        return ModRenderTypes.getDreadlandsPortal();
    }

    /**
     * 统计一个门方块会提交的四边形数量，供测试核对剔除效果。
     *
     * <p>实际几何判断位于公共端的 {@code DreadPortalGeometry}，使同一逻辑可在服务端测试中验证。
     */
    public static int submittedFaceCount(Level level, BlockPos pos) {
        return com.zonlong.iceandfiredreadland.world.portal.DreadPortalGeometry
                .submittedFaceCount(level, pos);
    }
}
