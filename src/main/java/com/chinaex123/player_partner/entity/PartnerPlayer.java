package com.chinaex123.player_partner.entity;

import com.mojang.authlib.GameProfile;
import com.chinaex123.player_partner.client.menu.PlayerPartnerMenu;
import com.chinaex123.player_partner.world.PartnerManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PartnerPlayer extends ServerPlayer {
    public static final String MARKER = "player_partner";

    /** 主人的 UUID。 */
    @Nullable
    private UUID ownerUuid;
    /** 随机游走的目标点。 */
    @Nullable
    private Vec3 wanderTarget;
    /** 距离下一次重新选择游走目标的 tick 数。 */
    private int wanderCooldown;
    /** 距离下一次尝试进食的 tick 数。 */
    private int eatCooldown;
    /** 当前行为：闲逛 / 跟着主人 / 看着主人。 */
    private int behaviour = BEHAVIOUR_WANDER;
    /** 距离下次换行为还剩多少 tick。 */
    private int behaviourTicks;
    /** 闲站着四处张望的计时。 */
    private int idleLookTicks;
    /** 是否已经进入移除流程。 */
    private boolean removing;

    private static final int BEHAVIOUR_WANDER = 0;
    private static final int BEHAVIOUR_FOLLOW = 1;
    private static final int BEHAVIOUR_WATCH = 2;

    public PartnerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation information) {
        super(server, level, profile, information);
        this.getAttribute(Attributes.SCALE).setBaseValue(0.5D);
        this.refreshDimensions();
    }

    public static boolean isPartner(GameProfile profile) {
        return profile.getProperties().containsKey(MARKER);
    }

    public static UUID createUuid(String displayName) {
        return UUIDUtil.createOfflinePlayerUUID("PlayerPartner:" + displayName);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide || !this.isAlive()) {
            return InteractionResult.PASS;
        }

        // 潜行右键：打开伙伴的物品栏。
        if (player.isSecondaryUseActive()) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (containerId, inventory, owner) -> new PlayerPartnerMenu(containerId, inventory, this),
                            Component.translatable("container.player_partner.inventory")),
                    buffer -> buffer.writeUUID(this.getUUID()));
            return InteractionResult.sidedSuccess(false);
        }

        // 用命名牌改名（同时换皮肤）。
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            ItemStack stack = player.getMainHandItem();
            Component customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (stack.is(net.minecraft.world.item.Items.NAME_TAG) && customName != null) {
                String newName = customName.getString().trim();
                if (!newName.isEmpty()) {
                    if (!serverPlayer.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    PartnerManager.get(this.server).rename(this, newName, serverPlayer);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            this.tickBehaviour();
        }

        super.tick();
        this.doTick();

        if (!this.level().isClientSide && this.tickCount % 10 == 0) {
            this.connection.resetPosition();
            this.serverLevel().getChunkSource().move(this);
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (this.level().isClientSide) {
            return;
        }
        super.die(cause);
        if (!this.removing) {
            this.removing = true;
            MinecraftServer server = this.server;
            server.execute(() -> PartnerManager.get(server).handleDeath(this));
        }
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Nullable
    public Player getOwner() {
        if (this.ownerUuid == null) {
            return null;
        }
        return this.server.getPlayerList().getPlayer(this.ownerUuid);
    }

    public boolean isRemoving() {
        return this.removing;
    }

    private void tickBehaviour() {
        if (this.removing || !this.isAlive()) {
            return;
        }

        this.setJumping(false);

        this.tickEating();

        if (this.isUsingItem()) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
            this.setJumping(false);
            return;
        }

        Player owner = this.getOwner();
        if (owner == null || owner.level() != this.level() || owner.isSpectator()) {
            this.tickWander();
            return;
        }

        double distance = this.distanceToSqr(owner);
        if (distance > 1024.0D) {
            // 离得太远就直接传送到主人身边。
            this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            return;
        }
        // 走散太远时优先追回来，其余时间像普通生物一样自己活动。
        if (distance > 400.0D) {
            this.behaviour = BEHAVIOUR_FOLLOW;
            this.behaviourTicks = 80 + this.random.nextInt(80);
        } else if (--this.behaviourTicks <= 0) {
            this.pickBehaviour(distance);
        }

        switch (this.behaviour) {
            case BEHAVIOUR_FOLLOW -> {
                if (distance > 16.0D) {
                    this.moveTowards(owner.position());
                } else {
                    // 已经走到主人身边了，换回闲逛或抬头看一看，
                    this.zza = 0.0F;
                    this.xxa = 0.0F;
                    this.behaviour = this.random.nextBoolean() ? BEHAVIOUR_WANDER : BEHAVIOUR_WATCH;
                    this.behaviourTicks = 100 + this.random.nextInt(100);
                    this.wanderTarget = null;
                }
            }
            case BEHAVIOUR_WATCH -> {
                this.lookAt(owner.getX(), owner.getEyeY() + 0.15D, owner.getZ());
                this.zza = 0.0F;
                this.xxa = 0.0F;
            }
            default -> {
                this.tickWander();
                // 只有在站着不动的时候才四处张望，走路时朝行进方向看。
                if (this.zza == 0.0F) {
                    this.tickIdleLook();
                }
            }
        }
    }

    private void pickBehaviour(double distanceToOwner) {
        if (distanceToOwner > 64.0D) {
            // 离得比较远了，先跟过去。
            this.behaviour = BEHAVIOUR_FOLLOW;
            this.behaviourTicks = 100 + this.random.nextInt(80);
            return;
        }

        int roll = this.random.nextInt(100);
        if (roll < 55) {
            this.behaviour = BEHAVIOUR_WANDER;
            this.behaviourTicks = 120 + this.random.nextInt(160);
            this.wanderTarget = null;
        } else if (roll < 85) {
            this.behaviour = BEHAVIOUR_FOLLOW;
            this.behaviourTicks = 60 + this.random.nextInt(80);
        } else {
            this.behaviour = BEHAVIOUR_WATCH;
            this.behaviourTicks = 40 + this.random.nextInt(60);
        }
    }

    private void tickIdleLook() {
        if (--this.idleLookTicks > 0) {
            return;
        }
        this.idleLookTicks = 40 + this.random.nextInt(60);

        float targetYaw = this.getYRot() + (this.random.nextFloat() - 0.5F) * 120.0F;
        float targetPitch = (this.random.nextFloat() - 0.4F) * 40.0F;

        float yawDelta = Mth.wrapDegrees(targetYaw - this.getYRot());
        float yaw = this.getYRot() + Mth.clamp(yawDelta, -8.0F, 8.0F);
        this.setYRot(yaw);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
        this.setXRot((float) Mth.approach(this.getXRot(), Mth.clamp(targetPitch, -25.0F, 25.0F), 6.0F));
    }

    private void moveTowards(Vec3 target) {
        double dx = target.x - this.getX();
        double dz = target.z - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.8D) {
            this.zza = 0.0F;
            return;
        }

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;

        this.zza = 1.0F;
        this.xxa = 0.0F;

        if (this.isInWater()) {
            this.setJumping(true);
        } else if (this.horizontalCollision && this.onGround() && this.random.nextInt(6) == 0) {
            this.setJumping(true);
        }
    }

    private void tickWander() {
        if (--this.wanderCooldown <= 0 || this.wanderTarget == null) {
            this.wanderCooldown = 60 + this.random.nextInt(80);
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + this.random.nextDouble() * 4.0D;
            this.wanderTarget = new Vec3(this.getX() + Math.cos(angle) * distance, this.getY(),
                    this.getZ() + Math.sin(angle) * distance);
        }

        if (this.wanderTarget.distanceToSqr(this.position()) < 1.0D) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
            return;
        }

        this.moveTowards(this.wanderTarget);
    }

    private void lookAt(double x, double y, double z) {
        double dx = x - this.getX();
        double dy = y - this.getEyeY();
        double dz = z - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));
        pitch = Mth.clamp(pitch, -30.0F, -4.0F);

        if (horizontal > 0.05D) {
            float yawDelta = Mth.wrapDegrees(yaw - this.getYRot());
            this.setYRot(this.getYRot() + Mth.clamp(yawDelta, -15.0F, 15.0F));
            this.yHeadRot = this.getYRot();
            this.yBodyRot = this.getYRot();
        }
        this.setXRot((float) Mth.approach(this.getXRot(), pitch, 15.0F));
    }

    private void tickEating() {
        if (this.isUsingItem()) {
            return;
        }
        if (this.getFoodData().getFoodLevel() >= 20) {
            return;
        }
        if (--this.eatCooldown > 0) {
            return;
        }
        this.eatCooldown = 60;

        int slot = this.findBestFoodSlot();
        if (slot < 0) {
            return;
        }

        Inventory inventory = this.getInventory();
        ItemStack food = inventory.getItem(slot);
        if (food.isEmpty()) {
            return;
        }

        if (slot != Inventory.SLOT_OFFHAND) {
            ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
            inventory.setItem(Inventory.SLOT_OFFHAND, food);
            inventory.setItem(slot, offhand);
        }

        this.startUsingItem(InteractionHand.OFF_HAND);
    }

    private int findBestFoodSlot() {
        Inventory inventory = this.getInventory();
        int bestGood = -1;
        int bestGoodNutrition = -1;
        int bestAny = -1;
        int bestAnyNutrition = -1;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            FoodProperties food = stack.getFoodProperties(this);
            if (food == null) {
                continue;
            }
            if (this.hasHarmfulEffect(food)) {
                if (food.nutrition() > bestAnyNutrition) {
                    bestAnyNutrition = food.nutrition();
                    bestAny = slot;
                }
            } else if (food.nutrition() > bestGoodNutrition) {
                bestGoodNutrition = food.nutrition();
                bestGood = slot;
            }
        }

        return bestGood >= 0 ? bestGood : bestAny;
    }

    private boolean hasHarmfulEffect(FoodProperties food) {
        return food.effects().stream().anyMatch(possible ->
                !possible.effect().getEffect().value().isBeneficial());
    }
}
