package com.chinaex123.player_partner.init;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.item.PartnerSpawnEggItem;
import com.chinaex123.player_partner.item.PartnerSummonItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface PPItems {
    DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlayerPartner.MODID);

    /** 玩家伙伴刷怪蛋 */
    DeferredItem<Item> PARTNER_SPAWN_EGG = ITEMS.registerItem("player_partner_spawn_egg", PartnerSpawnEggItem::new, new Item.Properties());
    /** 玩家伙伴召唤物。 */
    DeferredItem<Item> PARTNER_SUMMONER = ITEMS.registerItem("partner_summoner", PartnerSummonItem::new, new Item.Properties().stacksTo(16));

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
