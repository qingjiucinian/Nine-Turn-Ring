package com.jiuzhuan.capability;

import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.network.NetworkHandler;
import com.jiuzhuan.network.SyncPlayerDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlayerData implements IPlayerData {
    private boolean ringEquipped = false;
    private final boolean[] activated = new boolean[11]; // index 1-10

    private int powerKillCount = 0;
    private int healthKillCount = 0;

    private long undyingCooldownEnd = 0;
    private long invincibleEnd = 0;

    private final Map<String, Integer> adaptationLevels = new HashMap<>();
    private final Map<String, Long> adaptationTimes = new HashMap<>();

    private boolean openedFirstChest = false;

    private static final double ADAPTATION_PER_LEVEL = 0.10;
    private static final int ADAPTATION_MAX_LEVEL = 10;
    private static final long ADAPTATION_CD_MS = 3000; // 3秒适应一层
    private static final double KILL_BONUS_PER_KILL = 0.05; // 5% per kill
    // ===== 十转：负面效果适应 =====
    private final Map<String, Integer> effectAdaptationLevels = new HashMap<>();
    private final Map<String, Long> effectAdaptationTimes = new HashMap<>();
    private final Map<String, Integer> effectExposureTicks = new HashMap<>();
    private static final int EFFECT_ADAPTATION_MAX_LEVEL = 5;
    private static final long EFFECT_ADAPTATION_CD_MS = 3000; // 3秒叠加一层
    private static final int EFFECT_EXPOSURE_THRESHOLD = 100; // 持续5秒(100tick)叠加一层
    // ===== 饰品防收取：已装备的九转戒/轮转物品快照（用于被强制摘取后自动恢复） =====
    private final java.util.Map<String, net.minecraft.nbt.CompoundTag> accessorySnapshot = new java.util.HashMap<>();

    // ===== 自然获得计数 =====
    private int hungerDeathCount = 0;
    private boolean natallyGotRot2 = false;
    private int darkDeathCount = 0;
    private boolean natallyGotRot3 = false;
    private boolean lastDamageInDark = false;
    private boolean nightVisionEnabled = true; // 三转夜视手动开关，默认开启
    private int poisonDeathCount = 0;
    private boolean natallyGotRot4 = false;
    private boolean killedWither = false;
    private boolean killedEnderDragon = false;
    private int magicDeathCount = 0;
    private boolean natallyGotRot6 = false;
    private int totemDeathCount = 0;
    private boolean totemDeathThisCycle = false;
    private boolean natallyGotRot7 = false;
    private boolean totemEffectActive = false;
    private long totemEffectEndTime = 0;
    private int junkFishCount = 0;
    private boolean natallyGotRot8 = false;
    private int consecutiveTotemCount = 0;
    private long lastTotemTriggerTime = 0;
    private int totemChainDeathCount = 0;
    private boolean natallyGotRot9 = false;
    private boolean natallyGotRot10 = false;
    private boolean announcedFlightAdaptation = false;
    private boolean flightGrantedByMod = false;
    private final Set<String> grantedAdvancements = new HashSet<>();
    // 维度切换修复延迟（临时字段，不持久化）
    private transient int dimensionFixDelay = 0;

    @Override public boolean isRingEquipped() { return ringEquipped; }
    @Override public void setRingEquipped(boolean equipped) { this.ringEquipped = equipped; }

    @Override
    public boolean isActivated(int rotation) {
        if (rotation < 1 || rotation > 10) return false;
        return activated[rotation];
    }

    @Override
    public void setActivated(int rotation, boolean value) {
        if (rotation >= 1 && rotation <= 10) activated[rotation] = value;
    }

    @Override public int getPowerKillCount() { return powerKillCount; }
    @Override public void addPowerKill(int count) { this.powerKillCount += count; }
    @Override public double getPowerDamageBonus() {
        double bonus = powerKillCount * ServerConfig.getRot1DamagePerKill();
        double cap = ServerConfig.getRot1DamageCap();
        return cap > 0 ? Math.min(bonus, cap) : bonus;
    }

    @Override public int getHealthKillCount() { return healthKillCount; }
    @Override public void addHealthKill(int count) { this.healthKillCount += count; }
    @Override public double getHealthBonus() {
        double bonus = healthKillCount * ServerConfig.getRot5HealthPerKill();
        double cap = ServerConfig.getRot5HealthCap();
        return cap > 0 ? Math.min(bonus, cap) : bonus;
    }

    @Override public long getUndyingCooldownEnd() { return undyingCooldownEnd; }
    @Override public void setUndyingCooldownEnd(long time) { this.undyingCooldownEnd = time; }
    @Override public long getInvincibleEnd() { return invincibleEnd; }
    @Override public void setInvincibleEnd(long time) { this.invincibleEnd = time; }
    @Override public boolean isInCooldown(long now) { return now < undyingCooldownEnd; }
    @Override public boolean isInvincible(long now) { return now < invincibleEnd; }

    @Override public int getAdaptationLevel(String damageType) { return adaptationLevels.getOrDefault(damageType, 0); }

    @Override
    public void addAdaptation(String damageType, long now) {
        Long lastTime = adaptationTimes.get(damageType);
        long cdMs = (long) ServerConfig.getRot10StackCooldownSeconds() * 1000L;
        if (lastTime != null && now - lastTime < cdMs) return;
        int current = adaptationLevels.getOrDefault(damageType, 0);
        if (current < ServerConfig.getRot10MaxStacks()) adaptationLevels.put(damageType, current + 1);
        adaptationTimes.put(damageType, now);
    }

    @Override
    public double getAdaptationReduction(String damageType) {
        return Math.min(1.0, getAdaptationLevel(damageType) * ServerConfig.getRot10ReductionPerStack());
    }

    @Override public Map<String, Integer> getAllAdaptationLevels() { return adaptationLevels; }
    @Override public Map<String, Long> getAllAdaptationTimes() { return adaptationTimes; }
    @Override public int getCompletedAdaptationCount() {
        int count = 0;
        for (int level : adaptationLevels.values()) {
            if (level >= 10) count++;
        }
        return count;
    }
    @Override public boolean hasFullAdaptation(String damageType) {
        return getAdaptationLevel(damageType) >= ServerConfig.getRot10MaxStacks();
    }
    @Override public boolean hasFlightAdaptation() {
        return getCompletedAdaptationCount() >= 3;
    }
    // ===== 十转：负面效果适应 =====
    @Override public int getEffectAdaptationLevel(String effectId) {
        return effectAdaptationLevels.getOrDefault(effectId, 0);
    }
    @Override
    public void addEffectAdaptation(String effectId, long now) {
        Long lastTime = effectAdaptationTimes.get(effectId);
        if (lastTime != null && now - lastTime < EFFECT_ADAPTATION_CD_MS) return;
        int current = effectAdaptationLevels.getOrDefault(effectId, 0);
        if (current < EFFECT_ADAPTATION_MAX_LEVEL) {
            effectAdaptationLevels.put(effectId, current + 1);
        }
        effectAdaptationTimes.put(effectId, now);
    }
    @Override
    public double getEffectAdaptationReduction(String effectId) {
        return Math.min(1.0, getEffectAdaptationLevel(effectId) * ADAPTATION_PER_LEVEL);
    }
    @Override public Map<String, Integer> getAllEffectAdaptationLevels() { return effectAdaptationLevels; }
    @Override public Map<String, Long> getAllEffectAdaptationTimes() { return effectAdaptationTimes; }
    @Override public boolean hasFullEffectAdaptation(String effectId) {
        return getEffectAdaptationLevel(effectId) >= EFFECT_ADAPTATION_MAX_LEVEL;
    }
    @Override public int getEffectExposureTicks(String effectId) {
        return effectExposureTicks.getOrDefault(effectId, 0);
    }
    @Override public void addEffectExposureTicks(String effectId, int ticks) {
        effectExposureTicks.put(effectId, effectExposureTicks.getOrDefault(effectId, 0) + ticks);
    }
    @Override public void resetEffectExposureTicks(String effectId) {
        effectExposureTicks.put(effectId, 0);
    }
    @Override public boolean hasAnnouncedFlightAdaptation() { return announcedFlightAdaptation; }
    @Override public void setAnnouncedFlightAdaptation(boolean value) { this.announcedFlightAdaptation = value; }
    @Override public boolean isFlightGrantedByMod() { return flightGrantedByMod; }
    @Override public void setFlightGrantedByMod(boolean value) { this.flightGrantedByMod = value; }
    @Override public boolean hasOpenedFirstChest() { return openedFirstChest; }
    @Override public void setOpenedFirstChest(boolean value) { this.openedFirstChest = value; }

    // 自然获得
    @Override public int getHungerDeathCount() { return hungerDeathCount; }
    @Override public void addHungerDeath() { this.hungerDeathCount++; }
    @Override public boolean hasNatallyGotRot2() { return natallyGotRot2; }
    @Override public void setNatallyGotRot2(boolean value) { this.natallyGotRot2 = value; }

    @Override public int getDarkDeathCount() { return darkDeathCount; }
    @Override public void addDarkDeath() { this.darkDeathCount++; }
    @Override public boolean hasNatallyGotRot3() { return natallyGotRot3; }
    @Override public void setNatallyGotRot3(boolean value) { this.natallyGotRot3 = value; }
    @Override public boolean isLastDamageInDark() { return lastDamageInDark; }
    @Override public void setLastDamageInDark(boolean value) { this.lastDamageInDark = value; }
    @Override public boolean isNightVisionEnabled() { return nightVisionEnabled; }
    @Override public void setNightVisionEnabled(boolean enabled) { this.nightVisionEnabled = enabled; }

    @Override public int getPoisonDeathCount() { return poisonDeathCount; }
    @Override public void addPoisonDeath() { this.poisonDeathCount++; }
    @Override public boolean hasNatallyGotRot4() { return natallyGotRot4; }
    @Override public void setNatallyGotRot4(boolean value) { this.natallyGotRot4 = value; }

    @Override public boolean hasKilledWither() { return killedWither; }
    @Override public void setKilledWither(boolean value) { this.killedWither = value; }
    @Override public boolean hasKilledEnderDragon() { return killedEnderDragon; }
    @Override public void setKilledEnderDragon(boolean value) { this.killedEnderDragon = value; }

    @Override public int getMagicDeathCount() { return magicDeathCount; }
    @Override public void addMagicDeath() { this.magicDeathCount++; }
    @Override public boolean hasNatallyGotRot6() { return natallyGotRot6; }
    @Override public void setNatallyGotRot6(boolean value) { this.natallyGotRot6 = value; }

    @Override public int getTotemDeathCount() { return totemDeathCount; }
    @Override public void addTotemDeath() { this.totemDeathCount++; }
    @Override public boolean hasTotemDeathThisCycle() { return totemDeathThisCycle; }
    @Override public void setTotemDeathThisCycle(boolean value) { this.totemDeathThisCycle = value; }
    @Override public boolean hasNatallyGotRot7() { return natallyGotRot7; }
    @Override public void setNatallyGotRot7(boolean value) { this.natallyGotRot7 = value; }
    @Override public boolean isTotemEffectActive() { return totemEffectActive; }
    @Override public void setTotemEffectActive(boolean value) { this.totemEffectActive = value; }
    @Override public long getTotemEffectEndTime() { return totemEffectEndTime; }
    @Override public void setTotemEffectEndTime(long time) { this.totemEffectEndTime = time; }

    @Override public int getJunkFishCount() { return junkFishCount; }
    @Override public void addJunkFish() { this.junkFishCount++; }
    @Override public boolean hasNatallyGotRot8() { return natallyGotRot8; }
    @Override public void setNatallyGotRot8(boolean value) { this.natallyGotRot8 = value; }

    @Override public int getConsecutiveTotemCount() { return consecutiveTotemCount; }
    @Override public void setConsecutiveTotemCount(int count) { this.consecutiveTotemCount = count; }
    @Override public long getLastTotemTriggerTime() { return lastTotemTriggerTime; }
    @Override public void setLastTotemTriggerTime(long time) { this.lastTotemTriggerTime = time; }
    @Override public int getTotemChainDeathCount() { return totemChainDeathCount; }
    @Override public void addTotemChainDeath() { this.totemChainDeathCount++; }
    @Override public boolean hasNatallyGotRot9() { return natallyGotRot9; }
    @Override public void setNatallyGotRot9(boolean value) { this.natallyGotRot9 = value; }

    @Override public boolean hasNatallyGotRot10() { return natallyGotRot10; }
    @Override public void setNatallyGotRot10(boolean value) { this.natallyGotRot10 = value; }

    @Override public boolean hasAdvancement(String key) { return grantedAdvancements.contains(key); }
    @Override public void setAdvancement(String key, boolean value) {
        if (value) grantedAdvancements.add(key); else grantedAdvancements.remove(key);
    }
    @Override public int getDimensionFixDelay() { return dimensionFixDelay; }
    @Override public void setDimensionFixDelay(int ticks) { this.dimensionFixDelay = ticks; }

    // ===== 饰品防收取快照 =====
    @Override
    public java.util.Map<String, net.minecraft.nbt.CompoundTag> getAccessorySnapshot() {
        return accessorySnapshot;
    }
    @Override
    public void setAccessorySnapshot(java.util.Map<String, net.minecraft.nbt.CompoundTag> snapshot) {
        accessorySnapshot.clear();
        accessorySnapshot.putAll(snapshot);
    }
    @Override
    public void putAccessory(String slotIdentifier, net.minecraft.nbt.CompoundTag itemTag) {
        accessorySnapshot.put(slotIdentifier, itemTag);
    }
    @Override
    public void clearAccessorySnapshot() {
        accessorySnapshot.clear();
    }

    @Override
    public void syncToClient(Player player) {
        if (player instanceof ServerPlayer sp) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new SyncPlayerDataPacket(saveNBT()));
        }
    }

    @Override
    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("ringEquipped", ringEquipped);
        CompoundTag actTag = new CompoundTag();
        for (int i = 1; i <= 10; i++) actTag.putBoolean("rot_" + i, activated[i]);
        tag.put("activated", actTag);
        tag.putInt("powerKills", powerKillCount);
        tag.putInt("healthKills", healthKillCount);
        tag.putLong("undyingCd", undyingCooldownEnd);
        tag.putLong("invincibleEnd", invincibleEnd);
        tag.putBoolean("firstChest", openedFirstChest);

        CompoundTag adaptTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : adaptationLevels.entrySet()) {
            CompoundEntry entry = new CompoundEntry();
            entry.level = e.getValue();
            entry.time = adaptationTimes.getOrDefault(e.getKey(), 0L);
            adaptTag.put(e.getKey(), entry.save());
        }
        tag.put("adaptation", adaptTag);
        // 十转：负面效果适应
        CompoundTag effectAdaptTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : effectAdaptationLevels.entrySet()) {
            EffectEntry entry = new EffectEntry();
            entry.level = e.getValue();
            entry.time = effectAdaptationTimes.getOrDefault(e.getKey(), 0L);
            entry.exposure = effectExposureTicks.getOrDefault(e.getKey(), 0);
            effectAdaptTag.put(e.getKey(), entry.save());
        }
        tag.put("effectAdaptation", effectAdaptTag);

        tag.putInt("hungerDeaths", hungerDeathCount);
        tag.putBoolean("gotRot2", natallyGotRot2);
        tag.putInt("darkDeaths", darkDeathCount);
        tag.putBoolean("gotRot3", natallyGotRot3);
        tag.putBoolean("lastDamageInDark", lastDamageInDark);
        tag.putBoolean("nightVisionEnabled", nightVisionEnabled);
        tag.putInt("poisonDeaths", poisonDeathCount);
        tag.putBoolean("gotRot4", natallyGotRot4);
        tag.putBoolean("killedWither", killedWither);
        tag.putBoolean("killedEnderDragon", killedEnderDragon);
        tag.putInt("magicDeaths", magicDeathCount);
        tag.putBoolean("gotRot6", natallyGotRot6);
        tag.putInt("totemDeaths", totemDeathCount);
        tag.putBoolean("gotRot7", natallyGotRot7);
        tag.putBoolean("totemEffectActive", totemEffectActive);
        tag.putLong("totemEffectEnd", totemEffectEndTime);
        tag.putInt("junkFish", junkFishCount);
        tag.putBoolean("gotRot8", natallyGotRot8);
        tag.putInt("consecTotem", consecutiveTotemCount);
        tag.putLong("lastTotemTime", lastTotemTriggerTime);
        tag.putInt("totemChainDeaths", totemChainDeathCount);
        tag.putBoolean("gotRot9", natallyGotRot9);
        tag.putBoolean("gotRot10", natallyGotRot10);
        tag.putBoolean("announcedFlight", announcedFlightAdaptation);
        tag.putBoolean("flightGrantedByMod", flightGrantedByMod);

        CompoundTag advTag = new CompoundTag();
        for (String key : grantedAdvancements) advTag.putBoolean(key, true);
        tag.put("advancements", advTag);
        // 饰品防收取快照
        CompoundTag snapTag = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : accessorySnapshot.entrySet()) {
            snapTag.put(e.getKey(), e.getValue());
        }
        tag.put("accessorySnapshot", snapTag);
        return tag;
    }

    @Override
    public void loadNBT(CompoundTag tag) {
        ringEquipped = tag.getBoolean("ringEquipped");
        if (tag.contains("activated")) {
            CompoundTag actTag = tag.getCompound("activated");
            for (int i = 1; i <= 10; i++) activated[i] = actTag.getBoolean("rot_" + i);
        }
        powerKillCount = tag.getInt("powerKills");
        healthKillCount = tag.getInt("healthKills");
        undyingCooldownEnd = tag.getLong("undyingCd");
        invincibleEnd = tag.getLong("invincibleEnd");
        openedFirstChest = tag.getBoolean("firstChest");

        adaptationLevels.clear();
        adaptationTimes.clear();
        if (tag.contains("adaptation")) {
            CompoundTag adaptTag = tag.getCompound("adaptation");
            for (String key : adaptTag.getAllKeys()) {
                CompoundEntry entry = CompoundEntry.load(adaptTag.getCompound(key));
                adaptationLevels.put(key, entry.level);
                adaptationTimes.put(key, entry.time);
            }
        }
        // 十转：负面效果适应
        effectAdaptationLevels.clear();
        effectAdaptationTimes.clear();
        effectExposureTicks.clear();
        if (tag.contains("effectAdaptation")) {
            CompoundTag effectAdaptTag = tag.getCompound("effectAdaptation");
            for (String key : effectAdaptTag.getAllKeys()) {
                EffectEntry entry = EffectEntry.load(effectAdaptTag.getCompound(key));
                effectAdaptationLevels.put(key, entry.level);
                effectAdaptationTimes.put(key, entry.time);
                effectExposureTicks.put(key, entry.exposure);
            }
        }

        hungerDeathCount = tag.getInt("hungerDeaths");
        natallyGotRot2 = tag.getBoolean("gotRot2");
        darkDeathCount = tag.getInt("darkDeaths");
        natallyGotRot3 = tag.getBoolean("gotRot3");
        lastDamageInDark = tag.getBoolean("lastDamageInDark");
        nightVisionEnabled = tag.getBoolean("nightVisionEnabled");
        // 兼容旧存档：没有该字段时默认开启
        if (!tag.contains("nightVisionEnabled")) nightVisionEnabled = true;
        poisonDeathCount = tag.getInt("poisonDeaths");
        natallyGotRot4 = tag.getBoolean("gotRot4");
        killedWither = tag.getBoolean("killedWither");
        killedEnderDragon = tag.getBoolean("killedEnderDragon");
        magicDeathCount = tag.getInt("magicDeaths");
        natallyGotRot6 = tag.getBoolean("gotRot6");
        totemDeathCount = tag.getInt("totemDeaths");
        natallyGotRot7 = tag.getBoolean("gotRot7");
        totemEffectActive = tag.getBoolean("totemEffectActive");
        totemEffectEndTime = tag.getLong("totemEffectEnd");
        junkFishCount = tag.getInt("junkFish");
        natallyGotRot8 = tag.getBoolean("gotRot8");
        consecutiveTotemCount = tag.getInt("consecTotem");
        lastTotemTriggerTime = tag.getLong("lastTotemTime");
        totemChainDeathCount = tag.getInt("totemChainDeaths");
        natallyGotRot9 = tag.getBoolean("gotRot9");
        natallyGotRot10 = tag.getBoolean("gotRot10");
        announcedFlightAdaptation = tag.getBoolean("announcedFlight");
        flightGrantedByMod = tag.getBoolean("flightGrantedByMod");

        grantedAdvancements.clear();
        if (tag.contains("advancements")) {
            CompoundTag advTag = tag.getCompound("advancements");
            for (String key : advTag.getAllKeys())
                if (advTag.getBoolean(key)) grantedAdvancements.add(key);
        }
        // 饰品防收取快照
        accessorySnapshot.clear();
        if (tag.contains("accessorySnapshot")) {
            CompoundTag snapTag = tag.getCompound("accessorySnapshot");
            for (String key : snapTag.getAllKeys()) {
                accessorySnapshot.put(key, snapTag.getCompound(key));
            }
        }
    }

    private static class CompoundEntry {
        int level;
        long time;
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("lvl", level);
            t.putLong("time", time);
            return t;
        }
        static CompoundEntry load(CompoundTag t) {
            CompoundEntry e = new CompoundEntry();
            e.level = t.getInt("lvl");
            e.time = t.getLong("time");
            return e;
        }
    }
    // 负面效果适应条目：层数 + 上次叠加时间 + 当前暴露tick
    private static class EffectEntry {
        int level;
        long time;
        int exposure;
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("lvl", level);
            t.putLong("time", time);
            t.putInt("exp", exposure);
            return t;
        }
        static EffectEntry load(CompoundTag t) {
            EffectEntry e = new EffectEntry();
            e.level = t.getInt("lvl");
            e.time = t.getLong("time");
            e.exposure = t.getInt("exp");
            return e;
        }
    }
}
