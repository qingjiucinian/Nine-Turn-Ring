package com.jiuzhuan.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public interface IPlayerData {
    // 戒指是否装备
    boolean isRingEquipped();
    void setRingEquipped(boolean equipped);

    // 十转激活状态 (1-10)
    boolean isActivated(int rotation);
    void setActivated(int rotation, boolean activated);

    // 1转：击杀数（力量成长）
    int getPowerKillCount();
    void addPowerKill(int count);
    double getPowerDamageBonus(); // 5% per kill

    // 5转：击杀数（血量成长）
    int getHealthKillCount();
    void addHealthKill(int count);
    double getHealthBonus(); // 5% per kill

    // 7转：不死
    long getUndyingCooldownEnd();
    void setUndyingCooldownEnd(long time);
    long getInvincibleEnd();
    void setInvincibleEnd(long time);
    boolean isInCooldown(long now);
    boolean isInvincible(long now);

    // 10转：适应
    int getAdaptationLevel(String damageType);
    void addAdaptation(String damageType, long now);
    double getAdaptationReduction(String damageType);
    Map<String, Integer> getAllAdaptationLevels();
    Map<String, Long> getAllAdaptationTimes();
    // 已完成（满级10层）的适应类型数量
    int getCompletedAdaptationCount();
    // 某伤害类型是否已满级适应（10层）
    boolean hasFullAdaptation(String damageType);
    // 是否满足飞行适应条件（完成3种伤害类型适应）
    boolean hasFlightAdaptation();
    // 伤害类型适应禁用列表（右键关闭后不再继续适应）
    boolean isDamageAdaptationDisabled(String damageType);
    void setDamageAdaptationDisabled(String damageType, boolean disabled);
    java.util.Set<String> getDisabledDamageTypes();
    // ===== 十转：负面效果适应 =====
    // 某负面效果的适应层数（0-10）
    int getEffectAdaptationLevel(String effectId);
    // 尝试叠加一层负面效果适应（受3秒CD限制）
    void addEffectAdaptation(String effectId, long now);
    // 某负面效果的适应减益比例（0.0-1.0，每层0.1）
    double getEffectAdaptationReduction(String effectId);
    // 获取所有负面效果适应层数
    Map<String, Integer> getAllEffectAdaptationLevels();
    // 获取所有负面效果上次叠加时间戳
    Map<String, Long> getAllEffectAdaptationTimes();
    // 某负面效果是否已满级适应（10层，完全免疫）
    boolean hasFullEffectAdaptation(String effectId);
    // 某负面效果当前持续暴露的tick数
    int getEffectExposureTicks(String effectId);
    // 增加某负面效果的持续暴露tick数
    void addEffectExposureTicks(String effectId, int ticks);
    // 重置某负面效果的持续暴露计数（叠加一层后调用）
    void resetEffectExposureTicks(String effectId);
    // 负面效果适应禁用列表（右键关闭后不再继续适应）
    boolean isEffectAdaptationDisabled(String effectId);
    void setEffectAdaptationDisabled(String effectId, boolean disabled);
    java.util.Set<String> getDisabledEffectTypes();
    // 是否已播放过"我适应了大地"提示（避免重复）
    boolean hasAnnouncedFlightAdaptation();
    void setAnnouncedFlightAdaptation(boolean value);

    // 模组是否授予了飞行能力（用于区分模组飞行与其他模组飞行，避免冲突）
    boolean isFlightGrantedByMod();
    void setFlightGrantedByMod(boolean value);

    // 首箱标记
    boolean hasOpenedFirstChest();
    void setOpenedFirstChest(boolean value);

    // ===== 自然获得计数 =====
    // 二转：饥饿死亡计数
    int getHungerDeathCount();
    void addHungerDeath();
    boolean hasNatallyGotRot2();
    void setNatallyGotRot2(boolean value);

    // 三转：黑暗/低亮度死亡计数
    int getDarkDeathCount();
    void addDarkDeath();
    boolean hasNatallyGotRot3();
    void setNatallyGotRot3(boolean value);
    // 三转：最后一次受伤时是否处于黑暗/低亮度
    boolean isLastDamageInDark();
    void setLastDamageInDark(boolean value);
    // 三转：夜视手动开关状态
    boolean isNightVisionEnabled();
    void setNightVisionEnabled(boolean enabled);

    // 四转：中毒/凋灵死亡计数
    int getPoisonDeathCount();
    void addPoisonDeath();
    boolean hasNatallyGotRot4();
    void setNatallyGotRot4(boolean value);

    // 五转：是否已击杀凋灵
    boolean hasKilledWither();
    void setKilledWither(boolean value);
    // 九转：是否已击杀末影龙
    boolean hasKilledEnderDragon();
    void setKilledEnderDragon(boolean value);

    // 六转：魔法/虚空/真实伤害死亡计数
    int getMagicDeathCount();
    void addMagicDeath();
    boolean hasNatallyGotRot6();
    void setNatallyGotRot6(boolean value);

    // 七转：佩戴不死图腾死亡计数
    int getTotemDeathCount();
    void addTotemDeath();
    boolean hasTotemDeathThisCycle();
    void setTotemDeathThisCycle(boolean value);
    boolean hasNatallyGotRot7();
    void setNatallyGotRot7(boolean value);
    // 七转：图腾效果标记（触发后5秒内死亡才算）
    boolean isTotemEffectActive();
    void setTotemEffectActive(boolean value);
    long getTotemEffectEndTime();
    void setTotemEffectEndTime(long time);
    // 是否已播放过图腾祝福提示（避免重复）
    boolean hasAnnouncedTotemBlessing();
    void setAnnouncedTotemBlessing(boolean value);

    // 八转：钓鱼垃圾计数
    int getJunkFishCount();
    void addJunkFish();
    boolean hasNatallyGotRot8();
    void setNatallyGotRot8(boolean value);

    // 九转：连续不死图腾触发计数（间隔5s内）
    int getConsecutiveTotemCount();
    void setConsecutiveTotemCount(int count);
    long getLastTotemTriggerTime();
    void setLastTotemTriggerTime(long time);
    int getTotemChainDeathCount(); // 满足连续条件后的死亡计数
    void addTotemChainDeath();
    boolean hasNatallyGotRot9();
    void setNatallyGotRot9(boolean value);

    // 十转：是否已自然获得
    boolean hasNatallyGotRot10();
    void setNatallyGotRot10(boolean value);

    // 进度授予标记（避免重复）
    boolean hasAdvancement(String key);
    void setAdvancement(String key, boolean value);

    // 维度切换修复延迟（临时字段，不持久化）：维度切换后等几tick再重建轮转槽和同步
    int getDimensionFixDelay();
    void setDimensionFixDelay(int ticks);

    // ===== 饰品防收取快照 =====
    java.util.Map<String, net.minecraft.nbt.CompoundTag> getAccessorySnapshot();
    void setAccessorySnapshot(java.util.Map<String, net.minecraft.nbt.CompoundTag> snapshot);
    void putAccessory(String slotIdentifier, net.minecraft.nbt.CompoundTag itemTag);
    void clearAccessorySnapshot();

    // 同步到客户端
    void syncToClient(Player player);

    // NBT
    CompoundTag saveNBT();
    void loadNBT(CompoundTag tag);
}
