package com.chinaex123.player_partner.mixin;

import com.chinaex123.player_partner.entity.PartnerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerTouchMixin {

    @Inject(
            method = "touch(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void playerPartner$skipItemPickup(Entity entity, CallbackInfo callback) {
        if (entity instanceof ItemEntity && playerPartner$isPartner(this)) {
            callback.cancel();
        }
    }

    @Unique
    private static boolean playerPartner$isPartner(Object self) {
        return self instanceof PartnerPlayer;
    }
}
