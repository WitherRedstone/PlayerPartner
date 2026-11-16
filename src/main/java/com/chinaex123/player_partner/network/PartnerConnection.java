package com.chinaex123.player_partner.network;

import com.chinaex123.player_partner.mixin.ConnectionAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.ProtocolInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartnerConnection extends Connection {

    public PartnerConnection() {
        super(PacketFlow.SERVERBOUND);
        ((ConnectionAccessor) this).playerPartner$setChannel(new EmbeddedChannel());
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
    public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
        // 丢弃。
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener) {
        // 没有网络管线，无需初始化。
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocolInfo) {
        // 没有网络管线，无需初始化。
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        // 不需要握手。
    }

    @Override
    public void setReadOnly() {
        // 没有通道可设置。
    }

    @Override
    public void handleDisconnection() {
        // 永不掉线。
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public boolean isMemoryConnection() {
        return true;
    }

    @Override
    public void disconnect(Component reason) {
        // 不处理断开。
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        // 不处理断开。
    }

    @Override
    public void tick() {
        // 没有要处理的网络事件。
    }

    @Override
    public @NotNull String getLoggableAddress(boolean logIps) {
        return "127.0.0.1";
    }
}
