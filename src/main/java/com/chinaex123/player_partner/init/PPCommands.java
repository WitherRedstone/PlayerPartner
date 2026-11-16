package com.chinaex123.player_partner.init;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PartnerPlayer;
import com.chinaex123.player_partner.client.menu.PlayerPartnerMenu;
import com.chinaex123.player_partner.world.PartnerManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public final class PPCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playerpartner")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(context -> spawn(context, PlayerPartner.DEFAULT_DISPLAY_NAME, PlayerPartner.DEFAULT_SKIN_NAME))
                        .then(Commands.argument("skin", StringArgumentType.word())
                                .executes(context -> {
                                    String skin = StringArgumentType.getString(context, "skin");
                                    return spawn(context, skin, skin);
                                })))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> rename(context, StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("remove")
                        .executes(PPCommands::removeNearest))
                .then(Commands.literal("open")
                        .executes(PPCommands::openNearest))
                .then(Commands.literal("list")
                        .executes(PPCommands::list)));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String displayName, String skinName) {
        ServerLevel level;
        Vec3 pos;
        float yaw;
        ServerPlayer owner = context.getSource().getPlayer();
        ServerPlayer feedback = owner;
        if (owner != null) {
            if (!(owner.level() instanceof ServerLevel ownerLevel)) {
                return 0;
            }
            level = ownerLevel;
            pos = owner.position().add(owner.getLookAngle().scale(2.0D));
            yaw = owner.getYRot();
        } else {
            // 命令方块 / 控制台：在命令源的位置生成。
            level = context.getSource().getLevel();
            pos = context.getSource().getPosition();
            yaw = 0.0F;
        }
        PartnerManager.get(level.getServer()).spawn(displayName, skinName,
                owner == null ? null : owner.getUUID(), level, pos, yaw, feedback);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context, String newName) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            context.getSource().sendFailure(Component.translatable("message.player_partner.need_player"));
            return 0;
        }

        PartnerManager manager = PartnerManager.get(level.getServer());
        PartnerPlayer partner = findNearest(manager, player);
        if (partner == null) {
            context.getSource().sendFailure(Component.translatable("message.player_partner.not_found"));
            return 0;
        }

        manager.rename(partner, newName, player);
        return 1;
    }

    private static int removeNearest(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            context.getSource().sendFailure(Component.translatable("message.player_partner.need_player"));
            return 0;
        }

        PartnerManager manager = PartnerManager.get(level.getServer());
        PartnerPlayer partner = findNearest(manager, player);
        if (partner == null) {
            context.getSource().sendFailure(Component.translatable("message.player_partner.not_found"));
            return 0;
        }
        manager.remove(partner, true);
        context.getSource().sendSuccess(() -> Component.translatable("message.player_partner.removed",
                partner.getGameProfile().getName()), true);
        return 1;
    }

    private static int openNearest(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            context.getSource().sendFailure(Component.translatable("message.player_partner.need_player"));
            return 0;
        }

        PartnerManager manager = PartnerManager.get(level.getServer());
        PartnerPlayer partner = findNearest(manager, player);
        if (partner == null) {
            context.getSource().sendFailure(Component.translatable("message.player_partner.not_found"));
            return 0;
        }

        OptionalInt opened = player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new PlayerPartnerMenu(containerId, inventory, partner),
                        partner.getDisplayNameForMenu()),
                buffer -> buffer.writeUUID(partner.getUUID()));
        if (opened.isEmpty()) {
            context.getSource().sendFailure(Component.literal("无法打开伙伴的物品栏"));
            return 0;
        }
        return 1;
    }
    private static int list(CommandContext<CommandSourceStack> context) {
        PartnerManager manager = PartnerManager.get(context.getSource().getServer());
        List<String> names = manager.getRecords().stream()
                .map(PartnerManager.PartnerRecord::displayName)
                .collect(Collectors.toList());
        if (names.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("message.player_partner.list_empty"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("message.player_partner.list",
                    String.join("、", names)), false);
        }
        return 1;
    }

    private static PartnerPlayer findNearest(PartnerManager manager, Player player) {
        PartnerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (PartnerPlayer partner : manager.getActivePartners()) {
            if (partner.level() != player.level()) {
                continue;
            }
            double distance = partner.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                nearest = partner;
            }
        }
        return nearest;
    }
}
