package com.chinaex123.player_partner.item;

import com.chinaex123.player_partner.init.PPEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.jetbrains.annotations.NotNull;

public class PartnerSpawnEggItem extends DeferredSpawnEggItem {

    public PartnerSpawnEggItem(Item.Properties properties) {
        super(PPEntities.PARTNER_MARKER, 0x3C8C5A, 0xF2F2F2, properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        PartnerSummoning.summonAt(serverLevel, pos, player.getYRot(), player);
        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected DispenseItemBehavior createDispenseBehavior() {
        return PartnerSummoning.dispenseBehavior();
    }
}
