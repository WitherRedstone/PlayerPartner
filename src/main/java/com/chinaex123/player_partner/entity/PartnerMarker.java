package com.chinaex123.player_partner.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class PartnerMarker extends Mob {

    public PartnerMarker(EntityType<? extends PartnerMarker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.discard();
        }
    }
}
