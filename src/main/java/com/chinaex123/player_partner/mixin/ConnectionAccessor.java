package com.chinaex123.player_partner.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ConnectionAccessor {

    @Accessor("channel")
    void playerPartner$setChannel(@Nullable Channel channel);
}
