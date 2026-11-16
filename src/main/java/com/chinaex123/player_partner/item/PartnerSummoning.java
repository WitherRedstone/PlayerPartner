package com.chinaex123.player_partner.item;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.world.PartnerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public final class PartnerSummoning {

    public static void summonAt(ServerLevel level, BlockPos pos, float yaw, ServerPlayer owner) {
        Vec3 spawnPos = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        PartnerManager.get(level.getServer()).spawn(PlayerPartner.DEFAULT_DISPLAY_NAME,
                PlayerPartner.DEFAULT_SKIN_NAME, owner == null ? null : owner.getUUID(), level, spawnPos, yaw, owner);
    }

    public static DispenseItemBehavior dispenseBehavior() {
        return (source, stack) -> {
            if (source.level() instanceof ServerLevel serverLevel) {
                Direction face = source.state().getValue(DispenserBlock.FACING);
                BlockPos pos = source.pos().relative(face);
                summonAt(serverLevel, pos, face.toYRot(), null);
                stack.shrink(1);
            }
            return stack;
        };
    }

    public static void registerDispenserBehavior(Item item) {
        DispenserBlock.registerBehavior(item, dispenseBehavior());
    }
}
