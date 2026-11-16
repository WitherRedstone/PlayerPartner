package com.chinaex123.player_partner.entity;

import org.jetbrains.annotations.Nullable;

public enum PartnerJob {
    /** 没有指派任务：像普通生物一样自己闲逛。 */
    NONE("none"),
    /** 战斗：跟着主人打敌对生物。 */
    COMBAT("combat"),
    /** 挖矿：挖附近的矿石类方块。 */
    MINING("mining"),
    /** 砍树：砍附近的原木。 */
    CHOPPING("chopping"),
    /** 耕种：锄地，并在有种子时种下去。 */
    FARMING("farming");

    private final String id;

    PartnerJob(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String translationKey() {
        return "job.player_partner." + this.id;
    }

    public static PartnerJob byId(int ordinal) {
        PartnerJob[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return NONE;
        }
        return values[ordinal];
    }

    public static PartnerJob byName(@Nullable String name) {
        if (name != null) {
            for (PartnerJob job : values()) {
                if (job.id.equals(name)) {
                    return job;
                }
            }
        }
        return NONE;
    }
}
