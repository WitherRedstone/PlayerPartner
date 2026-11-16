package com.chinaex123.player_partner.client.gui;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.client.menu.PlayerPartnerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class PlayerPartnerScreen extends AbstractContainerScreen<PlayerPartnerMenu> {
    /** 背景贴图。 */
    private static final ResourceLocation TEXTURE = PlayerPartner.id("textures/gui/player_partner.png");

    /** 生命值图标。 */
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    /** 护甲图标。 */
    private static final ResourceLocation ARMOR_EMPTY = ResourceLocation.withDefaultNamespace("hud/armor_empty");
    private static final ResourceLocation ARMOR_FULL = ResourceLocation.withDefaultNamespace("hud/armor_full");
    /** 饥饿图标。 */
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");

    /** 界面文字颜色。 */
    private static final int LABEL_COLOR = 0xE6E8F2;
    /** 三项数值的颜色。 */
    private static final int HEART_COLOR = 0xC02830;
    private static final int ARMOR_COLOR = 0x3D4A6B;
    private static final int FOOD_COLOR = 0x9A5A12;
    /** 三项数值。 */
    private static final int STAT_Y = 122;
    private static final int STAT_FOOD_X = 48;
    private static final int STAT_ARMOR_X = 82;
    private static final int STAT_HEART_X = 118;
    /** 三项数值大小。 */
    private static final int STAT_TEXT_OFFSET = 11;

    public PlayerPartnerScreen(PlayerPartnerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PlayerPartnerMenu.IMAGE_WIDTH;
        this.imageHeight = PlayerPartnerMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = 124;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
        this.renderStats(guiGraphics);
    }

    private void renderStats(GuiGraphics guiGraphics) {
        int health = Mth.ceil(this.menu.getPartner().getHealth());
        int armor = this.menu.getPartner().getArmorValue();
        int food = this.menu.getPartner().getFoodData().getFoodLevel();

        this.drawStat(guiGraphics, HEART_CONTAINER, HEART_FULL, STAT_HEART_X, health, HEART_COLOR);
        this.drawStat(guiGraphics, ARMOR_EMPTY, ARMOR_FULL, STAT_ARMOR_X, armor, ARMOR_COLOR);
        this.drawStat(guiGraphics, FOOD_EMPTY, FOOD_FULL, STAT_FOOD_X, food, FOOD_COLOR);
    }

    private void drawStat(GuiGraphics guiGraphics, ResourceLocation container, ResourceLocation icon, int x, int value, int color) {
        guiGraphics.blitSprite(container, x, STAT_Y, 9, 9);
        guiGraphics.blitSprite(icon, x, STAT_Y, 9, 9);
        guiGraphics.drawString(this.font, "x" + value, x + STAT_TEXT_OFFSET, STAT_Y + 1, color, false);
    }
}
