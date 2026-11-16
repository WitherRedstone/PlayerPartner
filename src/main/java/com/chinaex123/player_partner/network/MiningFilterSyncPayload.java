package com.chinaex123.player_partner.network;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.client.gui.JobFilterScreen;
import com.chinaex123.player_partner.world.MiningRules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record MiningFilterSyncPayload(boolean whitelist, List<String> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MiningFilterSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(PlayerPartner.id("mining_filter_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiningFilterSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MiningFilterSyncPayload::whitelist,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), MiningFilterSyncPayload::entries,
            MiningFilterSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MiningFilterSyncPayload of(MinecraftServer server) {
        MiningRules rules = MiningRules.get(server);
        return new MiningFilterSyncPayload(rules.isWhitelistMode(), new ArrayList<>(rules.getEntries()));
    }

    public static void handle(MiningFilterSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            JobFilterScreen.acceptRules(payload.whitelist(), payload.entries());
            if (Minecraft.getInstance().screen instanceof JobFilterScreen screen) {
                screen.refreshFromCache();
            }
        });
    }
}
