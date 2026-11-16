package com.chinaex123.player_partner.mixin;

import com.chinaex123.player_partner.entity.PartnerPlayer;
import com.chinaex123.player_partner.network.PartnerPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Redirect(
            method = "placeNewPlayer",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/network/ServerGamePacketListenerImpl;")
    )
    private ServerGamePacketListenerImpl playerPartner$createListener(MinecraftServer server, Connection connection,
                                                                     ServerPlayer player, CommonListenerCookie cookie) {
        if (player instanceof PartnerPlayer) {
            return new PartnerPacketListener(server, connection, player, cookie);
        }
        return new ServerGamePacketListenerImpl(server, connection, player, cookie);
    }
}
