package com.chinaex123.player_partner.network;

import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

public class PartnerPacketListener extends ServerGamePacketListenerImpl {

    public PartnerPacketListener(MinecraftServer server, Connection connection, ServerPlayer player,
                                 CommonListenerCookie cookie) {
        super(server, connection, player, cookie);
    }

    @Override
    public void send(Packet<?> packet) {
        // 丢弃。
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        // 丢弃。
    }

    @Override
    public void disconnect(Component reason) {
        // 伙伴不会被踢下线。
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        // 伙伴不会被踢下线。
    }
}
