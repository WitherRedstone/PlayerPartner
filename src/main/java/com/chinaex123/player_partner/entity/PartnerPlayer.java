package com.chinaex123.player_partner.entity;

import com.mojang.authlib.GameProfile;
import com.chinaex123.player_partner.client.menu.PlayerPartnerMenu;
import com.chinaex123.player_partner.config.PPConfig;
import com.chinaex123.player_partner.world.MiningRules;
import com.chinaex123.player_partner.world.PartnerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class PartnerPlayer extends ServerPlayer {
    public static final String MARKER = "player_partner";
    /** 受击后“僵直”的 tick 数：这段时间不接管移动，让原版击退把人推出去。 */
    private static final int STAGGER_TICKS = 10;

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
    /** 举盾防守还剩多少 tick，每次受到伤害都会重新计时。 */
    private int guardTicks;
    /** 举盾期间连续没有威胁的 tick 数。 */
    private int guardIdleTicks;
    /** 举盾时被换下来的副手物品（一般是食物），防守结束后放回副手。 */
    private ItemStack stashedOffhand = ItemStack.EMPTY;
    /** 盾牌原来所在的背包槽位；-1 表示没有从背包里换过盾牌。 */
    private int shieldSlot = -1;
    /** 受击后的僵直 tick 数：这段时间不接管移动，让击退真的把人推出去。 */
    private int staggerTicks;
    /** 上一 tick 的位置，用来给假玩家补算原版的移动疲劳。 */
    private double lastMoveX;
    private double lastMoveY;
    private double lastMoveZ;
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

    /** 受到伤害后举盾防守 5 秒，期间再被打就重新计时。 */
    private static final int GUARD_TICKS = 100;
    /** 连续 2 秒没有威胁就提前收盾。 */
    private static final int GUARD_IDLE_TICKS = 40;

    private PartnerJob job = PartnerJob.NONE;

    /** 战斗任务当前盯上的目标。 */
    @Nullable
    private LivingEntity jobTarget;
    /** 挖矿 / 砍树 / 耕种当前盯着的方块。 */
    @Nullable
    private BlockPos jobPos;
    /** 当前方块已经挖了多少 tick。 */
    private int jobBreakTicks;
    /** 搜索方块目标的冷却，避免每 tick 都扫一大圈。 */
    private int jobScanCooldown;
    /** 攻击冷却，避免每 tick 都挥一下。 */
    private int attackCooldown;

    public PartnerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation information) {
        super(server, level, profile, information);
        this.getAttribute(Attributes.SCALE).setBaseValue(0.5D);
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(PPConfig.PARTNER_MAX_HEALTH.get());
        }
        this.setHealth(this.getMaxHealth());

        this.refreshDimensions();
    }

    public static boolean isPartner(GameProfile profile) {
        return profile.getProperties().containsKey(MARKER);
    }

    public static UUID createUuid(String displayName) {
        return UUIDUtil.createOfflinePlayerUUID("PlayerPartner:" + displayName);
    }

    /** 伙伴当前在做什么。 */
    public PartnerJob getJob() {
        return this.job;
    }

    /** 给伙伴换个任务；换任务时清掉之前盯着的目标和方块，并把任务写进存档。 */
    public void setJob(PartnerJob job) {
        this.job = job;
        this.jobTarget = null;
        this.jobPos = null;
        this.jobBreakTicks = 0;
        this.jobScanCooldown = 0;
        if (!this.level().isClientSide) {
            PartnerManager.get(this.server).persistJob(this);
        }
    }

    @Override
    public @NotNull InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide || !this.isAlive()) {
            return InteractionResult.PASS;
        }

        // 潜行右键：打开伙伴的物品栏。
        if (player.isSecondaryUseActive()) {
            player.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, owner) -> new PlayerPartnerMenu(containerId, inventory, this),
                            this.getDisplayNameForMenu()),
                    buffer -> buffer.writeUUID(this.getUUID()));
            return InteractionResult.sidedSuccess(false);
        }

        // 用命名牌改名（同时换皮肤）。
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            ItemStack stack = player.getMainHandItem();
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (stack.is(Items.NAME_TAG) && customName != null) {
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

        if (!this.level().isClientSide) {
            double dx = this.getX() - this.lastMoveX;
            double dz = this.getZ() - this.lastMoveZ;
            this.lastMoveX = this.getX();
            this.lastMoveY = this.getY();
            this.lastMoveZ = this.getZ();
            if (this.tickCount > 1 && !this.isPassenger()) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 1.0E-4D) {
                    this.causeFoodExhaustion((float) (distance * 0.1D));
                }
                if (this.jumping && this.onGround()) {
                    this.causeFoodExhaustion(0.1F);
                }
            }

            if (this.tickCount % 10 == 0) {
                this.connection.resetPosition();
                this.serverLevel().getChunkSource().move(this);
            }
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

    public void onMenuOpened() {
        this.menuViewers++;
    }

    public void onMenuClosed() {
        this.menuViewers = Math.max(0, this.menuViewers - 1);
    }

    public boolean isMenuOpen() {
        return this.menuViewers > 0;
    }

    public Component getDisplayNameForMenu() {
        return this.getName();
    }

    private void tickBehaviour() {
        if (this.removing || !this.isAlive()) {
            return;
        }

        this.setJumping(false);

        // 被打了，这一小段时间完全不接管移动，让击退真的把人推出去。
        if (this.staggerTicks > 0) {
            this.staggerTicks--;
            this.zza = 0.0F;
            this.xxa = 0.0F;
            return;
        }

        // 有人正开着伙伴的物品栏，站着别动，看着界面就行，等界面关掉再继续活动。
        if (this.isMenuOpen()) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
            return;
        }

        this.tickShield();
        this.tickEating();

        if (this.isUsingItem()) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
            this.setJumping(false);
            return;
        }

        // 有任务时先干活：工作会各自接管移动，干完了才回到闲逛。
        PartnerJob job = this.getJob();
        if (job != PartnerJob.NONE) {
            this.tickJob(job);
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

    /** 按当前任务分派，每个任务自己负责移动和交互。 */
    private void tickJob(PartnerJob job) {
        switch (job) {
            case COMBAT -> this.tickCombatJob();
            case MINING -> this.tickMiningJob();
            case CHOPPING -> this.tickChoppingJob();
            case FARMING -> this.tickFarmingJob();
            default -> {
            }
        }
    }

    /** 站住不动，但像闲逛时一样四处张望。 */
    private void idleForJob() {
        this.zza = 0.0F;
        this.xxa = 0.0F;
        this.tickIdleLook();
    }

    private void tickCombatJob() {
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        // 每半秒重新挑一次目标；目标死了或者跑没影了也重新挑。
        if (this.tickCount % 10 == 0 || this.jobTarget == null || !this.jobTarget.isAlive()) {
            this.jobTarget = this.pickCombatTarget();
        }

        LivingEntity target = this.jobTarget;
        if (target == null) {
            this.idleForJob();
            return;
        }

        this.lookAt(target.getX(), target.getEyeY(), target.getZ());

        // 伙伴只有一半体型，够得着的距离也短一点。
        if (this.distanceToSqr(target) > 2.5D * 2.5D) {
            this.moveTowards(target.position());
            return;
        }

        this.zza = 0.0F;
        this.xxa = 0.0F;
        if (this.attackCooldown <= 0) {
            this.swing(InteractionHand.MAIN_HAND);
            this.attack(target);
            this.resetAttackStrengthTicker();
            this.attackCooldown = 20;
        }
    }

    /** 优先打主人正在打的怪，其次打附近盯上主人或自己的怪。 */
    @Nullable
    private LivingEntity pickCombatTarget() {
        Player owner = this.getOwner();
        if (owner != null && owner.level() == this.level()) {
            LivingEntity hurt = owner.getLastHurtMob();
            if (hurt instanceof Monster monster && this.canFight(monster)) {
                return monster;
            }
        }

        int radius = PPConfig.JOB_RADIUS.get();
        AABB area = this.getBoundingBox().inflate(radius, 4.0D, radius);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Monster monster : this.level().getEntitiesOfClass(Monster.class, area, this::canFight)) {
            double distance = monster.distanceToSqr(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = monster;
            }
        }
        return best;
    }

    private boolean canFight(Monster monster) {
        return monster.isAlive()
                && !monster.isRemoved()
                && !monster.isInvulnerable()
                && monster.distanceToSqr(this) <= (double) PPConfig.JOB_RADIUS.get() * PPConfig.JOB_RADIUS.get() * 2.0D;
    }

    private void tickMiningJob() {
        this.tickBreakingJob(pos -> this.isMiningBlock(this.level().getBlockState(pos)));
    }

    private void tickChoppingJob() {
        this.tickBreakingJob(pos -> this.level().getBlockState(pos).is(BlockTags.LOGS));
    }

    /** 挖矿和砍树共用：找方块 → 走过去 → 一点点挖掉。 */
    private void tickBreakingJob(Predicate<BlockPos> filter) {
        if (this.jobPos != null && !filter.test(this.jobPos)) {
            this.clearBreakProgress(this.jobPos);
            this.jobPos = null;
            this.jobBreakTicks = 0;
        }

        if (this.jobPos == null) {
            if (this.jobScanCooldown > 0) {
                this.jobScanCooldown--;
                this.idleForJob();
                return;
            }
            this.jobPos = this.findBlockInRange(filter);
            this.jobScanCooldown = 20;
            this.jobBreakTicks = 0;
            if (this.jobPos == null) {
                this.idleForJob();
                return;
            }
        }

        BlockPos pos = this.jobPos;
        Vec3 center = Vec3.atCenterOf(pos);
        if (this.distanceToSqr(center) > 9.0D) {
            this.moveTowards(center);
            return;
        }

        this.zza = 0.0F;
        this.xxa = 0.0F;
        this.lookAt(center.x, center.y, center.z);

        BlockState state = this.level().getBlockState(pos);
        float hardness = state.getDestroySpeed(this.level(), pos);
        if (hardness < 0.0F) {
            // 基岩这类挖不动的方块，直接放弃。
            this.clearBreakProgress(pos);
            this.jobPos = null;
            this.jobBreakTicks = 0;
            return;
        }

        ItemStack tool = this.getMainHandItem();
        float speed = Math.max(1.0F, tool.getDestroySpeed(state));
        int requiredTicks = Math.max(6, (int) (hardness * 30.0F / speed));
        if (++this.jobBreakTicks < requiredTicks) {
            // 每 6 tick 挥一下手，看起来就是在一下一下地敲方块（原版玩家每次左键都会挥手）。
            if (this.jobBreakTicks % 6 == 1) {
                this.swing(InteractionHand.MAIN_HAND);
            }
            // 同步挖掘裂纹：0~9 是原版的十个破坏阶段，客户端拿它画方块上的裂纹。
            int progress = (int) (9.0F * (float) this.jobBreakTicks / (float) requiredTicks);
            this.serverLevel().destroyBlockProgress(this.getId(), pos, progress);
            return;
        }

        this.breakBlockAndCollect(pos, state);
        this.jobPos = null;
        this.jobBreakTicks = 0;
        this.jobScanCooldown = 10;
    }

    /** 挖掉方块并把掉落物塞进伙伴背包，塞不下的掉在地上。 */
    private void breakBlockAndCollect(BlockPos pos, BlockState state) {
        ServerLevel level = this.serverLevel();
        ItemStack tool = this.getMainHandItem();
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this, tool);
        tool.mineBlock(level, state, pos, this);
        level.destroyBlock(pos, false);
        this.clearBreakProgress(pos);
        for (ItemStack drop : drops) {
            if (!this.getInventory().add(drop)) {
                this.drop(drop, false);
            }
        }
    }

    /** 清掉某个方块上的挖掘裂纹动画，-1 表示“这个实体不再挖这里”。 */
    private void clearBreakProgress(@Nullable BlockPos pos) {
        if (pos != null) {
            this.serverLevel().destroyBlockProgress(this.getId(), pos, -1);
        }
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return false;
    }

    /** 挖矿规则（白名单 / 黑名单）存在存档里，可以用指令随时改、下一 tick 就生效。 */
    private boolean isMiningBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return MiningRules.get(this.server).matches(state);
    }

    private void tickFarmingJob() {
        // 有种子就先种，没有种子再锄地。
        if (this.tryPlantSeed()) {
            return;
        }

        if (!(this.getMainHandItem().getItem() instanceof HoeItem)) {
            this.idleForJob();
            return;
        }

        if (this.jobPos == null || !this.isTillable(this.jobPos)) {
            if (this.jobScanCooldown > 0) {
                this.jobScanCooldown--;
                this.idleForJob();
                return;
            }
            this.jobPos = this.findBlockInRange(this::isTillable);
            this.jobScanCooldown = 20;
            if (this.jobPos == null) {
                this.idleForJob();
                return;
            }
        }

        BlockPos pos = this.jobPos;
        Vec3 center = Vec3.atCenterOf(pos);
        if (this.distanceToSqr(center) > 9.0D) {
            this.moveTowards(center);
            return;
        }

        this.zza = 0.0F;
        this.xxa = 0.0F;
        this.lookAt(center.x, center.y, center.z);
        if (this.isTillable(pos)) {
            // 锄地也挥一下手，并像原版锄头一样掉 1 点耐久。
            this.swing(InteractionHand.MAIN_HAND);
            this.getMainHandItem().hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
            this.serverLevel().setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
            this.serverLevel().levelEvent(2001, pos, Block.getId(Blocks.FARMLAND.defaultBlockState()));
        }
        this.jobPos = null;
        this.jobScanCooldown = 10;
    }

    private boolean isTillable(BlockPos pos) {
        BlockState state = this.level().getBlockState(pos);
        boolean soil = state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT);
        return soil && this.level().getBlockState(pos.above()).isAir();
    }

    /** 背包里有作物种子就找一块空耕地种下去。 */
    private boolean tryPlantSeed() {
        Inventory inventory = this.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof CropBlock crop)) {
                continue;
            }
            BlockPos farmland = this.findBlockInRange(pos -> this.level().getBlockState(pos).is(Blocks.FARMLAND)
                    && this.level().getBlockState(pos.above()).isAir());
            if (farmland == null) {
                return false;
            }
            Vec3 center = Vec3.atCenterOf(farmland);
            if (this.distanceToSqr(center) > 9.0D) {
                this.moveTowards(center);
                return true;
            }
            this.zza = 0.0F;
            this.xxa = 0.0F;
            this.lookAt(center.x, center.y + 0.5D, center.z);
            // 种地也挥一下手。
            this.swing(InteractionHand.MAIN_HAND);
            this.serverLevel().setBlockAndUpdate(farmland.above(), crop.defaultBlockState());
            stack.shrink(1);
            return true;
        }
        return false;
    }

    /** 在以自己为中心、上下各 3 格、半径来自配置的范围内找最近的满足条件的方块。 */
    @Nullable
    private BlockPos findBlockInRange(Predicate<BlockPos> predicate) {
        int radius = PPConfig.JOB_RADIUS.get();
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!this.level().isLoaded(cursor) || !predicate.test(cursor)) {
                        continue;
                    }
                    double distance = this.distanceToSqr(Vec3.atCenterOf(cursor));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            return super.hurt(source, amount);
        }

        // 被打之后先“僵直”半秒，别立刻又往目标走。
        this.staggerTicks = STAGGER_TICKS;
        this.beginGuard();
        boolean hurt = super.hurt(source, amount);

        if (hurt) {
            Entity attacker = source.getEntity();
            if (attacker != null && !source.is(DamageTypeTags.NO_KNOCKBACK)) {
                double dx = this.getX() - attacker.getX();
                double dz = this.getZ() - attacker.getZ();
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length > 1.0E-4D) {
                    dx /= length;
                    dz /= length;
                } else {
                    dx = 0.0D;
                    dz = 1.0D;
                }
                // 假玩家没有客户端，原版击退的位移常常被 AI 的行走输入吃掉，
                // 这里显式再补一次水平冲量，并打上 hurtMarked 让服务器把速度包广播出去。
                this.setDeltaMovement(dx * 0.35D, Math.max(this.getDeltaMovement().y, 0.35D), dz * 0.35D);
                this.hurtMarked = true;
            }
        }

        return hurt;
    }

    private void beginGuard() {
        this.guardTicks = GUARD_TICKS;
        this.guardIdleTicks = 0;

        // 副手本来就拿着盾牌（玩家自己放的），直接举起来。
        if (this.getOffhandItem().getItem() instanceof ShieldItem) {
            if (!this.isUsingItem()) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }
            return;
        }
        // 已经从背包里换过一次盾牌，还在防守中，不用再换。
        if (this.shieldSlot >= 0) {
            return;
        }

        Inventory inventory = this.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            if (!(inventory.getItem(slot).getItem() instanceof ShieldItem)) {
                continue;
            }
            // 副手一般是食物，先存起来，防守结束后放回去。
            this.stashedOffhand = inventory.getItem(Inventory.SLOT_OFFHAND).copy();
            ItemStack shield = inventory.getItem(slot).copy();
            inventory.setItem(slot, this.stashedOffhand.copy());
            inventory.setItem(Inventory.SLOT_OFFHAND, shield);
            inventory.setChanged();
            this.shieldSlot = slot;
            this.stopUsingItem();
            this.startUsingItem(InteractionHand.OFF_HAND);
            return;
        }
    }

    /** 举盾期间的维护：面朝威胁、盾牌被打断就重新举、威胁消失后收盾。 */
    private void tickShield() {
        if (this.guardTicks <= 0) {
            return;
        }

        this.guardTicks--;
        if (this.getOffhandItem().getItem() instanceof ShieldItem) {
            if (!this.isUsingItem()) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }
            LivingEntity threat = this.findThreat();
            if (threat != null) {
                // 盾牌只挡“正面”的伤害，所以举盾时要面朝威胁方向。
                this.lookAt(threat.getX(), threat.getEyeY(), threat.getZ());
                this.guardIdleTicks = 0;
            } else if (++this.guardIdleTicks > GUARD_IDLE_TICKS) {
                // 2 秒没有威胁就提前收盾，不用一直举到时间结束。
                this.guardTicks = 0;
            }
        }

        if (this.guardTicks <= 0) {
            this.endGuard();
        }
    }

    private void endGuard() {
        this.stopUsingItem();
        this.guardIdleTicks = 0;
        if (this.shieldSlot < 0) {
            return;
        }

        Inventory inventory = this.getInventory();
        ItemStack shield = inventory.getItem(Inventory.SLOT_OFFHAND);
        ItemStack food = this.stashedOffhand;
        inventory.setItem(Inventory.SLOT_OFFHAND, food.copy());

        ItemStack current = inventory.getItem(this.shieldSlot);
        if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, food)) {
            inventory.setItem(this.shieldSlot, shield.copy());
        } else {
            // 防守期间玩家动过这个槽位，盾牌就塞回背包空位，塞不下丢在脚边。
            if (!inventory.add(shield.copy())) {
                this.drop(shield.copy(), false);
            }
        }

        inventory.setChanged();
        this.stashedOffhand = ItemStack.EMPTY;
        this.shieldSlot = -1;
    }

    @Nullable
    private LivingEntity findThreat() {
        Player owner = this.getOwner();
        AABB area = this.getBoundingBox().inflate(6.0D, 3.0D, 6.0D);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            LivingEntity target = mob.getTarget();
            if (target != this && target != owner) {
                continue;
            }
            double distance = mob.distanceToSqr(this);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = mob;
            }
        }
        return nearest;
    }

    private void tickEating() {
        if (this.isUsingItem()) {
            return;
        }
        // 副手拿着盾牌时不要拿盾牌当食物槽，避免把盾牌换走。
        if (this.getOffhandItem().getItem() instanceof ShieldItem) {
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
