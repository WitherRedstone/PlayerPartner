package com.chinaex123.player_partner.init;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PlayerPartnerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface PPEntities {
    DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, PlayerPartner.MODID);

    DeferredHolder<EntityType<?>, EntityType<PlayerPartnerEntity>> PLAYER_PARTNER =
            ENTITY_TYPES.register("player_partner", () -> EntityType.Builder
                    .of(PlayerPartnerEntity::new, MobCategory.CREATURE)
                    .sized(0.3F, 0.9F)
                    .eyeHeight(0.8F)
                    .clientTrackingRange(8)
                    .build("player_partner"));


    static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
