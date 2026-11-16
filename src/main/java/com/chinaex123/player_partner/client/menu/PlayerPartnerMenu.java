package com.chinaex123.player_partner.client.menu;

import com.chinaex123.player_partner.entity.PartnerPlayer;
import com.chinaex123.player_partner.init.PPMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerPartnerMenu extends AbstractContainerMenu {

    public static final int IMAGE_WIDTH = 178;
    public static final int IMAGE_HEIGHT = 222;

    /** 装备区域槽位数量：主手、副手、头盔、胸甲、护腿、靴子。 */
    public static final int EQUIPMENT_SLOTS = 6;
    /** 主手槽在菜单里的索引。 */
    public static final int MAIN_HAND_SLOT = 0;
    /** 副手槽在菜单里的索引。 */
    public static final int OFF_HAND_SLOT = 1;
    /** 伙伴背包格数。 */
    public static final int PARTNER_INV_SLOTS = 36;
    /** 伙伴背包起始索引。 */
    public static final int PARTNER_INV_START = EQUIPMENT_SLOTS;
    /** 玩家背包起始索引。 */
    public static final int PLAYER_INV_START = EQUIPMENT_SLOTS + PARTNER_INV_SLOTS;
    /** 玩家快捷栏起始索引。 */
    public static final int HOTBAR_START = PLAYER_INV_START + 27;

    private static final int EQUIPMENT_Y = 19;

    private static final int GENERAL_INV_X = 9;
    private static final int PARTNER_INV_Y = 41;
    private static final int PARTNER_HOTBAR_Y = 99;
    private static final int PLAYER_INV_Y = 139;
    private static final int HOTBAR_Y = 197;

    /** 护甲槽对应的玩家背包索引：头、胸、腿、脚。 */
    private static final int[] ARMOR_INDICES = {
            39, 38, 37, 36
    };

    /** 护甲槽对应的玩家背包索引：头、胸、腿、脚。 */
    private static final EquipmentSlot[] ARMOR_TYPES = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Player partner;

    public PlayerPartnerMenu(int containerId, Inventory playerInventory, Player partner) {
        super(PPMenus.PLAYER_PARTNER_MENU.get(), containerId);
        this.partner = partner;
        this.addSlots(playerInventory);

        // 界面打开期间伙伴站着不动。
        if (!playerInventory.player.level().isClientSide && partner instanceof PartnerPlayer partnerPlayer) {
            partnerPlayer.onMenuOpened();
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 界面关掉后伙伴恢复活动。
        if (!player.level().isClientSide && this.partner instanceof PartnerPlayer partnerPlayer) {
            partnerPlayer.onMenuClosed();
        }
    }

    public PlayerPartnerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, readPartner(data));
    }

    private static Player readPartner(RegistryFriendlyByteBuf data) {
        UUID uuid = data.readUUID();
        Player partner = Minecraft.getInstance().level == null
                ? null : Minecraft.getInstance().level.getPlayerByUUID(uuid);
        if (partner == null) {
            throw new IllegalStateException("找不到玩家伙伴实体：" + uuid);
        }
        return partner;
    }

    private void addSlots(Inventory playerInventory) {
        Inventory partnerInventory = this.partner.getInventory();

        // 主手
        this.addSlot(new MainHandSlot(partnerInventory, GENERAL_INV_X, EQUIPMENT_Y));

        // 副手
        this.addSlot(new Slot(partnerInventory, Inventory.SLOT_OFFHAND, GENERAL_INV_X + 18, EQUIPMENT_Y) {
            @Override
            public int getMaxStackSize() {
                return 64;
            }
        });

        // 护甲：头盔、胸甲、护腿、靴子
        Player partner = this.partner;
        for (int i = 0; i < ARMOR_INDICES.length; i++) {
            int containerSlot = ARMOR_INDICES[i];
            EquipmentSlot equipmentSlot = ARMOR_TYPES[i];
            this.addSlot(new Slot(partnerInventory, containerSlot, GENERAL_INV_X + (3 + i) * 18, EQUIPMENT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return partner.getEquipmentSlotForItem(stack) == equipmentSlot;
                }

                @Override
                public int getMaxStackSize() {
                    return 64;
                }
            });
        }

        // 伙伴主背包
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(partnerInventory, 9 + row * 9 + column, GENERAL_INV_X + column * 18, PARTNER_INV_Y + row * 18));
            }
        }
        // 伙伴快捷栏
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(partnerInventory, column, GENERAL_INV_X + column * 18, PARTNER_HOTBAR_Y));
        }

        // 玩家主背包
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + column, GENERAL_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        // 玩家快捷栏
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, GENERAL_INV_X + column * 18, HOTBAR_Y));
        }
    }

    public Player getPartner() {
        return this.partner;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.partner.isAlive() && this.partner.distanceToSqr(player) <= 32.0D * 32.0D;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < PLAYER_INV_START) {
                // 伙伴那边 → 玩家背包
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 玩家背包 → 先试护甲槽与副手，再试伙伴背包
                if (!this.moveItemStackTo(stack, 2, EQUIPMENT_SLOTS, false)
                        && !this.moveItemStackTo(stack, OFF_HAND_SLOT, OFF_HAND_SLOT + 1, false)
                        && !this.moveItemStackTo(stack, PARTNER_INV_START, PLAYER_INV_START, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    /** 主手槽。 */
    private static class MainHandSlot extends Slot {
        private final Inventory inventory;

        MainHandSlot(Inventory inventory, int x, int y) {
            super(inventory, 0, x, y);
            this.inventory = inventory;
        }

        @Override
        public ItemStack getItem() {
            return this.inventory.getItem(this.inventory.selected);
        }

        @Override
        public void set(ItemStack stack) {
            this.inventory.setItem(this.inventory.selected, stack);
        }

        @Override
        public boolean hasItem() {
            return !this.getItem().isEmpty();
        }

        @Override
        public ItemStack remove(int amount) {
            return this.inventory.removeItem(this.inventory.selected, amount);
        }

        @Override
        public void setChanged() {
            this.inventory.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }
}
