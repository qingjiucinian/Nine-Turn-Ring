package com.jiuzhuan.event;

import com.jiuzhuan.capability.IPlayerData;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModEventHandlers {

    // 5转血量修饰符UUID
    private static final UUID HEALTH_BONUS_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    // 8转幸运修饰符UUID
    private static final UUID LUCK_BONUS_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    // 飞行适应玩家的摔落追踪：记录离开地面时的Y坐标
    private final Map<UUID, Double> playerAirStartY = new HashMap<>();
    // 飞行适应玩家的摔落追踪：记录上一tick是否在地面
    private final Map<UUID, Boolean> playerWasOnGround = new HashMap<>();

    // 玩家进入游戏自动给予九转戒（仅一次）
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 检查玩家是否已经拥有过戒指（用一个标记）
            if (!data.isRingEquipped() && !hasRing(player)) {
                // 给予戒指
                ItemStack ring = new ItemStack(ModItems.NINE_TURN_RING.get());
                if (!player.getInventory().add(ring)) {
                    player.spawnAtLocation(ring);
                }
                player.sendSystemMessage(Component.translatable("nine_turn_ring.message.ring_obtained"));
            }
            // 同步轮转槽位状态：装备了戒指开10个，没装备关闭
            boolean equipped = isRingWorn(player);
            PlayerDataProvider.setRotationSlots(player, equipped ? 10 : 0);
            data.setRingEquipped(equipped);
            data.syncToClient(player);
        });
    }

    // 玩家维度切换：重新设置轮转槽位（Curios在维度切换时会重建饰品栏）
    @SubscribeEvent
    public void onPlayerChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 立即同步一次当前数据（适应层数、击杀数等）
            data.syncToClient(player);
            // 延迟10 tick再重建轮转槽和校验激活状态（等Curios饰品栏完全重建）
            data.setDimensionFixDelay(10);
            // 维度切换会重置PlayerAbilities，立即强制恢复飞行（不等下一个tick）
            applyFlightAdaptation(player, data);
        });
    }

    // 检查戒指是否装备在Curios饰品栏中
    private boolean isRingWorn(Player player) {
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                var inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (handler.getStacks().getStackInSlot(i).is(ModItems.NINE_TURN_RING.get())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    // 检查玩家是否已有戒指（背包或饰品栏）
    private boolean hasRing(Player player) {
        // 检查背包
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.NINE_TURN_RING.get())) {
                return true;
            }
        }
        // 检查Curios饰品栏
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                var inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    var stacksHandler = entry.getValue();
                    for (int i = 0; i < stacksHandler.getSlots(); i++) {
                        if (stacksHandler.getStacks().getStackInSlot(i).is(ModItems.NINE_TURN_RING.get())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // 玩家Tick - 应用所有常驻效果
    // LOWEST优先级：确保在其他模组（如Boss禁飞行）之后执行，强制恢复被外部禁用的飞行
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 维度切换延迟修复：等Curios饰品栏重建完成后再设置轮转槽和校验状态
            if (data.getDimensionFixDelay() > 0) {
                data.setDimensionFixDelay(data.getDimensionFixDelay() - 1);
                if (data.getDimensionFixDelay() == 0) {
                    boolean equipped = isRingWorn(player);
                    PlayerDataProvider.setRotationSlots(player, equipped ? 10 : 0);
                    data.setRingEquipped(equipped);
                    data.syncToClient(player);
                    // 维度切换修复完成后再次强制恢复飞行（确保Curios饰品栏重建后状态正确）
                    applyFlightAdaptation(player, data);
                }
            }
            if (!data.isRingEquipped()) return;

            long now = System.currentTimeMillis();

            // 每秒校验：只有真正装备在轮转槽中的转才保持激活
            // 维度切换延迟修复期间跳过校验，避免Curios饰品栏未重建时误取消激活
            if (player.tickCount % 20 == 0 && data.getDimensionFixDelay() <= 0) {
                validateActivatedRotations(player, data);
            }

            // 2转饱食：时刻饱腹，免疫饥饿虚弱
            if (data.isActivated(2)) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0f);
                player.getFoodData().setExhaustion(0.0f);
            }

            // 3转夜视：持续15s夜视（每tick刷新，无法被清除），需手动开关开启
            if (data.isActivated(3) && data.isNightVisionEnabled()) {
                MobEffectInstance nightVision = new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false);
                player.addEffect(nightVision);
            }

            // 4转再生：每tick回复生命（无视禁疗，直接修改生命值，绕过LivingHealEvent和所有治疗减益）
            // 加isAlive检查，防止玩家异常死亡状态下被回血导致无法重生
            if (data.isActivated(4) && player.tickCount % 10 == 0 && player.isAlive()) {
                if (player.getHealth() < player.getMaxHealth()) {
                    // 直接设置生命值，无视任何禁疗效果/治疗减免/治疗反制
                    float newHealth = Math.min(player.getMaxHealth(), player.getHealth() + 2.0f);
                    player.setHealth(newHealth);
                    // 同步清除可能存在的禁疗类效果（凋灵已在onMobEffectApplicable中免疫，这里兜底清除其他mod的禁疗）
                    removeHealingPreventionEffects(player);
                }
            }

            // 6转抗性：常驻抗性提升III已移至 PlayerDamageHandler 直接减伤（非药水效果）

            // 8转幸运：+100幸运值
            applyLuckModifier(player, data.isActivated(8));

            // 5转血量：根据击杀数更新最大生命
            applyHealthModifier(player, data);
            // 10转飞行适应：完成3种伤害类型适应后给予创造模式飞行能力
            applyFlightAdaptation(player, data);
            // 10转满级适应：特殊效果免疫（溺水无限氧气、冰冻免疫、火焰免疫等）
            applyFullAdaptationImmunities(player, data);
            // 十转：负面效果适应（持续暴露叠层，满5层免疫）
            applyEffectAdaptation(player, data);
            // 飞行适应摔落伤害处理：mayfly会导致原版摔落免疫，这里手动追踪并施加摔落伤害
            applyFlightFallDamage(player, data);
        });
    }

    // 药水效果免疫
    @SubscribeEvent
    public void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;
            var effect = event.getEffectInstance().getEffect();

            // 2转：免疫饥饿、虚弱
            if (data.isActivated(2)) {
                if (effect == MobEffects.HUNGER || effect == MobEffects.WEAKNESS) {
                    event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                    return;
                }
            }
            // 3转：免疫黑暗、失明
            if (data.isActivated(3)) {
                if (effect == MobEffects.DARKNESS || effect == MobEffects.BLINDNESS) {
                    event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                    return;
                }
            }
            // 4转：免疫凋零、中毒
            if (data.isActivated(4)) {
                if (effect == MobEffects.WITHER || effect == MobEffects.POISON) {
                    event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                    return;
                }
            }
            // 十转：负面效果适应——已满层适应的效果直接免疫
            if (data.isActivated(10)) {
                ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(effect);
                if (rl != null && data.hasFullEffectAdaptation(rl.toString()) && !data.isEffectAdaptationDisabled(rl.toString())) {
                    event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                    return;
                }
            }
        });
    }

    // 阻止药水效果被移除（牛奶等）- 针对戒指提供的夜视
    @SubscribeEvent
    public void onMobEffectRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;
            // 3转夜视不允许被移除（仅当手动开关开启时）
            if (data.isActivated(3) && data.isNightVisionEnabled() && event.getEffect() == MobEffects.NIGHT_VISION) {
                event.setCanceled(true);
            }
        });
    }

    // 校验所有转的激活状态：如果某转标记为激活但物品不在轮转槽中，则取消激活
    private void validateActivatedRotations(Player player, IPlayerData data) {
        boolean[] equipped = new boolean[11];
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                var inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    if (!"rotation".equals(entry.getKey())) continue;
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStacks().getStackInSlot(i);
                        int rot = getRotationLevelFromStack(stack);
                        if (rot >= 1 && rot <= 10) equipped[rot] = true;
                    }
                }
            }
        } catch (Exception ignored) {}
        boolean needSync = false;
        for (int i = 1; i <= 10; i++) {
            if (data.isActivated(i) && !equipped[i]) {
                data.setActivated(i, false);
                needSync = true;
            }
        }
        if (needSync) data.syncToClient(player);
    }

    private int getRotationLevelFromStack(ItemStack stack) {
        if (stack.is(ModItems.ROTATION_1_POWER.get())) return 1;
        if (stack.is(ModItems.ROTATION_2_SATIETY.get())) return 2;
        if (stack.is(ModItems.ROTATION_3_NIGHT_VISION.get())) return 3;
        if (stack.is(ModItems.ROTATION_4_REGEN.get())) return 4;
        if (stack.is(ModItems.ROTATION_5_HEALTH.get())) return 5;
        if (stack.is(ModItems.ROTATION_6_RESISTANCE.get())) return 6;
        if (stack.is(ModItems.ROTATION_7_UNDYING.get())) return 7;
        if (stack.is(ModItems.ROTATION_8_LUCK.get())) return 8;
        if (stack.is(ModItems.ROTATION_9_IMMORTAL.get())) return 9;
        if (stack.is(ModItems.ROTATION_10_ADAPTATION.get())) return 10;
        return 0;
    }

    // 应用幸运修饰符
    private void applyLuckModifier(Player player, boolean active) {
        AttributeInstance luckAttr = player.getAttribute(Attributes.LUCK);
        if (luckAttr == null) return;

        AttributeModifier existing = luckAttr.getModifier(LUCK_BONUS_UUID);
        if (active) {
            if (existing == null) {
                luckAttr.addPermanentModifier(new AttributeModifier(
                        LUCK_BONUS_UUID, "nine_turn_ring_luck_bonus", 100.0,
                        AttributeModifier.Operation.ADDITION));
            }
        } else {
            if (existing != null) {
                luckAttr.removeModifier(LUCK_BONUS_UUID);
            }
        }
    }

    // 应用血量修饰符（5转）
    private void applyHealthModifier(Player player, IPlayerData data) {
        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return;

        AttributeModifier existing = healthAttr.getModifier(HEALTH_BONUS_UUID);
        if (data.isActivated(5)) {
            double bonus = data.getHealthBonus(); // 比例，如0.5表示+50%
            double amount = 20.0 * bonus; // 基础20血的百分比加成
            if (existing == null || existing.getAmount() != amount) {
                if (existing != null) {
                    healthAttr.removeModifier(HEALTH_BONUS_UUID);
                }
                healthAttr.addPermanentModifier(new AttributeModifier(
                        HEALTH_BONUS_UUID, "nine_turn_ring_health_bonus", amount,
                        AttributeModifier.Operation.ADDITION));
            }
        } else {
            if (existing != null) {
                healthAttr.removeModifier(HEALTH_BONUS_UUID);
            }
        }
    }

    // 10转飞行适应：完成3种伤害类型适应后给予创造模式飞行能力
    private void applyFlightAdaptation(Player player, IPlayerData data) {
        // 创造/旁观模式本来就能飞，不干预
        if (player.isCreative() || player.isSpectator()) return;

        boolean shouldFly = data.isActivated(10) && data.hasFlightAdaptation();
        if (shouldFly) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                data.setFlightGrantedByMod(true);
                // 首次获得飞行适应时发送提示
                if (!data.hasAnnouncedFlightAdaptation()) {
                    data.setAnnouncedFlightAdaptation(true);
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.fly_adapt"));
                    data.syncToClient(player);
                }
            }
        } else {
            // 只关闭模组自己授予的飞行，不影响其他模组（星月遗物、等价交换等）的飞行能力
            if (data.isFlightGrantedByMod() && player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                data.setFlightGrantedByMod(false);
            }
        }
    }

    // 飞行适应摔落伤害：mayfly会导致原版摔落免疫，手动追踪下落距离并施加伤害
    private void applyFlightFallDamage(Player player, IPlayerData data) {
        if (player.isCreative() || player.isSpectator()) return;
        if (!data.isActivated(10) || !data.hasFlightAdaptation()) return;

        UUID uuid = player.getUUID();
        boolean onGround = player.onGround();
        boolean wasOnGround = playerWasOnGround.getOrDefault(uuid, true);

        if (onGround) {
            // 在地面时强制关闭飞行状态，避免flying导致摔落免疫
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            // 刚从空中落地：计算实际下落距离并手动施加摔落伤害
            if (!wasOnGround) {
                Double startY = playerAirStartY.get(uuid);
                if (startY != null) {
                    double fallDist = startY - player.getY();
                    if (fallDist > 3.0) {
                        float damage = (float) (fallDist - 3.0);
                        player.hurt(player.level().damageSources().fall(), damage);
                    }
                }
            }
            playerAirStartY.put(uuid, player.getY());
        } else {
            // 在空中：如果之前在地面，记录起始Y坐标
            if (wasOnGround) {
                playerAirStartY.put(uuid, player.getY());
            }
        }
        playerWasOnGround.put(uuid, onGround);
    }

    // 取消飞行适应玩家的原版摔落伤害（避免与手动施加叠加）
    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isActivated(10) || !data.hasFlightAdaptation()) return;
            if (player.isCreative() || player.isSpectator()) return;
            // 原版摔落伤害由手动追踪统一处理，这里取消避免叠加
            event.setCanceled(true);
        });
    }

    // 消除飞行挖掘惩罚和水下挖掘惩罚（10转飞行适应激活时）
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        // 挖掘速度在客户端计算，需在客户端处理（服务端仅做校验）
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isActivated(10) || !data.hasFlightAdaptation()) return;
            float speed = event.getNewSpeed();
            // 消除飞行挖掘惩罚：不在地面上时Minecraft将速度除以5，这里乘回来
            if (!player.onGround()) {
                speed *= 5.0F;
            }
            // 消除水下挖掘惩罚：在水中且没有水下呼吸附魔时Minecraft将速度除以5，这里乘回来
            if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
                speed *= 5.0F;
            }
            event.setNewSpeed(speed);
        });
    }

    /**
     * 10转满级适应的特殊效果免疫：
     * - 溺水适应 → 水下无限氧气
     * - 冰冻适应 → 不会被冻住（细雪/冰冻天气）
     * - 火焰/燃烧/岩浆/岩浆块/雷击适应 → 不会燃烧
     * - 凋灵适应 → 清除凋灵效果
     * 伤害本身的完全免疫在 PlayerDamageHandler 中处理。
     */
    private void applyFullAdaptationImmunities(Player player, IPlayerData data) {
        if (!data.isActivated(10)) return;
        // 溺水适应：无限氧气
        if (data.hasFullAdaptation("drown") && !data.isDamageAdaptationDisabled("drown")) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        // 冰冻适应：清除冰冻进度，不会被冻住
        if (data.hasFullAdaptation("freeze") && !data.isDamageAdaptationDisabled("freeze")) {
            player.setTicksFrozen(0);
        }
        // 火焰/燃烧/岩浆/岩浆块/雷击适应：清除燃烧状态，不会被灼烧
        if ((data.hasFullAdaptation("inFire") && !data.isDamageAdaptationDisabled("inFire")) || (data.hasFullAdaptation("onFire") && !data.isDamageAdaptationDisabled("onFire"))
                || (data.hasFullAdaptation("lava") && !data.isDamageAdaptationDisabled("lava")) || (data.hasFullAdaptation("hotFloor") && !data.isDamageAdaptationDisabled("hotFloor"))
                || (data.hasFullAdaptation("lightningBolt") && !data.isDamageAdaptationDisabled("lightningBolt"))) {
            player.clearFire();
        }
        // 凋灵适应：清除凋灵效果（4转也会免疫，这里作为满级适应的额外保障）
        if (data.hasFullAdaptation("wither") && !data.isDamageAdaptationDisabled("wither")) {
            player.removeEffect(MobEffects.WITHER);
        }
    }

    /**
     * 十转：负面效果适应
     * <p>
     * 机制：
     * <ul>
     *   <li>持续暴露在负面效果下，每tick累积1点暴露值</li>
     *   <li>暴露值达到100（约5秒）且距离上次叠加超过3秒 → 适应层数+1</li>
     *   <li>满5层 → 完全免疫该负面效果（直接移除且无法再施加）</li>
     * </ul>
     */
    private void applyEffectAdaptation(Player player, IPlayerData data) {
        if (!data.isActivated(10)) return;

        long now = System.currentTimeMillis();
        List<MobEffectInstance> effects = new ArrayList<>(player.getActiveEffects());

        for (MobEffectInstance instance : effects) {
            net.minecraft.world.effect.MobEffect effect = instance.getEffect();
            // 跳过有益效果
            if (effect.isBeneficial()) continue;

            ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (rl == null) continue;
            String effectId = rl.toString();

            // 被禁用的效果类型：不免疫、不叠加，完全失效
            if (data.isEffectAdaptationDisabled(effectId)) {
                continue;
            }

            // 满层适应：直接移除
            if (data.hasFullEffectAdaptation(effectId)) {
                player.removeEffect(effect);
                continue;
            }

            // 累积暴露时间
            data.addEffectExposureTicks(effectId, 1);

            // 达到阈值(100tick=5秒)且不在CD(3秒)中 → 叠加一层
            if (data.getEffectExposureTicks(effectId) >= 100) {
                data.addEffectAdaptation(effectId, now);
                data.resetEffectExposureTicks(effectId);
                int level = data.getEffectAdaptationLevel(effectId);
                if (level >= 5) {
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.effect_full_immune",
                            Component.translatable(effect.getDescriptionId())));
                    player.removeEffect(effect);
                } else {
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.effect_adapt",
                            Component.translatable(effect.getDescriptionId()), level));
                }
                data.syncToClient(player);
            }
        }
    }

    /**
     * 清除玩家身上所有可能阻止治疗的效果（禁疗）。
     * 4转再生使用直接setHealth绕过LivingHealEvent，此方法作为兜底清除其他mod的禁疗类效果。
     */
    private void removeHealingPreventionEffects(Player player) {
        // 遍历所有活跃效果，移除可能阻止治疗的效果
        java.util.List<net.minecraft.world.effect.MobEffectInstance> toRemove = new java.util.ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            net.minecraft.world.effect.MobEffect effect = instance.getEffect();
            String effectName = effect.getDescriptionId().toLowerCase();
            // 常见禁疗效果关键词：healing_impaired, healing_disabled, anti_heal, no_heal, wither
            if (effectName.contains("heal") && (effectName.contains("disable") || effectName.contains("impair")
                    || effectName.contains("prevent") || effectName.contains("block") || effectName.contains("anti"))) {
                toRemove.add(instance);
            }
        }
        for (MobEffectInstance instance : toRemove) {
            player.removeEffect(instance.getEffect());
        }
    }
}
