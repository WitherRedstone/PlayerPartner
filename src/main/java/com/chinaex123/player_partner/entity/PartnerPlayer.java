package com.chinaex123.player_partner.entity;

import com.mojang.authlib.GameProfile;
import com.chinaex123.player_partner.client.menu.PlayerPartnerMenu;
import com.chinaex123.player_partner.world.PartnerManager;
import net.minecraft.core.BlockPos;
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
    /** 当前行为：闲逛 / 走远时往主人那边走 / 看着主人。 */
    private int behaviour = BEHAVIOUR_WANDER;
    /** 距离下次换行为还剩多少 tick。 */
    private int behaviourTicks;
    /** 闲站着四处张望的计时。 */
    private int idleLookTicks;
    /** 走远后尝试走向主人的次数。 */
    private int followAttempts;
    /** 本次尝试已经走了多少 tick。 */
    private int followTicks;
    /** 两次尝试之间的冷却 tick。 */
    private int followCooldown;
    /** 追主人时连续“走不动”的 tick 数。 */
    private int followStuckTicks;
    /** 有多少玩家正开着这个伙伴的物品栏；大于 0 时伙伴站着不动。 */
    private int menuViewers;
    /** 是否已经进入移除流程。 */
    private boolean removing;

    private static final int BEHAVIOUR_WANDER = 0;
    private static final int BEHAVIOUR_FOLLOW = 1;
    private static final int BEHAVIOUR_WATCH = 2;

    /** 超过 64 格才算“走远了”，才会尝试主动走过去。 */
    private static final double FAR_DISTANCE_SQR = 64.0D * 64.0D;
    /** 一次尝试最多走 5 秒。 */
    private static final int FOLLOW_ATTEMPT_TICKS = 100;
    /** 两次尝试之间冷却 2 秒。 */
    private static final int FOLLOW_COOLDOWN_TICKS = 40;
    /** 最多尝试 3 次，还走不到就直接瞬移。 */
    private static final int MAX_FOLLOW_ATTEMPTS = 3;

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

    /** 有玩家打开这个伙伴的物品栏时记一笔，界面关掉之前伙伴不会乱走。 */
    public void onMenuOpened() {
        this.menuViewers++;
    }

    public void onMenuClosed() {
        this.menuViewers = Math.max(0, this.menuViewers - 1);
    }

    public boolean isMenuOpen() {
        return this.menuViewers > 0;
    }

    private void tickBehaviour() {
        if (this.removing || !this.isAlive()) {
            return;
        }

        this.setJumping(false);

        // 有人正开着伙伴的物品栏：站着别动，看着界面就行，等界面关掉再继续活动。
        if (this.isMenuOpen()) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
            return;
        }

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

        // 意外保护：掉到虚空（或掉得太深）就立刻回到主人身边，别让伙伴就这么没了。
        if (this.getY() < this.level().getMinBuildHeight() - 8.0D) {
            this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            this.resetFollowAttempts();
            return;
        }

        // 只有走远到 64 格以外才会尝试走过去（3 次走不到就瞬移），平时不跟着玩家。
        if (distance > FAR_DISTANCE_SQR) {
            this.tickFarFollow(owner);
            return;
        }
        if (distance < 32.0D * 32.0D) {
            // 回到 32 格以内，重置尝试计数，下一次走远重新给 3 次机会。
            this.resetFollowAttempts();
        }

        // 玩家在附近时，偶尔抬头看两眼。
        if (this.behaviour != BEHAVIOUR_WATCH && distance < 100.0D && this.random.nextInt(200) == 0) {
            this.behaviour = BEHAVIOUR_WATCH;
            this.behaviourTicks = 40 + this.random.nextInt(60);
        }

        if (--this.behaviourTicks <= 0) {
            this.pickBehaviour(distance);
        }

        // 看向主人的眼睛再往上一点点：抬头动作能看出来，但幅度不要太大。
        if (this.behaviour == BEHAVIOUR_WATCH) {
            this.lookAt(owner.getX(), owner.getEyeY() + 0.15D, owner.getZ());
            this.zza = 0.0F;
            this.xxa = 0.0F;
        } else {
            this.tickWander();
            // 只有在站着不动的时候才四处张望，走路时朝行进方向看。
            if (this.zza == 0.0F) {
                this.tickIdleLook();
            }
        }
    }

    private void tickFarFollow(Player owner) {
        this.behaviour = BEHAVIOUR_FOLLOW;

        if (this.followCooldown > 0) {
            this.followCooldown--;
            this.zza = 0.0F;
            this.xxa = 0.0F;
            return;
        }

        if (this.followAttempts >= MAX_FOLLOW_ATTEMPTS) {
            // 三次都走不过去，直接瞬移过去。
            this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            this.resetFollowAttempts();
            return;
        }

        boolean moved = this.moveTowards(owner.position());
        if (!moved) {
            this.followStuckTicks++;
            this.zza = 0.0F;
            if (this.followStuckTicks >= 10) {
                this.followStuckTicks = 0;
                this.followTicks = 0;
                this.followAttempts++;
                this.followCooldown = FOLLOW_COOLDOWN_TICKS;
            }
            return;
        }
        this.followStuckTicks = 0;

        if (++this.followTicks >= FOLLOW_ATTEMPT_TICKS) {
            this.followTicks = 0;
            this.followAttempts++;
            this.followCooldown = FOLLOW_COOLDOWN_TICKS;
            this.zza = 0.0F;
        }
    }

    private void resetFollowAttempts() {
        this.followAttempts = 0;
        this.followTicks = 0;
        this.followCooldown = 0;
        this.followStuckTicks = 0;
    }

    private void pickBehaviour(double distanceToOwner) {
        int roll = this.random.nextInt(100);
        if (roll < 75) {
            this.behaviour = BEHAVIOUR_WANDER;
            this.behaviourTicks = 120 + this.random.nextInt(160);
            this.wanderTarget = null;
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

    private boolean moveTowards(Vec3 target) {
        double dx = target.x - this.getX();
        double dz = target.z - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.8D) {
            this.zza = 0.0F;
            return true;
        }

        // 主动避让“没有方块”的方向：往前 0.8 格的脚下 4 格都找不到实体方块就不走，
        if (!this.hasGroundAhead(dx, dz)) {
            this.zza = 0.0F;
            return false;
        }

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;

        // 走路时把头摆正
        this.setXRot((float) Mth.approach(this.getXRot(), 0.0F, 8.0F));

        this.zza = 1.0F;
        this.xxa = 0.0F;

        if (this.isInWater()) {
            this.setJumping(true);
        } else if (this.horizontalCollision && this.onGround() && this.random.nextInt(6) == 0) {
            this.setJumping(true);
        }
        return true;
    }

    private boolean hasGroundAhead(double dx, double dz) {
        // 游泳、爬梯子、鞘翅飞行时本来就不需要地面。
        if (this.isInWater() || this.isInLava() || this.onClimbable() || this.isFallFlying()) {
            return true;
        }

        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return true;
        }
        double nx = this.getX() + dx / length * 0.8D;
        double nz = this.getZ() + dz / length * 0.8D;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy >= -4; dy--) {
            pos.set(nx, this.getY() + dy, nz);
            if (!this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void tickWander() {
        if (--this.wanderCooldown <= 0 || this.wanderTarget == null) {
            this.wanderCooldown = 60 + this.random.nextInt(80);
            this.wanderTarget = this.pickWanderTarget();
        }

        if (this.wanderTarget == null) {
            // 周围没有安全的方向，就原地待着。
            this.zza = 0.0F;
            this.xxa = 0.0F;
            return;
        }

        if (this.wanderTarget.distanceToSqr(this.position()) < 1.0D) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
            return;
        }

        if (!this.moveTowards(this.wanderTarget)) {
            // 走不过去就丢掉这个目标，下一 tick 重新挑一个安全的方向。
            this.wanderTarget = null;
            this.wanderCooldown = 0;
        }
    }

    @Nullable
    private Vec3 pickWanderTarget() {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + this.random.nextDouble() * 4.0D;
            double tx = this.getX() + Math.cos(angle) * distance;
            double tz = this.getZ() + Math.sin(angle) * distance;
            if (this.hasGroundAhead(tx - this.getX(), tz - this.getZ())) {
                return new Vec3(tx, this.getY(), tz);
            }
        }
        return null;
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
