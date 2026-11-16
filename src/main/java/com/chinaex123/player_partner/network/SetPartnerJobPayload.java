package com.chinaex123.player_partner.network;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PartnerJob;
import com.chinaex123.player_partner.entity.PartnerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetPartnerJobPayload(int entityId, int job) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetPartnerJobPayload> TYPE =
            new CustomPacketPayload.Type<>(PlayerPartner.id("set_partner_job"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPartnerJobPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetPartnerJobPayload::entityId,
            ByteBufCodecs.VAR_INT, SetPartnerJobPayload::job,
            SetPartnerJobPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetPartnerJobPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            Entity entity = sender.level().getEntity(payload.entityId());
            if (!(entity instanceof PartnerPlayer partner)) {
                return;
            }
            // 只有主人才能给伙伴派活，避免别人乱点。
            PartnerJob job = PartnerJob.byId(payload.job());
            boolean isOwner = partner.getOwnerUuid() != null && partner.getOwnerUuid().equals(sender.getUUID());
            if (!isOwner && !sender.hasPermissions(2)) {
                return;
            }
            partner.setJob(job);
        });
    }
}
