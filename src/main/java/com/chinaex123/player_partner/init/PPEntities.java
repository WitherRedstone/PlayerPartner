package com.chinaex123.player_partner.init;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PartnerMarker;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface PPEntities {
    DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, PlayerPartner.MODID);

    /** 玩家伙伴 */
    DeferredHolder<EntityType<?>, EntityType<PartnerMarker>> PARTNER_MARKER = ENTITY_TYPES.register("partner_marker",
            () -> EntityType.Builder.of(PartnerMarker::new, MobCategory.MISC).sized(0.3F, 0.9F).noSummon().build("partner_marker"));

    static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
