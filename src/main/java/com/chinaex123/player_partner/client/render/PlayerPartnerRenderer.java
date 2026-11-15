package com.chinaex123.player_partner.client.render;

import com.chinaex123.player_partner.client.skin.PartnerSkins;
import com.chinaex123.player_partner.entity.PlayerPartnerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PlayerPartnerRenderer extends LivingEntityRenderer<PlayerPartnerEntity, PlayerModel<PlayerPartnerEntity>> {

    /** 宽臂模型。 */
    private final PlayerModel<PlayerPartnerEntity> wideModel;
    /** 细臂模型。 */
    private final PlayerModel<PlayerPartnerEntity> slimModel;

    public PlayerPartnerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.25F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        HumanoidArmorModel<PlayerPartnerEntity> innerArmor =
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        HumanoidArmorModel<PlayerPartnerEntity> outerArmor =
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        this.addLayer(new HumanoidArmorLayer<>(this, innerArmor, outerArmor, context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(PlayerPartnerEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // 每帧根据当前皮肤决定使用哪套模型。
        PlayerSkin skin = PartnerSkins.getSkin(entity);
        PlayerModel<PlayerPartnerEntity> model = skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;
        model.setAllVisible(true);
        this.model = model;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(PlayerPartnerEntity entity) {
        return PartnerSkins.getSkin(entity).texture();
    }

    @Override
    protected void scale(PlayerPartnerEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.5F, 0.5F, 0.5F);
    }
}
