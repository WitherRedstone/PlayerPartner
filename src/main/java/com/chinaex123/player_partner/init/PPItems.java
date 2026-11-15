package com.chinaex123.player_partner.init;

import com.chinaex123.player_partner.PlayerPartner;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface PPItems {
    DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlayerPartner.MODID);

    /** 玩家伙伴。 */
    DeferredItem<DeferredSpawnEggItem> PLAYER_PARTNER_SPAWN_EGG = ITEMS.register("player_partner_spawn_egg",
            () -> new DeferredSpawnEggItem(PPEntities.PLAYER_PARTNER, 0x3C8C5A, 0xF2F2F2, new Item.Properties()));

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
