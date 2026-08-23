package com.jiuzhuan.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 九转戒通用配置
 * 配置文件路径：config/nine_turn_ring-common.toml
 * 游戏启动即生成，无需进入世界，可在不修改源码的情况下调整模组数值
 */
public class ServerConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue ROT1_DAMAGE_PER_KILL;
    public static final ForgeConfigSpec.DoubleValue ROT1_DAMAGE_CAP;

    public static final ForgeConfigSpec.DoubleValue ROT5_HEALTH_PER_KILL;
    public static final ForgeConfigSpec.DoubleValue ROT5_HEALTH_CAP;

    public static final ForgeConfigSpec.DoubleValue ROT7_HEAL_RATIO;
    public static final ForgeConfigSpec.IntValue ROT7_INVINCIBLE_SECONDS;
    public static final ForgeConfigSpec.IntValue ROT7_COOLDOWN_SECONDS;

    public static final ForgeConfigSpec.DoubleValue ROT9_MIN_HEALTH;

    public static final ForgeConfigSpec.DoubleValue ROT10_REDUCTION_PER_STACK;
    public static final ForgeConfigSpec.IntValue ROT10_MAX_STACKS;
    public static final ForgeConfigSpec.IntValue ROT10_STACK_COOLDOWN_SECONDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "You can control the balance settings of Nine Turn Ring in this config.",
                "你可以在此配置文件中调整九转戒的数值平衡设定."
        ).push("general");

        builder.push("rotation_1");
        ROT1_DAMAGE_PER_KILL = builder
                .comment(
                        "Final damage bonus percentage per kill. (default 0.05 = 5%)",
                        "每击杀一个生物增加的最终伤害百分比. (默认0.05 = 5%)",
                        "Range: 0.0 ~ 10.0"
                )
                .defineInRange("damage_per_kill", 0.05, 0.0, 10.0);
        ROT1_DAMAGE_CAP = builder
                .comment(
                        "Maximum damage bonus. (0 = no limit, 2.0 = max +200% damage)",
                        "伤害加成上限. (0 = 无上限, 2.0 = 最多+200%伤害)",
                        "Range: 0.0 ~ 100.0"
                )
                .defineInRange("damage_cap", 0.0, 0.0, 100.0);
        builder.pop();

        builder.push("rotation_5");
        ROT5_HEALTH_PER_KILL = builder
                .comment(
                        "Max health bonus percentage per kill. (default 0.05 = 5%)",
                        "每击杀一个生物增加的最大生命值百分比. (默认0.05 = 5%)",
                        "Range: 0.0 ~ 10.0"
                )
                .defineInRange("health_per_kill", 0.05, 0.0, 10.0);
        ROT5_HEALTH_CAP = builder
                .comment(
                        "Maximum health bonus. (0 = no limit, 3.0 = max +300% max health)",
                        "血量加成上限. (0 = 无上限, 3.0 = 最多+300%最大生命)",
                        "Range: 0.0 ~ 100.0"
                )
                .defineInRange("health_cap", 0.0, 0.0, 100.0);
        builder.pop();

        builder.push("rotation_7");
        ROT7_HEAL_RATIO = builder
                .comment(
                        "Percentage of max health restored on revive. (default 0.5 = 50%)",
                        "复活时恢复最大生命值的比例. (默认0.5 = 50%)",
                        "Range: 0.1 ~ 1.0"
                )
                .defineInRange("heal_ratio", 0.5, 0.1, 1.0);
        ROT7_INVINCIBLE_SECONDS = builder
                .comment(
                        "Invincibility duration after revive, in seconds.",
                        "复活后无敌时长, 单位秒.",
                        "Range: 1 ~ 600"
                )
                .defineInRange("invincible_seconds", 10, 1, 600);
        ROT7_COOLDOWN_SECONDS = builder
                .comment(
                        "Revive cooldown, in seconds.",
                        "复活冷却时长, 单位秒.",
                        "Range: 1 ~ 3600"
                )
                .defineInRange("cooldown_seconds", 40, 1, 3600);
        builder.pop();

        builder.push("rotation_9");
        ROT9_MIN_HEALTH = builder
                .comment(
                        "Minimum health the player can be reduced to. (default 1.0)",
                        "玩家生命值最低保留值. (默认1.0)",
                        "Range: 0.5 ~ 20.0"
                )
                .defineInRange("min_health", 1.0, 0.5, 20.0);
        builder.pop();

        builder.push("rotation_10");
        ROT10_REDUCTION_PER_STACK = builder
                .comment(
                        "Damage reduction percentage per adaptation stack. (default 0.1 = 10%)",
                        "每层适应提供的减伤比例. (默认0.1 = 10%)",
                        "Range: 0.01 ~ 1.0"
                )
                .defineInRange("reduction_per_stack", 0.1, 0.01, 1.0);
        ROT10_MAX_STACKS = builder
                .comment(
                        "Maximum adaptation stacks per damage type.",
                        "单种伤害类型最大适应层数.",
                        "Range: 1 ~ 100"
                )
                .defineInRange("max_stacks", 10, 1, 100);
        ROT10_STACK_COOLDOWN_SECONDS = builder
                .comment(
                        "Cooldown between stacking the same damage type, in seconds.",
                        "同类伤害叠加冷却, 单位秒.",
                        "Range: 0 ~ 60"
                )
                .defineInRange("stack_cooldown_seconds", 3, 0, 60);
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    public static double getRot1DamagePerKill() { return ROT1_DAMAGE_PER_KILL.get(); }
    public static double getRot1DamageCap() { return ROT1_DAMAGE_CAP.get(); }

    public static double getRot5HealthPerKill() { return ROT5_HEALTH_PER_KILL.get(); }
    public static double getRot5HealthCap() { return ROT5_HEALTH_CAP.get(); }

    public static double getRot7HealRatio() { return ROT7_HEAL_RATIO.get(); }
    public static int getRot7InvincibleSeconds() { return ROT7_INVINCIBLE_SECONDS.get(); }
    public static int getRot7CooldownSeconds() { return ROT7_COOLDOWN_SECONDS.get(); }

    public static double getRot9MinHealth() { return ROT9_MIN_HEALTH.get(); }

    public static double getRot10ReductionPerStack() { return ROT10_REDUCTION_PER_STACK.get(); }
    public static int getRot10MaxStacks() { return ROT10_MAX_STACKS.get(); }
    public static int getRot10StackCooldownSeconds() { return ROT10_STACK_COOLDOWN_SECONDS.get(); }
}
