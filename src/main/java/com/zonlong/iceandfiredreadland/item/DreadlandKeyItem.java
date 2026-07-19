package com.zonlong.iceandfiredreadland.item;

import com.zonlong.iceandfiredreadland.registry.tag.ModBlockTags;
import com.zonlong.iceandfiredreadland.world.portal.DreadPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class DreadlandKeyItem extends Item {
    public DreadlandKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        if (!level.getBlockState(clickedPos).is(ModBlockTags.DREADLAND_PORTAL_FRAME)) {
            return InteractionResult.PASS;
        }

        BlockPos interiorPos = clickedPos.relative(context.getClickedFace());
        Optional<DreadPortalShape> shape = DreadPortalShape.findEmptyPortalShape(level, interiorPos, Direction.Axis.X);
        if (shape.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            shape.get().createPortalBlocks();
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
