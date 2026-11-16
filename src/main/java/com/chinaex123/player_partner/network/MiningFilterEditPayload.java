package com.chinaex123.player_partner.network;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.world.MiningRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MiningFilterEditPayload(int action, String entry) implements CustomPacketPayload {
    public static final int ACTION_WHITELIST = 0;
    public static final int ACTION_BLACKLIST = 1;
    public static final int ACTION_ADD = 2;
    public static final int ACTION_REMOVE = 3;
    public static final int ACTION_CLEAR = 4;

    public static final CustomPacketPayload.Type<MiningFilterEditPayload> TYPE =
            new CustomPacketPayload.Type<>(PlayerPartner.id("mining_filter_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiningFilterEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MiningFilterEditPayload::action,
            ByteBufCodecs.STRING_UTF8, MiningFilterEditPayload::entry,
            MiningFilterEditPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MiningFilterEditPayload mode(boolean whitelist) {
        return new MiningFilterEditPayload(whitelist ? ACTION_WHITELIST : ACTION_BLACKLIST, "");
    }

    public static MiningFilterEditPayload add(String entry) {
        return new MiningFilterEditPayload(ACTION_ADD, entry);
    }

    public static MiningFilterEditPayload remove(String entry) {
        return new MiningFilterEditPayload(ACTION_REMOVE, entry);
    }

    public static MiningFilterEditPayload clear() {
        return new MiningFilterEditPayload(ACTION_CLEAR, "");
    }

    public static void handle(MiningFilterEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            if (!sender.hasPermissions(0)) {
                return;
            }
            MiningRules rules = MiningRules.get(sender.server);
            switch (payload.action()) {
                case ACTION_WHITELIST -> rules.setWhitelistMode(true);
                case ACTION_BLACKLIST -> rules.setWhitelistMode(false);
                case ACTION_ADD -> {
                    if (!rules.add(payload.entry())) {
                        // 名字不对（方块不存在、标签写法不对）时不加进去，直接在物品栏上方提示一下。
                        sender.displayClientMessage(
                                Component.translatable("message.player_partner.mining_invalid", payload.entry()), true);
                    }
                }
                case ACTION_REMOVE -> rules.remove(payload.entry());
                case ACTION_CLEAR -> rules.clear();
                default -> {
                }
            }
            PacketDistributor.sendToPlayer(sender, MiningFilterSyncPayload.of(sender.server));
        });
    }
}
