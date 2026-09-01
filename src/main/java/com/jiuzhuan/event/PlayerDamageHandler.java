package com.jiuzhuan.event;

import com.jiuzhuan.capability.IPlayerData;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerDamageHandler {

    // 记录每个玩家 tick 开始时的速度，用于抵消爆炸击退（爆炸击退在 hurt() 之前通过 push() 应用）
    private static final Map<UUID, Vec3> preTickVelocity = new HashMap<>();
    // 标记玩家在本次伤害处理中是否完全免疫（用于联动取消击退事件）
    private static final Set<UUID> immuneThisHit = new HashSet<>();

    @SubscribeEvent
    public void onPlayerTickStart(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (event.player.level().isClientSide) return;
        preTickVelocity.put(event.player.getUUID(), event.player.getDeltaMovement());
        immuneThisHit.remove(event.player.getUUID());
    }

    // 伤害类型归一化：将同类伤害的不同变体合并为统一类型
    private String normalizeDamageType(String msgId) {
        // 所有爆炸类伤害统一为 explosion（苦力怕、TNT、玩家爆炸等）
        if (msgId.equals("explosion.player")) return "explosion";
        return msgId;
    }

    // 实体攻击最早阶段取消：怪物攻击、玩家攻击、弹射物攻击都走这里，满级适应直接终止整个攻击流程
    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped() || !data.isActivated(10)) return;
            String damageType = normalizeDamageType(event.getSource().getMsgId());
            if (data.hasFullAdaptation(damageType) && !data.isDamageAdaptationDisabled(damageType)) {
                event.setCanceled(true);
                immuneThisHit.add(player.getUUID());
                // 销毁弹射物（箭矢、三叉戟、火球、烟花等），避免插在身上或继续存在
                Entity direct = event.getSource().getDirectEntity();
                if (direct instanceof Projectile) {
                    direct.discard();
                }
                // 恢复速度，抵消已应用的击退
                Vec3 prevVel = preTickVelocity.get(player.getUUID());
                if (prevVel != null) player.setDeltaMovement(prevVel);
                player.hurtTime = 0;
                player.hurtDuration = 0;
            }
        });
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        // ===== 1转力量：玩家造成的伤害加成 =====
        if (event.getSource().getEntity() instanceof Player attacker) {
            attacker.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                if (data.isRingEquipped() && data.isActivated(1)) {
                    double bonus = data.getPowerDamageBonus();
                    float originalDamage = event.getAmount();
                    event.setAmount(originalDamage * (1.0f + (float) bonus));
                }
            });
        }

        // ===== 玩家受到伤害时的处理 =====
        if (!(event.getEntity() instanceof Player player)) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;

            long now = System.currentTimeMillis();
            float damage = event.getAmount();
            String damageType = normalizeDamageType(event.getSource().getMsgId());

            // 7转不死：无敌时间内免疫所有伤害
            if (data.isActivated(7) && data.isInvincible(now)) {
                cancelDamageCompletely(player, event);
                return;
            }

            // 10转适应：受击时叠加层数（3秒cd），按类型减伤；满级则完全免疫
            // 被禁用的伤害类型：不叠加层数，也不应用减伤/免疫
            if (data.isActivated(10) && !data.isDamageAdaptationDisabled(damageType)) {
                data.addAdaptation(damageType, now);
                // 满级适应：完全免疫该类型伤害 + 击退 + 动画
                if (data.hasFullAdaptation(damageType) && !data.isDamageAdaptationDisabled(damageType)) {
                    cancelDamageCompletely(player, event);
                    data.syncToClient(player);
                    return;
                }
                double reduction = data.getAdaptationReduction(damageType);
                damage = damage * (1.0f - (float) reduction);
                data.syncToClient(player);
            }

            // 6转抗性：免疫药水伤害、魔法伤害；50%虚空伤害；75%真实伤害
            if (data.isActivated(6)) {
                if (isMagicDamage(event.getSource())) {
                    cancelDamageCompletely(player, event);
                    return;
                }
                damage = damage * 0.4f;
                if (isVoidDamage(event.getSource())) {
                    damage = damage * 0.5f;
                } else if (isTrueDamage(event.getSource())) {
                    damage = damage * 0.25f;
                }
            }

            // 9转不朽：血量最多降低至配置的最小值（优先级最高，装了9转就纯锁血，不介入7转复活）
            // 加isAlive检查，防止玩家死亡帧仍被锁血导致"死了但血量被拉回"的异常状态
            if (player.isAlive() && data.isActivated(9) && isRotationInSlot(player, 9)) {
                float currentHealth = player.getHealth();
                float minHealth = (float) ServerConfig.getRot9MinHealth();
                float afterDamage = currentHealth - damage;
                if (afterDamage < minHealth) {
                    damage = currentHealth - minHealth;
                    if (damage < 0) damage = 0;
                }
            }

            // 7转涅槃：致命伤害时直接取消伤害并回血，完全不触发死亡（无死亡动画、无物品掉落）
            // 仅在没有9转时触发：有9转时9转已把伤害降到剩1血，玩家不会死，7转不介入
            if (player.isAlive() && !data.isActivated(9)) {
                float currentHealth = player.getHealth();
                float afterDamage = currentHealth - damage;
                if (afterDamage <= 0 && data.isActivated(7) && isRotationInSlot(player, 7) && !data.isInCooldown(now)) {
                    event.setAmount(0);
                    float maxHealth = player.getMaxHealth();
                    player.setHealth(maxHealth * (float) ServerConfig.getRot7HealRatio());
                    data.setInvincibleEnd(now + (long) ServerConfig.getRot7InvincibleSeconds() * 1000L);
                    data.setUndyingCooldownEnd(now + (long) ServerConfig.getRot7CooldownSeconds() * 1000L);
                    data.syncToClient(player);
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.seven_triggered"));
                    return;
                }
            }

            event.setAmount(damage);
        });
    }

    /**
     * 完全取消伤害：包括伤害值、击退、受伤动画。
     * 爆炸击退通过 entity.push() 在 hurt() 之前应用，需恢复 tick 前速度来抵消。
     */
    private void cancelDamageCompletely(Player player, LivingHurtEvent event) {
        event.setAmount(0);
        event.setCanceled(true);
        // 标记本次伤害完全免疫，联动 LivingKnockBackEvent 取消击退
        immuneThisHit.add(player.getUUID());
        // 销毁弹射物（箭矢、三叉戟、火球等）
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile) {
            direct.discard();
        }
        // 恢复 tick 开始时的速度，抵消爆炸等已通过 push() 应用的击退
        Vec3 prevVel = preTickVelocity.get(player.getUUID());
        if (prevVel != null) {
            player.setDeltaMovement(prevVel);
        }
        // 清除受伤动画计时（红色闪烁、后仰视角）
        player.hurtTime = 0;
        player.hurtDuration = 0;
    }

    // 击退事件：如果本次伤害已完全免疫，则取消击退（怪物攻击的击退在 LivingHurtEvent 之后应用）
    @SubscribeEvent
    public void onLivingKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (immuneThisHit.contains(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    // 药水效果事件：本次伤害已完全免疫时，拒绝随之而来的药水效果（箭矢药水、龙息、凋灵效果等）
    @SubscribeEvent
    public void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (immuneThisHit.contains(player.getUUID())) {
            event.setResult(Event.Result.DENY);
        }
    }

    // 爆炸引爆事件：满级适应该爆炸伤害类型的玩家直接从影响列表中移除，完全不受爆炸影响（伤害+击退+冲击）
    // 使用爆炸的实际伤害类型msgId，适配原版和其他模组的自定义爆炸伤害
    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;
        DamageSource source = event.getExplosion().getDamageSource();
        if (source == null) return;
        String damageType = normalizeDamageType(source.getMsgId());
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : event.getAffectedEntities()) {
            if (entity instanceof Player player) {
                player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                    if (data.isRingEquipped() && data.isActivated(10)
                            && data.hasFullAdaptation(damageType) && !data.isDamageAdaptationDisabled(damageType)) {
                        toRemove.add(entity);
                    }
                });
            }
        }
        event.getAffectedEntities().removeAll(toRemove);
    }

    // 判断是否为魔法/药水伤害
    private boolean isMagicDamage(DamageSource source) {
        String id = source.getMsgId();
        return id.equals("magic") || id.equals("indirectMagic") || id.equals("thorns")
                || id.equals("dragonBreath") || id.equals("wither");
    }

    // 判断是否为虚空伤害
    private boolean isVoidDamage(DamageSource source) {
        return source.getMsgId().equals("outOfWorld");
    }

    // 判断是否为真实伤害
    private boolean isTrueDamage(DamageSource source) {
        if (isVoidDamage(source)) return false;
        if (isMagicDamage(source)) return false;
        return source.getMsgId().equals("genericKill");
    }

    /**
     * 实时校验某转物品是否真的在轮转槽中（防止activated状态残留导致效果误触发）
     */
    private boolean isRotationInSlot(Player player, int rotation) {
        try {
            var curiosOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isEmpty()) return false;
            var inv = curiosOpt.get();
            var handler = inv.getCurios().get("rotation");
            if (handler == null) return false;
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStacks().getStackInSlot(i);
                if (getRotationLevel(stack) == rotation) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int getRotationLevel(ItemStack stack) {
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_1_POWER.get())) return 1;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_2_SATIETY.get())) return 2;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_3_NIGHT_VISION.get())) return 3;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_4_REGEN.get())) return 4;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_5_HEALTH.get())) return 5;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_6_RESISTANCE.get())) return 6;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_7_UNDYING.get())) return 7;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_8_LUCK.get())) return 8;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_9_IMMORTAL.get())) return 9;
        if (stack.is(com.jiuzhuan.item.ModItems.ROTATION_10_ADAPTATION.get())) return 10;
        return 0;
    }
}
