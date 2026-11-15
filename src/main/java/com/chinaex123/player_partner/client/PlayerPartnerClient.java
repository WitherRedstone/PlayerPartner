package com.chinaex123.player_partner.client;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.client.gui.PlayerPartnerScreen;
import com.chinaex123.player_partner.client.render.PlayerPartnerRenderer;
import com.chinaex123.player_partner.init.PPEntities;
import com.chinaex123.player_partner.init.PPMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = PlayerPartner.MODID, value = Dist.CLIENT)
public class PlayerPartnerClient {

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PPEntities.PLAYER_PARTNER.get(), PlayerPartnerRenderer::new);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(PPMenus.PLAYER_PARTNER_MENU.get(), PlayerPartnerScreen::new);
    }
}
