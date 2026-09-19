package com.jiuzhuan.item;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.util.AdvancementUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio.DropRule;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NineTurnRingItem extends Item implements ICurioItem {
    public NineTurnRingItem(Properties properties) {
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

    // 死亡保留：即使关闭死亡不掉落，九转戒也由 Curios 原生保留在戒指槽，不离槽、不掉落（对齐七咒之戒）
    @Override
    public DropRule getDropRule(SlotContext slotContext, DamageSource source, int lootingLevel, boolean recentlyHit, ItemStack stack) {
        return DropRule.ALWAYS_KEEP;
    }

    // 允许手持右键直接装备到戒指槽
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    // 戒指无法被丢弃
    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    // 七咒式永恒绑定：戒指一旦出现在玩家背包且尚未装备，自动装备回戒指槽。
    // 仅对九转戒生效（轮转物品不做自动装备）；创造/旁观模式跳过，否则创造模式取下后会被立刻拉回。
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (player.isCreative() || player.isSpectator()) return;
        // 已装备九转戒则不再处理，背包里的备用戒指原样保留，避免重复佩戴
        if (hasRingEquipped(player)) return;
        if (equipToEmptyRing(player, stack)) {
            stack.shrink(1);
        }
    }

    // 永恒绑定：禁止附上消失诅咒（否则死亡时戒指会消失，破坏绑定），对齐七咒之戒
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(book);
        if (enchants.containsKey(Enchantments.VANISHING_CURSE)) return false;
        return super.isBookEnchantable(stack, book);
    }

    // 检查玩家戒指槽是否已装备九转戒
    private boolean hasRingEquipped(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve().map(inv -> {
            for (var entry : inv.getCurios().entrySet()) {
                String id = entry.getKey();
                if (!id.equals("ring") && !id.endsWith(":ring")) continue;
                var handler = entry.getValue();
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (handler.getStacks().getStackInSlot(i).getItem() instanceof NineTurnRingItem) {
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }

    // 把一枚九转戒放入第一个空的戒指槽（保留NBT），成功返回true
    private boolean equipToEmptyRing(Player player, ItemStack source) {
        return CuriosApi.getCuriosInventory(player).resolve().map(inv -> {
            for (var entry : inv.getCurios().entrySet()) {
                String id = entry.getKey();
                if (!id.equals("ring") && !id.endsWith(":ring")) continue;
                var handler = entry.getValue();
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (handler.getStacks().getStackInSlot(i).isEmpty()) {
                        ItemStack equipped = source.copy();
                        equipped.setCount(1);
                        handler.getStacks().setStackInSlot(i, equipped);
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
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
