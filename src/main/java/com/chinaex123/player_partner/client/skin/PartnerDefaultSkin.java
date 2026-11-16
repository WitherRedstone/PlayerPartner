package com.chinaex123.player_partner.client.skin;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PartnerPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class PartnerDefaultSkin {

    public static final ResourceLocation TEXTURE = PlayerPartner.id("textures/entity/default_skin.png");

    public static final PlayerSkin SKIN =
            new PlayerSkin(TEXTURE, null, null, null, PlayerSkin.Model.WIDE, false);

    private PartnerDefaultSkin() {
    }

    public static void register() {
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new SimpleTexture(TEXTURE));
    }

    public static boolean hasNoSkin(GameProfile profile) {
        return PartnerPlayer.isPartner(profile) && !profile.getProperties().containsKey("textures");
    }
}
