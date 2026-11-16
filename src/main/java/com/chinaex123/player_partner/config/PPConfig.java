package com.chinaex123.player_partner.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class PPConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue PARTNER_LIMIT;
    public static final ModConfigSpec.IntValue JOB_RADIUS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MINING_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MINING_BLACKLIST;
    public static final ModConfigSpec.DoubleValue PARTNER_MAX_HEALTH;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("通用配置").push("Common Config");
        PARTNER_LIMIT = builder
                .comment("同时存在的伙伴数量上限")
                .comment("Maximum number of simultaneous partners")
                .defineInRange("partnerLimit", 5, 1, 64);
        PARTNER_MAX_HEALTH = builder
                .comment("伙伴的最大生命值")
                .comment("Partner's max health")
                .defineInRange("partnerMaxHealth", 100.0D, 20.0D, 1024.0D);

        builder.comment("伙伴接活").push("Partner takes on jobs");
        JOB_RADIUS = builder
                .comment("接活时搜索目标的半径")
                .comment("Radius used when looking for job targets")
                .defineInRange("jobRadius", 8, 4, 32);
        MINING_WHITELIST = builder
                .comment("挖矿模式允许挖的方块列表，支持tag和单个方块ID。留空表示“所有方块都算矿石”，此时只看黑名单。")
                .comment("Mining Mode's list of blocks that can be mined, supporting tags and individual block IDs. Leave empty to mean \"all blocks count as ores\"; in this case, only the blacklist is considered.")
                .defineListAllowEmpty("miningWhitelist", List.of("#c:ores"), () -> "#c:ores",
                        entry -> entry instanceof String);
        MINING_BLACKLIST = builder
                .comment("挖矿模式永远不挖的方块列表，支持tag和单个方块ID。黑名单优先于白名单。")
                .comment("Mining Mode's list of blocks that must never be mined, supporting tags and individual block IDs. Blacklist wins over whitelist.")
                .defineListAllowEmpty("miningBlacklist", List.of(), () -> "minecraft:stone",
                        entry -> entry instanceof String);
        builder.pop();

        SPEC = builder.build();
    }
}
