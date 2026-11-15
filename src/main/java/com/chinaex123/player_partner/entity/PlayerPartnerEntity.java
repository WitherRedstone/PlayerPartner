package com.chinaex123.player_partner.entity;

import com.chinaex123.player_partner.entity.ai.EatFoodGoal;
import com.chinaex123.player_partner.inventory.PlayerPartnerEquipment;
import com.chinaex123.player_partner.inventory.PlayerPartnerMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerPartnerEntity extends PathfinderMob {
    /** 没有命名牌时使用的默认皮肤。 */
    public static final String DEFAULT_SKIN_NAME = "ChinaEX123_BILI";

    /** 饥饿值，需要同步给客户端，菜单界面里要画饥饿条。 */
    private static final EntityDataAccessor<Integer> DATA_FOOD_LEVEL =
            SynchedEntityData.defineId(PlayerPartnerEntity.class, EntityDataSerializers.INT);

    /** 储物栏的格子数量（三排，每排九个）。 */
    public static final int STORAGE_SIZE = 27;

    /** 饥饿值上限。 */
    public static final int MAX_FOOD = 20;

    /** 饱和度。 */
    private float saturation = 5.0F;
    /** 疲劳度，攒够 4 点就会消耗饱和度或饥饿值。 */
    private float exhaustion;
    /** 回血 / 饿伤的计时器。 */
    private int foodTickTimer;

    /** 储物栏，掉落物不会自动进入这里，只有玩家主动放入。 */
    private final SimpleContainer storage = new SimpleContainer(STORAGE_SIZE);

    /** 装备栏包装，直接读写实体身上的装备槽，这样客户端渲染能立刻看到变化。 */
    private final Container equipment = new PlayerPartnerEquipment(this);

    public PlayerPartnerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        // 作为玩家的伙伴，始终保留，不会因为离玩家太远而被刷掉。
        this.setPersistenceRequired();
        // 不产生经验值，避免被当成普通怪物刷经验。
        this.xpReward = 0;
        // 不会主动拾取地上的掉落物，所有物品只能由玩家放进它的储物栏。
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.STEP_HEIGHT, 0.6D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FOOD_LEVEL, MAX_FOOD);
    }

    @Override
    protected void registerGoals() {
        // 在水里时上浮，避免淹死。
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 饿了就自己找东西吃。
        this.goalSelector.addGoal(1, new EatFoodGoal(this));
        // 像玩家一样四处闲逛，会主动避开水面。
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        // 玩家靠近时转头看着玩家。
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        // 偶尔随机看看四周。
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.tickHunger();
        }
    }

    public int getFoodLevel() {
        return this.entityData.get(DATA_FOOD_LEVEL);
    }

    public void setFoodLevel(int foodLevel) {
        this.entityData.set(DATA_FOOD_LEVEL, net.minecraft.util.Mth.clamp(foodLevel, 0, MAX_FOOD));
    }

    public void addFood(int nutrition, float saturationModifier) {
        this.setFoodLevel(this.getFoodLevel() + nutrition);
        this.saturation = Math.min(
                this.saturation + nutrition * saturationModifier * 2.0F,
                this.getFoodLevel());
    }

    public void addExhaustion(float amount) {
        this.exhaustion = Math.min(this.exhaustion + amount, 40.0F);
    }

    private void tickHunger() {
        // 移动消耗：疾跑比走路快得多。
        if (this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D) {
            this.exhaustion += this.isSprinting() ? 0.1F : 0.01F;
        }

        if (this.getFoodLevel() >= 18 && this.getHealth() < this.getMaxHealth()) {
            // 吃饱时缓慢回血，每次消耗 6 点疲劳度。
            if (++this.foodTickTimer >= 80) {
                this.foodTickTimer = 0;
                this.heal(1.0F);
                this.addExhaustion(6.0F);
            }
        } else if (this.getFoodLevel() <= 0) {
            // 饿到 0 会掉血，但不会饿死。
            if (++this.foodTickTimer >= 80) {
                this.foodTickTimer = 0;
                if (this.getHealth() > 1.0F) {
                    this.hurt(this.damageSources().starve(), 1.0F);
                }
            }
        } else {
            this.foodTickTimer = 0;
        }

        // 疲劳度攒够 4 点：先掉饱和度，再掉饥饿值。
        if (this.exhaustion >= 4.0F) {
            this.exhaustion -= 4.0F;
            if (this.saturation > 0.0F) {
                this.saturation = Math.max(this.saturation - 1.0F, 0.0F);
            } else {
                this.setFoodLevel(this.getFoodLevel() - 1);
            }
        }
    }

    @Override
    public @NotNull ItemStack eat(Level level, ItemStack food, FoodProperties foodProperties) {
        if (!level.isClientSide) {
            this.addFood(foodProperties.nutrition(), foodProperties.saturation());
        }
        return super.eat(level, food, foodProperties);
    }

    public boolean hasFood() {
        return this.findBestFoodSlot() >= 0;
    }

    public int findBestFoodSlot() {
        int bestSafeSlot = -1;
        int bestSafeNutrition = -1;
        int bestAnySlot = -1;
        int bestAnyNutrition = -1;

        for (int i = 0; i < this.storage.getContainerSize(); i++) {
            ItemStack stack = this.storage.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            FoodProperties food = stack.getFoodProperties(this);
            if (food == null) {
                continue;
            }

            if (food.nutrition() > bestAnyNutrition) {
                bestAnySlot = i;
                bestAnyNutrition = food.nutrition();
            }
            if (!isHarmful(food) && food.nutrition() > bestSafeNutrition) {
                bestSafeSlot = i;
                bestSafeNutrition = food.nutrition();
            }
        }

        return bestSafeSlot >= 0 ? bestSafeSlot : bestAnySlot;
    }

    private static boolean isHarmful(FoodProperties food) {
        for (FoodProperties.PossibleEffect possibleEffect : food.effects()) {
            if (!possibleEffect.effect().getEffect().value().isBeneficial()) {
                return true;
            }
        }
        return false;
    }

    public void startEating() {
        int slot = this.findBestFoodSlot();
        if (slot < 0) {
            return;
        }

        ItemStack food = this.storage.getItem(slot);
        ItemStack offhand = this.getItemBySlot(EquipmentSlot.OFFHAND);

        // 先把食物拿出来，副手原本拿着的东西放回刚空出来的格子。
        this.storage.setItem(slot, ItemStack.EMPTY);
        if (!offhand.isEmpty()) {
            this.storage.setItem(slot, offhand);
        }

        this.setItemSlot(EquipmentSlot.OFFHAND, food);
        this.startUsingItem(InteractionHand.OFF_HAND);
    }

    public Container getStorage() {
        return this.storage;
    }

    public Container getEquipment() {
        return this.equipment;
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!this.level().isClientSide) {
                player.openMenu(new SimpleMenuProvider(
                                (containerId, inventory, p) -> new PlayerPartnerMenu(containerId, inventory, this),
                                Component.translatable("container.player_partner.inventory")),
                        buffer -> buffer.writeVarInt(this.getId()));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FoodLevel", this.getFoodLevel());
        tag.putFloat("Saturation", this.saturation);
        tag.putFloat("Exhaustion", this.exhaustion);
        tag.put("Storage", this.storage.createTag(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FoodLevel")) {
            this.setFoodLevel(tag.getInt("FoodLevel"));
        }
        this.saturation = tag.getFloat("Saturation");
        this.exhaustion = tag.getFloat("Exhaustion");
        if (tag.contains("Storage", Tag.TAG_LIST)) {
            this.storage.fromTag(tag.getList("Storage", Tag.TAG_COMPOUND), this.registryAccess());
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        Containers.dropContents(level, this, this.storage);
    }

    public String getSkinName() {
        Component customName = this.getCustomName();
        if (customName != null) {
            String name = customName.getString().trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return DEFAULT_SKIN_NAME;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PLAYER_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 0.6F;
    }
}
