package com.chinaex123.player_partner.client.gui;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.client.menu.PlayerPartnerMenu;
import com.chinaex123.player_partner.entity.PartnerJob;
import com.chinaex123.player_partner.network.SetPartnerJobPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class PlayerPartnerScreen extends AbstractContainerScreen<PlayerPartnerMenu> {
    /** 背景贴图。 */
    private static final ResourceLocation TEXTURE = PlayerPartner.id("textures/gui/player_partner.png");
    /** 左侧任务按钮的按钮底贴图。 */
    private static final ResourceLocation JOB_BUTTON_TEXTURE = PlayerPartner.id("textures/gui/job_button.png");
    private static final ResourceLocation JOB_BUTTON_TEXTURE_HOVER = PlayerPartner.id("textures/gui/job_button_hover.png");
    private static final ResourceLocation JOB_BUTTON_TEXTURE_SELECTED = PlayerPartner.id("textures/gui/job_button_selected.png");

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
    /** 数值在图标右侧的偏移。 */
    private static final int STAT_TEXT_OFFSET = 11;

    /** 左侧任务按钮。 */
    private static final int JOB_BUTTON_SIZE = 20;
    private static final int JOB_BUTTON_GAP = 4;
    private static final int JOB_BUTTON_OFFSET_X = -26;
    private static final int JOB_BUTTON_OFFSET_Y = 20;
    /** 按钮底贴图自身的尺寸。 */
    private static final int JOB_BUTTON_TEXTURE_WIDTH = 20;
    private static final int JOB_BUTTON_TEXTURE_HEIGHT = 20;

    /** 界面上依次排列的任务。 */
    private static final PartnerJob[] JOB_BUTTONS = {
            PartnerJob.COMBAT, PartnerJob.MINING, PartnerJob.CHOPPING, PartnerJob.FARMING
    };

    /** 每个任务按钮上渲染的物品图标。 */
    private static final Item[] JOB_BUTTON_ITEMS = {
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_HOE
    };

    /** 界面右侧的“任务过滤”按钮：与左侧任务按钮同尺寸，贴在面板右边缘外面。 */
    private static final int FILTER_BUTTON_SIZE = 20;
    private static final int FILTER_BUTTON_OFFSET_X = PlayerPartnerMenu.IMAGE_WIDTH + 6;
    private static final int FILTER_BUTTON_OFFSET_Y = JOB_BUTTON_OFFSET_Y;
    private static final Item FILTER_BUTTON_ITEM = Items.HOPPER;

    public PlayerPartnerScreen(PlayerPartnerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PlayerPartnerMenu.IMAGE_WIDTH;
        this.imageHeight = PlayerPartnerMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = 124;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        this.renderJobButtons(guiGraphics, mouseX, mouseY);
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
        int food = this.menu.getDisplayedFoodLevel();

        this.drawStat(guiGraphics, HEART_CONTAINER, HEART_FULL, STAT_HEART_X, health, HEART_COLOR);
        this.drawStat(guiGraphics, ARMOR_EMPTY, ARMOR_FULL, STAT_ARMOR_X, armor, ARMOR_COLOR);
        this.drawStat(guiGraphics, FOOD_EMPTY, FOOD_FULL, STAT_FOOD_X, food, FOOD_COLOR);
    }

    private void drawStat(GuiGraphics guiGraphics, ResourceLocation container, ResourceLocation icon, int x, int value, int color) {
        guiGraphics.blitSprite(container, x, STAT_Y, 9, 9);
        guiGraphics.blitSprite(icon, x, STAT_Y, 9, 9);
        guiGraphics.drawString(this.font, "x" + value, x + STAT_TEXT_OFFSET, STAT_Y + 1, color, false);
    }

    private void renderJobButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PartnerJob current = this.menu.getDisplayedJob();
        for (int index = 0; index < JOB_BUTTONS.length; index++) {
            PartnerJob job = JOB_BUTTONS[index];
            int x = this.jobButtonX();
            int y = this.jobButtonY(index);
            boolean hovered = isInside(mouseX, mouseY, x, y, JOB_BUTTON_SIZE, JOB_BUTTON_SIZE);
            // 鼠标悬停优先显示悬停态，其次是“选中”态，最后是普通态。
            ResourceLocation buttonTexture = hovered
                    ? JOB_BUTTON_TEXTURE_HOVER
                    : (job == current ? JOB_BUTTON_TEXTURE_SELECTED : JOB_BUTTON_TEXTURE);

            // 按钮底。
            guiGraphics.blit(buttonTexture, x, y, 0.0F, 0.0F,
                    JOB_BUTTON_SIZE, JOB_BUTTON_SIZE, JOB_BUTTON_TEXTURE_WIDTH, JOB_BUTTON_TEXTURE_HEIGHT);
            // 图标。
            guiGraphics.renderItem(new ItemStack(JOB_BUTTON_ITEMS[index]), x + 2, y + 2);

            if (hovered) {
                Component name = Component.translatable(job.translationKey());
                Component description = Component.translatable(job.translationKey() + ".desc").withStyle(ChatFormatting.GRAY);
                guiGraphics.renderComponentTooltip(this.font, List.of(name, description), mouseX, mouseY);
            }
        }

        // 面板右侧的“任务过滤”按钮：打开任务过滤界面，里面按任务类型分页设置各自的过滤。
        int filterX = this.filterButtonX();
        int filterY = this.filterButtonY();
        boolean filterHovered = isInside(mouseX, mouseY, filterX, filterY, FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE);
        guiGraphics.blit(filterHovered ? JOB_BUTTON_TEXTURE_HOVER : JOB_BUTTON_TEXTURE, filterX, filterY, 0.0F, 0.0F,
                FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE, JOB_BUTTON_TEXTURE_WIDTH, JOB_BUTTON_TEXTURE_HEIGHT);
        guiGraphics.renderItem(new ItemStack(FILTER_BUTTON_ITEM), filterX + 2, filterY + 2);
        if (filterHovered) {
            guiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("gui.player_partner.job_filter"),
                    Component.translatable("gui.player_partner.job_filter.desc").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
    }

    /** 过滤按钮贴在面板右边缘外面，纵向与第一个任务按钮对齐。 */
    private int filterButtonX() {
        return this.leftPos + FILTER_BUTTON_OFFSET_X;
    }

    private int filterButtonY() {
        return this.jobButtonY(0) + FILTER_BUTTON_OFFSET_Y - JOB_BUTTON_OFFSET_Y;
    }

    private int jobButtonX() {
        return this.leftPos + JOB_BUTTON_OFFSET_X;
    }

    private int jobButtonY(int index) {
        return this.topPos + JOB_BUTTON_OFFSET_Y + index * (JOB_BUTTON_SIZE + JOB_BUTTON_GAP);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int index = 0; index < JOB_BUTTONS.length; index++) {
                int x = this.jobButtonX();
                int y = this.jobButtonY(index);
                if (isInside(mouseX, mouseY, x, y, JOB_BUTTON_SIZE, JOB_BUTTON_SIZE)) {
                    this.sendJob(JOB_BUTTONS[index]);
                    return true;
                }
            }
            // 右侧的过滤按钮：打开任务过滤界面。
            int filterX = this.filterButtonX();
            int filterY = this.filterButtonY();
            if (isInside(mouseX, mouseY, filterX, filterY, FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE)) {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                minecraft.setScreen(new JobFilterScreen(this));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendJob(PartnerJob clicked) {
        PartnerJob target = this.menu.getDisplayedJob() == clicked ? PartnerJob.NONE : clicked;
        int entityId = this.menu.getPartner().getId();
        PacketDistributor.sendToServer(new SetPartnerJobPayload(entityId, target.ordinal()));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
