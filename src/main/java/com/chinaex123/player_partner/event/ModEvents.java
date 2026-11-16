package com.chinaex123.player_partner.event;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PartnerMarker;
import com.chinaex123.player_partner.init.PPCommands;
import com.chinaex123.player_partner.init.PPEntities;
import com.chinaex123.player_partner.init.PPItems;
import com.chinaex123.player_partner.item.PartnerSummoning;
import com.chinaex123.player_partner.network.MiningFilterEditPayload;
import com.chinaex123.player_partner.network.MiningFilterRequestPayload;
import com.chinaex123.player_partner.network.MiningFilterSyncPayload;
import com.chinaex123.player_partner.network.SetPartnerJobPayload;
import com.chinaex123.player_partner.world.PartnerManager;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = PlayerPartner.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(PPItems.PARTNER_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PPItems.PARTNER_SUMMONER);
        }
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> PartnerSummoning.registerDispenserBehavior(PPItems.PARTNER_SUMMONER.get()));
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PPEntities.PARTNER_MARKER.get(), PartnerMarker.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SetPartnerJobPayload.TYPE, SetPartnerJobPayload.STREAM_CODEC, SetPartnerJobPayload::handle);
        event.registrar("1").playToServer(MiningFilterRequestPayload.TYPE, MiningFilterRequestPayload.STREAM_CODEC, MiningFilterRequestPayload::handle);
        event.registrar("1").playToServer(MiningFilterEditPayload.TYPE, MiningFilterEditPayload.STREAM_CODEC, MiningFilterEditPayload::handle);
        event.registrar("1").playToClient(MiningFilterSyncPayload.TYPE, MiningFilterSyncPayload.STREAM_CODEC, MiningFilterSyncPayload::handle);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        PPCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        PartnerManager.get(event.getServer()).onServerStarted();
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        PartnerManager.get(event.getServer()).onServerStopping();
    }
}
