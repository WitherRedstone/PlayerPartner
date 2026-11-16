package com.chinaex123.player_partner.network;

import com.chinaex123.player_partner.PlayerPartner;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MiningFilterRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MiningFilterRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(PlayerPartner.id("mining_filter_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiningFilterRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new MiningFilterRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MiningFilterRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                PacketDistributor.sendToPlayer(sender, MiningFilterSyncPayload.of(sender.server));
            }
        });
    }
}
