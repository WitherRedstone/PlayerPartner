package com.chinaex123.player_partner.mixin;

import com.chinaex123.player_partner.entity.PartnerPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;
import java.util.List;

@Mixin(SleepStatus.class)
public class SleepStatusMixin {

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<ServerPlayer> playerPartner$skipPartners(List<ServerPlayer> players) {
        return players.stream().filter(player -> !(player instanceof PartnerPlayer)).iterator();
    }
}
