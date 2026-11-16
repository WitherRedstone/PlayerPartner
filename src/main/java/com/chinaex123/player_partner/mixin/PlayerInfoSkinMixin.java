package com.chinaex123.player_partner.mixin;

import com.chinaex123.player_partner.client.skin.PartnerDefaultSkin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {

    @Inject(
            method = "getSkin",
            at = @At("HEAD"),
            cancellable = true
    )
    private void playerPartner$useBundledSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        if (PartnerDefaultSkin.hasNoSkin(self.getProfile())) {
            callback.setReturnValue(PartnerDefaultSkin.SKIN);
        }
    }
}
