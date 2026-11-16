package com.chinaex123.player_partner.client.gui;

import com.chinaex123.player_partner.entity.PartnerJob;
import com.chinaex123.player_partner.network.MiningFilterEditPayload;
import com.chinaex123.player_partner.network.MiningFilterRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class JobFilterScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 200;
    private static final int TAB_WIDTH = 58;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP = 4;
    private static final int TAB_TOP = 22;
    private static final int ROW_HEIGHT = 12;
    private static final int LIST_TOP = 96;
    private static final int LIST_BOTTOM = PANEL_HEIGHT - 26;
    private static final int DELETE_SIZE = 10;

    /** 顶部按顺序排列的任务页签。 */
    private static final PartnerJob[] TABS = {
            PartnerJob.COMBAT, PartnerJob.MINING, PartnerJob.CHOPPING, PartnerJob.FARMING
    };

    /** 服务端最近一次发过来的规则，界面重开时先用它垫着，等同步回来再刷新。 */
    private static boolean cachedWhitelist = true;
    private static List<String> cachedEntries = List.of();

    private final Screen parent;
    private PartnerJob tab = PartnerJob.MINING;
    private boolean whitelist = cachedWhitelist;
    private List<String> entries = cachedEntries;
    private int scroll;
    private EditBox input;
    private Button modeButton;

    public JobFilterScreen(Screen parent) {
        super(Component.translatable("gui.player_partner.job_filter"));
        this.parent = parent;
    }

    /** 收到服务端同步的规则。 */
    public static void acceptRules(boolean whitelist, List<String> entries) {
        cachedWhitelist = whitelist;
        cachedEntries = List.copyOf(entries);
    }

    /** 同步包到达时刷新已经打开的界面。 */
    public void refreshFromCache() {
        this.whitelist = cachedWhitelist;
        this.entries = cachedEntries;
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.entries.size() - this.visibleRows()));
        this.updateModeButton();
    }

    private boolean isMiningTab() {
        return this.tab == PartnerJob.MINING;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        if (!this.isMiningTab()) {
            // 其它任务还没有可过滤项，只留“完成”按钮。
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                    .bounds(left + PANEL_WIDTH - 80, top + PANEL_HEIGHT - 22, 72, 20).build());
            return;
        }

        this.modeButton = this.addRenderableWidget(Button.builder(modeLabel(), button -> {
            this.send(MiningFilterEditPayload.mode(!this.whitelist));
        }).bounds(left + 8, top + LIST_TOP - 46, PANEL_WIDTH - 16, 20).build());

        this.input = new EditBox(this.font, left + 8, top + LIST_TOP - 22, PANEL_WIDTH - 68, 18,
                Component.translatable("gui.player_partner.mining_filter.hint"));
        this.input.setMaxLength(120);
        this.input.setHint(Component.translatable("gui.player_partner.mining_filter.hint"));
        this.addRenderableWidget(this.input);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.player_partner.mining_filter.add"), button -> {
            this.addFromInput();
        }).bounds(left + PANEL_WIDTH - 56, top + LIST_TOP - 22, 48, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.player_partner.mining_filter.clear"), button -> {
            this.send(MiningFilterEditPayload.clear());
        }).bounds(left + 8, top + PANEL_HEIGHT - 22, 72, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 80, top + PANEL_HEIGHT - 22, 72, 20).build());

        // 打开界面先要一份最新的规则。
        PacketDistributor.sendToServer(new MiningFilterRequestPayload());
    }

    private void updateModeButton() {
        if (this.modeButton != null) {
            this.modeButton.setMessage(modeLabel());
        }
    }

    private Component modeLabel() {
        return this.whitelist
                ? Component.translatable("gui.player_partner.mining_filter.mode_whitelist")
                : Component.translatable("gui.player_partner.mining_filter.mode_blacklist");
    }

    private int visibleRows() {
        return (LIST_BOTTOM - LIST_TOP) / ROW_HEIGHT;
    }

    private void addFromInput() {
        String value = this.input == null ? "" : this.input.getValue().trim();
        if (value.isEmpty()) {
            return;
        }
        this.send(MiningFilterEditPayload.add(value));
        this.input.setValue("");
    }

    private void send(MiningFilterEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private int tabX(int index) {
        int left = (this.width - PANEL_WIDTH) / 2;
        return left + 8 + index * (TAB_WIDTH + TAB_GAP);
    }

    private int tabY() {
        return (this.height - PANEL_HEIGHT) / 2 + TAB_TOP;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xC41A1E2A, 0xD8101220);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        guiGraphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF000000);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFB9BCC8);
        guiGraphics.drawString(this.font, this.title, left + 8, top + 8, 0x303030, false);

        // 顶部分页。
        int tabY = this.tabY();
        for (int index = 0; index < TABS.length; index++) {
            PartnerJob job = TABS[index];
            int x = this.tabX(index);
            boolean hovered = mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            boolean selected = job == this.tab;
            int fill;
            int textColor;
            if (selected) {
                fill = 0xFF4E76BC;
                textColor = 0xFFFFFFFF;
            } else if (hovered) {
                fill = 0xFF4C5478;
                textColor = 0xFFF2F4FA;
            } else {
                fill = 0xFF3A4060;
                textColor = 0xFFDDE1EC;
            }
            guiGraphics.fill(x - 1, tabY - 1, x + TAB_WIDTH + 1, tabY + TAB_HEIGHT + 1, 0xFF2A2C3A);
            guiGraphics.fill(x, tabY, x + TAB_WIDTH, tabY + TAB_HEIGHT, fill);
            if (selected) {
                // 选中页签上沿加一条亮线，和未选中区分开。
                guiGraphics.fill(x, tabY, x + TAB_WIDTH, tabY + 1, 0xFFA9BEE8);
            }
            Component label = Component.translatable(job.translationKey());
            int textX = x + (TAB_WIDTH - this.font.width(label)) / 2;
            guiGraphics.drawString(this.font, label, textX, tabY + 4, textColor, true);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (!this.isMiningTab()) {
            Component hint = Component.translatable("gui.player_partner.job_filter.no_filter");
            guiGraphics.drawString(this.font, hint,
                    left + (PANEL_WIDTH - this.font.width(hint)) / 2, top + LIST_TOP, 0xFF404040, false);
            return;
        }

        // 列表区域
        int listLeft = left + 8;
        int listTop = top + LIST_TOP;
        int listWidth = PANEL_WIDTH - 16;
        guiGraphics.fill(listLeft - 1, listTop - 1, listLeft + listWidth + 1, top + LIST_BOTTOM + 1, 0xFF8E93A4);

        if (this.entries.isEmpty()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.player_partner.mining_filter.empty"),
                    listLeft + 4, listTop + 2, 0xFF404040, false);
        } else {
            int visible = this.visibleRows();
            for (int row = 0; row < visible; row++) {
                int index = this.scroll + row;
                if (index >= this.entries.size()) {
                    break;
                }
                int y = listTop + row * ROW_HEIGHT;
                String entry = this.entries.get(index);
                guiGraphics.drawString(this.font, entry, listLeft + 4, y + 2, 0xFF1E1E1E, false);

                int deleteX = listLeft + listWidth - DELETE_SIZE - 2;
                boolean hovered = mouseX >= deleteX && mouseX < deleteX + DELETE_SIZE
                        && mouseY >= y && mouseY < y + DELETE_SIZE;
                guiGraphics.drawString(this.font, "x", deleteX + 2, y + 2, hovered ? 0xFFAA2222 : 0xFF555555, false);
            }
        }

        // 滚动条
        if (this.entries.size() > this.visibleRows()) {
            int barX = listLeft + listWidth - 3;
            int trackHeight = LIST_BOTTOM - LIST_TOP;
            guiGraphics.fill(barX, listTop, barX + 2, listTop + trackHeight, 0xFF6E7383);
            int thumbHeight = Math.max(8, trackHeight * this.visibleRows() / this.entries.size());
            int maxScroll = Math.max(1, this.entries.size() - this.visibleRows());
            int thumbY = listTop + (trackHeight - thumbHeight) * this.scroll / maxScroll;
            guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xFFE6E8F2);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 顶部页签
            int tabY = this.tabY();
            for (int index = 0; index < TABS.length; index++) {
                int x = this.tabX(index);
                if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                    if (TABS[index] != this.tab) {
                        this.tab = TABS[index];
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager()
                                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        }
                        this.rebuildWidgets();
                    }
                    return true;
                }
            }

            if (this.isMiningTab()) {
                int left = (this.width - PANEL_WIDTH) / 2;
                int top = (this.height - PANEL_HEIGHT) / 2;
                int listLeft = left + 8;
                int listTop = top + LIST_TOP;
                int listWidth = PANEL_WIDTH - 16;
                int deleteX = listLeft + listWidth - DELETE_SIZE - 2;
                int visible = this.visibleRows();

                for (int row = 0; row < visible; row++) {
                    int index = this.scroll + row;
                    if (index >= this.entries.size()) {
                        break;
                    }
                    int y = listTop + row * ROW_HEIGHT;
                    if (mouseX >= deleteX && mouseX < deleteX + DELETE_SIZE && mouseY >= y && mouseY < y + DELETE_SIZE) {
                        this.send(MiningFilterEditPayload.remove(this.entries.get(index)));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, this.entries.size() - this.visibleRows());
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 输入框里按回车直接添加。
        if (this.input != null && this.input.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            this.addFromInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
