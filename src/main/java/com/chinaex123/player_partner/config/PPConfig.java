package com.chinaex123.player_partner.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PPConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue PARTNER_LIMIT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        PARTNER_LIMIT = builder
                .comment("同时存在的伙伴数量上限")
                .comment("Maximum number of simultaneous partners")
                .defineInRange("partnerLimit", 5, 1, 64);

        SPEC = builder.build();
    }
}
