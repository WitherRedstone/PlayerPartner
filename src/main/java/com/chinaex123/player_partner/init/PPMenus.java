package com.chinaex123.player_partner.init;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.inventory.PlayerPartnerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface PPMenus {
    DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, PlayerPartner.MODID);

    /** 玩家伙伴的背包界面。 */
    DeferredHolder<MenuType<?>, MenuType<PlayerPartnerMenu>> PLAYER_PARTNER_MENU =
            MENU_TYPES.register("player_partner", () -> IMenuTypeExtension.create(PlayerPartnerMenu::new));

    static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
