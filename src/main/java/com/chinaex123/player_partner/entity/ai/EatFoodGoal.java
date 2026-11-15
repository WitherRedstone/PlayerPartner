package com.chinaex123.player_partner.entity.ai;

import com.chinaex123.player_partner.entity.PlayerPartnerEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

public class EatFoodGoal extends Goal {
    /** 两次进食之间的间隔（tick），避免吃完立刻又摸索。 */
    private static final int COOLDOWN_TICKS = 40;
    /** 单次进食最长等待时间（tick），防止状态卡住。 */
    private static final int MAX_EATING_TICKS = 400;

    private final PlayerPartnerEntity partner;

    private int cooldown;
    private int eatingTicks;

    public EatFoodGoal(PlayerPartnerEntity partner) {
        this.partner = partner;
        // 占用移动目标，进食时不会被闲逛目标打断。
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.partner.getFoodLevel() >= PlayerPartnerEntity.MAX_FOOD) {
            return false;
        }
        if (this.partner.isUsingItem()) {
            return false;
        }
        if (this.partner.getTarget() != null) {
            return false;
        }
        return this.partner.hasFood();
    }

    @Override
    public void start() {
        this.eatingTicks = 0;
        this.partner.startEating();
    }

    @Override
    public boolean canContinueToUse() {
        return this.partner.isUsingItem() && this.eatingTicks < MAX_EATING_TICKS;
    }

    @Override
    public void tick() {
        this.eatingTicks++;
        // 吃东西时站在原地不动。
        this.partner.getNavigation().stop();
    }

    @Override
    public void stop() {
        if (this.partner.isUsingItem()) {
            this.partner.stopUsingItem();
        }
        this.cooldown = COOLDOWN_TICKS;
    }
}
