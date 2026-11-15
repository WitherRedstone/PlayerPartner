package com.chinaex123.player_partner.inventory;

import com.chinaex123.player_partner.entity.PlayerPartnerEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class PlayerPartnerMenu extends AbstractContainerMenu {
    /** 装备槽数量。 */
    public static final int EQUIPMENT_SLOTS = 6;
    /** 储物栏数量。 */
    public static final int STORAGE_SLOTS = PlayerPartnerEntity.STORAGE_SIZE;
    /** 玩家背包（不含快捷栏）的槽位起点。 */
    public static final int PLAYER_INV_START = EQUIPMENT_SLOTS + STORAGE_SLOTS;
    /** 快捷栏的槽位起点。 */
    public static final int HOTBAR_START = PLAYER_INV_START + 27;

    /** 界面贴图大小。 */
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 222;

    /** 装备槽的纵坐标。 */
    private static final int EQUIPMENT_Y = 18;
    /** 储物栏第一排的纵坐标。 */
    private static final int STORAGE_Y = 40;
    /** 玩家背包第一排的纵坐标。 */
    private static final int PLAYER_INV_Y = 124;
    /** 快捷栏的纵坐标。 */
    private static final int HOTBAR_Y = 182;

    private final PlayerPartnerEntity partner;

    /** 服务端构造：直接使用伙伴自己的容器。 */
    public PlayerPartnerMenu(int containerId, Inventory playerInventory, PlayerPartnerEntity partner) {
        this(containerId, playerInventory, partner, partner.getEquipment(), partner.getStorage());
    }

    /** 客户端构造：从网络包里读出伙伴的实体 id。 */
    public PlayerPartnerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, readPartner(playerInventory, data));
    }

    private PlayerPartnerMenu(int containerId, Inventory playerInventory, PlayerPartnerEntity partner,
                              Container equipment, Container storage) {
        super(com.chinaex123.player_partner.init.PPMenus.PLAYER_PARTNER_MENU.get(), containerId);
        this.partner = partner;

        // 装备槽：主手、副手、头盔、胸甲、护腿、靴子。
        for (int i = 0; i < EQUIPMENT_SLOTS; i++) {
            this.addSlot(new EquipmentMenuSlot(equipment, i, 8 + i * 18, EQUIPMENT_Y));
        }

        // 储物栏：三排，每排九个。
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(storage, column + row * 9, 8 + column * 18, STORAGE_Y + row * 18));
            }
        }

        // 玩家背包：三排，每排九个。
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // 玩家快捷栏。
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    private static PlayerPartnerEntity readPartner(Inventory playerInventory, RegistryFriendlyByteBuf data) {
        int entityId = data.readVarInt();
        Level level = playerInventory.player.level();
        if (level.getEntity(entityId) instanceof PlayerPartnerEntity partner) {
            return partner;
        }
        throw new IllegalStateException("找不到玩家伙伴实体：" + entityId);
    }

    public PlayerPartnerEntity getPartner() {
        return this.partner;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.partner.isAlive() && player.distanceToSqr(this.partner) <= 64.0D;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < PLAYER_INV_START) {
            // 装备栏 / 储物栏 → 移到玩家背包。
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, EQUIPMENT_SLOTS, false)
                && !this.moveItemStackTo(stack, EQUIPMENT_SLOTS, PLAYER_INV_START, false)) {
            // 玩家背包 → 先试装备槽（盔甲会自动穿上），再放进储物栏。
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    private static class EquipmentMenuSlot extends Slot {
        EquipmentMenuSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.container.canPlaceItem(this.getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }
}
