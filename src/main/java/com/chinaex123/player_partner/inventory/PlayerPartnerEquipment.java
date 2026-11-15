package com.chinaex123.player_partner.inventory;

import com.chinaex123.player_partner.entity.PlayerPartnerEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlayerPartnerEquipment implements Container {
    /** 菜单里装备槽的顺序。 */
    public static final EquipmentSlot[] SLOT_ORDER = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final PlayerPartnerEntity partner;

    public PlayerPartnerEquipment(PlayerPartnerEntity partner) {
        this.partner = partner;
    }

    @Override
    public int getContainerSize() {
        return SLOT_ORDER.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : SLOT_ORDER) {
            if (!this.partner.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int index) {
        return this.partner.getItemBySlot(SLOT_ORDER[index]);
    }

    @Override
    public @NotNull ItemStack removeItem(int index, int count) {
        ItemStack stack = this.getItem(index);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = stack.split(count);
        if (stack.isEmpty()) {
            this.partner.setItemSlot(SLOT_ORDER[index], ItemStack.EMPTY);
        }
        return removed;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int index) {
        ItemStack stack = this.getItem(index);
        this.partner.setItemSlot(SLOT_ORDER[index], ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.partner.setItemSlot(SLOT_ORDER[index], stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return this.partner.isAlive() && player.distanceToSqr(this.partner) <= 64.0D;
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot slot : SLOT_ORDER) {
            this.partner.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        EquipmentSlot slot = SLOT_ORDER[index];
        // 主手和副手什么都能拿。
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            return true;
        }
        // 其它槽位交给原版判断：盔甲、鞘翅等会返回自己该穿在哪个部位，普通物品返回主手。
        return this.partner.getEquipmentSlotForItem(stack) == slot;
    }
}
