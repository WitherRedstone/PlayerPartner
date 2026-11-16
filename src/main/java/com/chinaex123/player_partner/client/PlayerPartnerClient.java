package com.chinaex123.player_partner.client;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.client.gui.PlayerPartnerScreen;
import com.chinaex123.player_partner.client.skin.PartnerDefaultSkin;
import com.chinaex123.player_partner.init.PPMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = PlayerPartner.MODID, value = Dist.CLIENT)
public class PlayerPartnerClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(PartnerDefaultSkin::register);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(PPMenus.PLAYER_PARTNER_MENU.get(), PlayerPartnerScreen::new);
    }
}
