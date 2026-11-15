package com.chinaex123.player_partner.client.gui;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.inventory.PlayerPartnerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PlayerPartnerScreen extends AbstractContainerScreen<PlayerPartnerMenu> {
    /** 背景贴图。 */
    private static final ResourceLocation TEXTURE = PlayerPartner.id("textures/gui/player_partner.png");
    /** 饥饿图标。 */
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");

    /** 饥饿条的位置。 */
    private static final int FOOD_X = 8;
    private static final int FOOD_Y = 98;

    public PlayerPartnerScreen(PlayerPartnerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PlayerPartnerMenu.IMAGE_WIDTH;
        this.imageHeight = PlayerPartnerMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = 112;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.renderFood(guiGraphics);
    }

    private void renderFood(GuiGraphics guiGraphics) {
        int foodLevel = this.menu.getPartner().getFoodLevel();
        for (int i = 0; i < 10; i++) {
            int x = this.leftPos + FOOD_X + i * 8;
            int y = this.topPos + FOOD_Y;
            guiGraphics.blitSprite(FOOD_EMPTY, x, y, 9, 9);

            int foodForIcon = foodLevel - i * 2;
            if (foodForIcon >= 2) {
                guiGraphics.blitSprite(FOOD_FULL, x, y, 9, 9);
            } else if (foodForIcon == 1) {
                guiGraphics.blitSprite(FOOD_HALF, x, y, 9, 9);
            }
        }
    }
}
