package com.chinaex123.player_partner;

import com.chinaex123.player_partner.init.PPEntities;
import com.chinaex123.player_partner.init.PPItems;
import com.chinaex123.player_partner.init.PPMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PlayerPartner.MODID)
public class PlayerPartner {
    public static final String MODID = "player_partner";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlayerPartner(IEventBus modEventBus, ModContainer modContainer) {
        PPEntities.register(modEventBus);
        PPItems.register(modEventBus);
        PPMenus.register(modEventBus);
    }

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MODID, name);
    }
}
