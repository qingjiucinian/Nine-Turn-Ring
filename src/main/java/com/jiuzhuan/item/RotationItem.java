package com.jiuzhuan.item;

import com.jiuzhuan.capability.IPlayerData;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.client.AdaptationScreen;
import com.jiuzhuan.client.RotationOverlay;
import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.util.AdvancementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 转生物品通用类 - 装备到轮转槽后立即激活对应转生效果
 */
public class RotationItem extends Item implements ICurioItem {
    private final int rotationLevel; // 1-10
    private final String rotationName;

    public RotationItem(int rotationLevel, String rotationName, Properties properties) {
        super(properties);
        this.rotationLevel = rotationLevel;
        this.rotationName = rotationName;
    }

    public int getRotationLevel() {
        return rotationLevel;
    }

    // 各转主题颜色（索引1-10对应一转到十转）
    private static final String[] ROTATION_COLORS = {
            "", "§c", "§6", "§9", "§d", "§5", "§3", "§4", "§a", "§e", "§f"
    };

    // 彩虹渐变色（RGB），用于九转名称的彩色流转效果
    private static final int[] RAINBOW_COLORS = {
            0xFF0000, // 红
            0xFF8800, // 橙
            0xFFFF00, // 黄
            0x00FF00, // 绿
            0x00FFFF, // 青
            0x0088FF, // 蓝
            0x8800FF, // 紫
            0xFF00FF  // 粉
    };

    /**
     * 颜色线性插值：在 c1 和 c2 之间按 t(0~1) 平滑过渡
     */
    private static int lerpColor(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 覆盖物品名称颜色，根据转生等级显示对应效果
     * 七~九转：同色系正弦平滑呼吸（无生硬跳变）
     * 十转：彩虹流转 + 柔和亮度呼吸
     */
    @Override
    public Component getName(ItemStack stack) {
        String name = super.getName(stack).getString();
        long time = System.currentTimeMillis();
        if (rotationLevel == 7) {
            float t = (float) (0.5 + 0.5 * Math.sin(time / 1200.0 * Math.PI * 2));
            int color = lerpColor(0xAA0000, 0xFF5555, t);
            return Component.literal(name).withStyle(s -> s.withColor(TextColor.fromRgb(color)));
        }
        if (rotationLevel == 8) {
            float t = (float) (0.5 + 0.5 * Math.sin(time / 1200.0 * Math.PI * 2));
            int color = lerpColor(0x00AA00, 0x55FF55, t);
            return Component.literal(name).withStyle(s -> s.withColor(TextColor.fromRgb(color)));
        }
        if (rotationLevel == 9) {
            float t = (float) (0.5 + 0.5 * Math.sin(time / 1200.0 * Math.PI * 2));
            int color = lerpColor(0xFFAA00, 0xFFFF88, t);
            return Component.literal(name).withStyle(s -> s.withColor(TextColor.fromRgb(color)));
        }
        if (rotationLevel == 10) {
            MutableComponent result = Component.literal("");
            int offset = (int) ((time / 250) % RAINBOW_COLORS.length);
            float flashT = (float) (0.5 + 0.5 * Math.sin(time / 600.0 * Math.PI * 2));
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                int rainbow = RAINBOW_COLORS[(i + offset) % RAINBOW_COLORS.length];
                int color = lerpColor(rainbow, 0xFFFFFF, flashT * 0.6f);
                result.append(Component.literal(String.valueOf(c))
                        .withStyle(s -> s.withColor(TextColor.fromRgb(color))));
            }
            return result;
        }
        String color = (rotationLevel >= 1 && rotationLevel <= 10) ? ROTATION_COLORS[rotationLevel] : "§f";
        return Component.literal(color + name);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFireResistant() {
        return true;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                data.setActivated(rotationLevel, true);
                data.syncToClient(player);
            });
            if (player instanceof ServerPlayer sp && rotationLevel >= 1 && rotationLevel <= 10) {
                AdvancementUtil.grant(sp, "rotation_" + rotationLevel, "rot" + rotationLevel);
            }
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                data.setActivated(rotationLevel, false);
                data.syncToClient(player);
            });
        }
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().contains("rotation");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Player clientPlayer = Minecraft.getInstance().player;
        IPlayerData data = null;
        if (clientPlayer != null) {
            data = clientPlayer.getCapability(PlayerDataProvider.PLAYER_DATA).resolve().orElse(null);
        }

        switch (rotationLevel) {
            case 1:
                tooltip.add(Component.translatable("nine_turn_ring.rotation.1.title"));
                double rot1Per = ServerConfig.getRot1DamagePerKill() * 100;
                double rot1Cap = ServerConfig.getRot1DamageCap();
                String rot1Desc = "§7每击杀一个生物，最终伤害永久增加" + String.format("%.0f", rot1Per) + "%";
                if (rot1Cap > 0) rot1Desc += "（上限" + String.format("%.0f", rot1Cap * 100) + "%）";
                tooltip.add(Component.literal(rot1Desc));
                if (data != null) {
                    double bonus = data.getPowerDamageBonus() * 100;
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.translatable("nine_turn_ring.rotation.1.bonus", String.format("%.1f", bonus)));
                    tooltip.add(Component.translatable("nine_turn_ring.rotation.1.kills", data.getPowerKillCount()));
                }
                break;
            case 2:
                tooltip.add(Component.translatable("nine_turn_ring.rotation.2.title"));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.2.desc"));
                break;
            case 3:
                tooltip.add(Component.translatable("nine_turn_ring.rotation.3.title"));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.3.desc"));
                break;
            case 4:
                tooltip.add(Component.translatable("nine_turn_ring.rotation.4.title"));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.4.desc"));
                break;
            case 5:
                tooltip.add(Component.translatable("nine_turn_ring.rotation.5.title"));
                double rot5Per = ServerConfig.getRot5HealthPerKill() * 100;
                double rot5Cap = ServerConfig.getRot5HealthCap();
                String rot5Desc = "§7每击杀一个生物，最大生命值永久增加" + String.format("%.0f", rot5Per) + "%";
                if (rot5Cap > 0) rot5Desc += "（上限" + String.format("%.0f", rot5Cap * 100) + "%）";
                tooltip.add(Component.literal(rot5Desc));
                if (data != null) {
                    double bonus = data.getHealthBonus() * 100;
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.translatable("nine_turn_ring.rotation.5.bonus", String.format("%.1f", bonus)));
                    tooltip.add(Component.translatable("nine_turn_ring.rotation.5.kills", data.getHealthKillCount()));
                }
                break;
            case 6:
                tooltip.add(Component.translatable("nine_turn_ring.rotation.6.title"));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.6.desc"));
                break;
            case 7:
                float t7 = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1200.0 * Math.PI * 2));
                int color7 = lerpColor(0xAA0000, 0xFF5555, t7);
                String title7 = Component.translatable("nine_turn_ring.rotation.7.title").getString();
                tooltip.add(Component.literal(title7).withStyle(s -> s.withColor(TextColor.fromRgb(color7))));
                double rot7Heal = ServerConfig.getRot7HealRatio() * 100;
                int rot7Inv = ServerConfig.getRot7InvincibleSeconds();
                int rot7Cd = ServerConfig.getRot7CooldownSeconds();
                String rot7Desc = "§7受到致命伤害时不会死亡，恢复" + String.format("%.0f", rot7Heal) + "%最大生命值并获得" + rot7Inv + "秒无敌，冷却" + rot7Cd + "秒";
                tooltip.add(Component.literal(rot7Desc));
                if (data != null) {
                    long now = System.currentTimeMillis();
                    tooltip.add(Component.literal(""));
                    if (data.isInvincible(now)) {
                        double remain = (data.getInvincibleEnd() - now) / 1000.0;
                        tooltip.add(Component.translatable("nine_turn_ring.rotation.7.invincible", String.format("%.1f", remain)));
                    }
                    if (data.isInCooldown(now)) {
                        double remain = (data.getUndyingCooldownEnd() - now) / 1000.0;
                        tooltip.add(Component.translatable("nine_turn_ring.rotation.7.cooldown", String.format("%.1f", remain)));
                    } else {
                        tooltip.add(Component.translatable("nine_turn_ring.rotation.7.ready"));
                    }
                }
                break;
            case 8:
                float t8 = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1200.0 * Math.PI * 2));
                int color8 = lerpColor(0x00AA00, 0x55FF55, t8);
                String title8 = Component.translatable("nine_turn_ring.rotation.8.title").getString();
                tooltip.add(Component.literal(title8).withStyle(s -> s.withColor(TextColor.fromRgb(color8))));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.8.desc"));
                break;
            case 9:
                float t9 = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1200.0 * Math.PI * 2));
                int color9 = lerpColor(0xFFAA00, 0xFFFF88, t9);
                String title9 = Component.translatable("nine_turn_ring.rotation.9.title").getString();
                tooltip.add(Component.literal(title9).withStyle(s -> s.withColor(TextColor.fromRgb(color9))));
                double rot9Min = ServerConfig.getRot9MinHealth();
                String rot9Desc = "§7生命值最低只会降到" + String.format("%.0f", rot9Min) + "点";
                tooltip.add(Component.literal(rot9Desc));
                break;
            case 10:
                String sub10 = Component.translatable("nine_turn_ring.rotation.10.title").getString();
                MutableComponent rainbowSub = Component.literal("");
                int off10 = (int) ((System.currentTimeMillis() / 250) % RAINBOW_COLORS.length);
                for (int i = 0; i < sub10.length(); i++) {
                    final int idx = i;
                    rainbowSub.append(Component.literal(String.valueOf(sub10.charAt(idx)))
                            .withStyle(s -> s.withColor(TextColor.fromRgb(RAINBOW_COLORS[(idx + off10) % RAINBOW_COLORS.length]))));
                }
                tooltip.add(rainbowSub);
                double rot10Per = ServerConfig.getRot10ReductionPerStack() * 100;
                int rot10Max = ServerConfig.getRot10MaxStacks();
                int rot10Cd = ServerConfig.getRot10StackCooldownSeconds();
                String rot10Desc1 = "§7受到某类伤害后，对该类型减伤叠加" + String.format("%.0f", rot10Per) + "%，最高" + String.format("%.0f", rot10Max * rot10Per) + "%免疫，同类伤害" + rot10Cd + "秒内只能叠一次；适应三种伤害类型后解锁飞行";
                tooltip.add(Component.literal(rot10Desc1));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.10.desc2"));
                if (data != null) {
                    Map<String, Integer> levels = data.getAllAdaptationLevels();
                    Map<String, Long> times = data.getAllAdaptationTimes();
                    long now = System.currentTimeMillis();
                    List<String> completed = new ArrayList<>();
                    List<String> progressing = new ArrayList<>();
                    for (Map.Entry<String, Integer> e : levels.entrySet()) {
                        String type = RotationOverlay.getDamageTypeName(e.getKey());
                        int lvl = e.getValue();
                        double reduction = data.getAdaptationReduction(e.getKey()) * 100;
                        if (lvl >= ServerConfig.getRot10MaxStacks()) {
                            completed.add(type + Component.translatable("nine_turn_ring.rotation.10.reduction_100").getString());
                        } else {
                            Long lastTime = times.get(e.getKey());
                            String cdStatus;
                            long cdMs = (long) ServerConfig.getRot10StackCooldownSeconds() * 1000L;
                            if (lastTime != null && now - lastTime < cdMs) {
                                long remain = (cdMs - (now - lastTime)) / 1000;
                                cdStatus = Component.translatable("nine_turn_ring.rotation.10.stack_cooldown", remain).getString();
                            } else {
                                cdStatus = Component.translatable("nine_turn_ring.rotation.10.stackable").getString();
                            }
                            progressing.add(type + "（" + lvl + "/" + ServerConfig.getRot10MaxStacks() + "，" + String.format("%.0f", reduction) + "%）- " + cdStatus);
                        }
                    }
                    tooltip.add(Component.literal(""));
                    if (!completed.isEmpty()) {
                        tooltip.add(Component.translatable("nine_turn_ring.rotation.10.completed"));
                        for (String s : completed) {
                            tooltip.add(Component.literal("§a  " + s));
                        }
                    }
                    if (!progressing.isEmpty()) {
                        if (!completed.isEmpty()) tooltip.add(Component.literal(""));
                        tooltip.add(Component.translatable("nine_turn_ring.rotation.10.adapting"));
                        for (String s : progressing) {
                            tooltip.add(Component.literal("§e  " + s));
                        }
                    }
                    if (completed.isEmpty() && progressing.isEmpty()) {
                        tooltip.add(Component.translatable("nine_turn_ring.rotation.10.no_damage"));
                    }
                    // 负面效果适应详情
                    Map<String, Integer> effectLevels = data.getAllEffectAdaptationLevels();
                    Map<String, Long> effectTimes = data.getAllEffectAdaptationTimes();
                    List<String> effectCompleted = new ArrayList<>();
                    List<String> effectProgressing = new ArrayList<>();
                    for (Map.Entry<String, Integer> e : effectLevels.entrySet()) {
                        String effectName = AdaptationScreen.getEffectName(e.getKey());
                        int lvl = Math.min(e.getValue(), 5);
                        if (lvl <= 0) continue;
                        if (lvl >= 5) {
                            effectCompleted.add(effectName + Component.translatable("nine_turn_ring.rotation.10.full_immunity").getString());
                        } else {
                            Long lastTime = effectTimes.get(e.getKey());
                            String cdStatus;
                            if (lastTime != null && now - lastTime < 3000) {
                                long remain = (3000 - (now - lastTime)) / 1000;
                                cdStatus = Component.translatable("nine_turn_ring.rotation.10.stack_cooldown", remain).getString();
                            } else {
                                cdStatus = Component.translatable("nine_turn_ring.rotation.10.stackable").getString();
                            }
                            effectProgressing.add(effectName + "（" + lvl + "/5）- " + cdStatus);
                        }
                    }
                    if (!effectCompleted.isEmpty() || !effectProgressing.isEmpty()) {
                        tooltip.add(Component.literal(""));
                        tooltip.add(Component.translatable("nine_turn_ring.rotation.10.effect_header"));
                        for (String s : effectCompleted) {
                            tooltip.add(Component.literal("§a  " + s));
                        }
                        for (String s : effectProgressing) {
                            tooltip.add(Component.literal("§e  " + s));
                        }
                    }
                }
                break;
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
