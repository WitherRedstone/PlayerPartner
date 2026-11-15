package com.chinaex123.player_partner.event;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PlayerPartnerEntity;
import com.chinaex123.player_partner.init.PPEntities;
import com.chinaex123.player_partner.init.PPItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = PlayerPartner.MODID)
public class ModEvents {

    @SubscribeEvent
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PPEntities.PLAYER_PARTNER.get(), PlayerPartnerEntity.createAttributes().build());
    }

    @SubscribeEvent
    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(PPItems.PLAYER_PARTNER_SPAWN_EGG.get());
        }
    }
}
