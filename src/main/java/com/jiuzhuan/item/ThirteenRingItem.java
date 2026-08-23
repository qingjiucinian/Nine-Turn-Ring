package com.jiuzhuan.item;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.util.AdvancementUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class ThirteenRingItem extends Item implements ICurioItem {
    public ThirteenRingItem(Properties properties) {
        super(properties);
    }

    // 附魔闪烁光泽
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // 不可摧毁：无法被任何伤害破坏（熔岩/仙人掌/爆炸/虚空等）
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFireResistant() {
        return true;
    }

    // 永恒绑定：非创造模式无法拆下
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return player.isCreative();
        }
        return false;
    }

    // 戒指无法被丢弃
    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    // 装备戒指时：开启10个轮转槽位（只在真正装备时调用）
    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            // 清除手动取下冷却，确保轮转物品能正常自动装备
            com.jiuzhuan.event.AccessoryProtectionHandler.clearManualUnequipCooldown(player.getUUID());
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                data.setRingEquipped(true);
                data.syncToClient(player);
            });
            // 授予"无敌之始"进度
            if (player instanceof ServerPlayer sp) {
                AdvancementUtil.grant(sp, "root", "equip_ring");
            }
            PlayerDataProvider.setRotationSlots(player, 10);
        }
    }

    // 卸下戒指时：物品弹回背包，关闭轮转槽位（只在真正卸下时调用）
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            // 清除饰品防护的手动取下冷却，确保重新装备戒指后轮转物品能正常自动装备
            com.jiuzhuan.event.AccessoryProtectionHandler.clearManualUnequipCooldown(player.getUUID());
            // 先把轮转槽里的物品弹回背包
            Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                ICuriosItemHandler inv = curiosOpt.get();
                inv.getCurios().forEach((identifier, handler) -> {
                    if ("rotation".equals(identifier)) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack slotStack = handler.getStacks().getStackInSlot(i);
                            if (!slotStack.isEmpty()) {
                                if (!player.getInventory().add(slotStack.copy())) {
                                    player.spawnAtLocation(slotStack.copy());
                                }
                                handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                            }
                        }
                    }
                });
            }
            // 清除所有激活状态和轮转饰品快照（避免重新装备戒指后快照恢复导致物品重复）
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                data.setRingEquipped(false);
                for (int i = 1; i <= 10; i++) {
                    data.setActivated(i, false);
                }
                // 清除所有rotation槽位的快照
                data.getAccessorySnapshot().keySet().removeIf(key -> key.startsWith("rotation:"));
                data.syncToClient(player);
            });
            // 关闭轮转槽位
            PlayerDataProvider.setRotationSlots(player, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("nine_turn_ring.ring.desc.bound"));
        tooltip.add(Component.translatable("nine_turn_ring.ring.desc.slots"));
        tooltip.add(Component.translatable("nine_turn_ring.ring.desc.protected"));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
