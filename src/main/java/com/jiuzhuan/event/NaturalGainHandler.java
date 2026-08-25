package com.jiuzhuan.event;

import com.jiuzhuan.capability.IPlayerData;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.item.ModItems;
import com.jiuzhuan.util.AdvancementUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class NaturalGainHandler {
    private static ServerLevel pendingRot9Level = null;
    private static int pendingRot9Timer = -1;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingRot9Timer > 0) {
            pendingRot9Timer--;
            if (pendingRot9Timer == 0 && pendingRot9Level != null) {
                spawnFloatingRot9(pendingRot9Level, null);
                pendingRot9Level = null;
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (data.hasNatallyGotRot3()) return;
            if (isDarkEnvironment(player)) {
                data.setLastDamageInDark(true);
            }
        });
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        if (entity instanceof Player player) {
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                DamageSource source = event.getSource();
                boolean needSync = false;

                // 二转：饥饿死亡
                if (!data.hasNatallyGotRot2() && isStarvationDeath(source)) {
                    data.addHungerDeath();
                    if (data.getHungerDeathCount() >= 1) {
                        giveItemToPlayer(player, ModItems.ROTATION_2_SATIETY.get().getDefaultInstance(),
                                "item.nine_turn_ring.rotation_2_satiety");
                        data.setNatallyGotRot2(true);
                    } else {
                        player.sendSystemMessage(Component.translatable("nine_turn_ring.message.two_progress",
                                data.getHungerDeathCount()));
                    }
                    needSync = true;
                }

                // 三转：黑暗/低亮度死亡
                if (!data.hasNatallyGotRot3() && (data.isLastDamageInDark() || isDarkEnvironment(player))) {
                    data.addDarkDeath();
                    data.setLastDamageInDark(false);
                    if (data.getDarkDeathCount() >= 3) {
                        giveItemToPlayer(player, ModItems.ROTATION_3_NIGHT_VISION.get().getDefaultInstance(),
                                "item.nine_turn_ring.rotation_3_night_vision");
                        data.setNatallyGotRot3(true);
                    } else {
                        player.sendSystemMessage(Component.translatable("nine_turn_ring.message.three_progress",
                                data.getDarkDeathCount()));
                    }
                    needSync = true;
                }

                // 四转：中毒/凋灵状态死亡
                if (!data.hasNatallyGotRot4() && isPoisonWitherDeath(player)) {
                    data.addPoisonDeath();
                    if (data.getPoisonDeathCount() >= 4) {
                        giveItemToPlayer(player, ModItems.ROTATION_4_REGEN.get().getDefaultInstance(),
                                "item.nine_turn_ring.rotation_4_regen");
                        data.setNatallyGotRot4(true);
                    } else {
                        player.sendSystemMessage(Component.translatable("nine_turn_ring.message.four_progress",
                                data.getPoisonDeathCount()));
                    }
                    needSync = true;
                }

                // 六转：魔法/虚空/真实伤害死亡
                if (!data.hasNatallyGotRot6() && isMagicVoidTrueDamage(source)) {
                    data.addMagicDeath();
                    if (data.getMagicDeathCount() >= 3) {
                        giveItemToPlayer(player, ModItems.ROTATION_6_RESISTANCE.get().getDefaultInstance(),
                                "item.nine_turn_ring.rotation_6_resistance");
                        data.setNatallyGotRot6(true);
                    } else {
                        player.sendSystemMessage(Component.translatable("nine_turn_ring.message.six_progress",
                                data.getMagicDeathCount()));
                    }
                    needSync = true;
                }

                // 七转：不死图腾碎裂后45秒内彻底死亡才算一层
                // 有图腾死亡：图腾碎裂救回玩家（没死），只标记效果期，不叠层
                // 彻底死亡的叠层在 onPlayerRespawn 中处理（图腾救回不会触发复活事件）
                if (!data.hasNatallyGotRot7() && hasTotem(player)) {
                    long now = System.currentTimeMillis();
                    data.setTotemEffectActive(true);
                    data.setTotemEffectEndTime(now + 45000L);
                    if (!data.hasAnnouncedTotemBlessing()) {
                        player.sendSystemMessage(Component.translatable("nine_turn_ring.message.totem_blessing"));
                        data.setAnnouncedTotemBlessing(true);
                    }
                    needSync = true;
                }

                if (needSync) {
                    data.syncToClient(player);
                }
            });
        }

        // 五转：击杀凋灵（100%掉落）
        if (entity instanceof WitherBoss wither) {
            if (event.getSource().getEntity() instanceof Player killer) {
                killer.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                    if (!data.hasKilledWither()) {
                        data.setKilledWither(true);
                        ItemStack rot5 = new ItemStack(ModItems.ROTATION_5_HEALTH.get());
                        wither.spawnAtLocation(rot5);
                        killer.sendSystemMessage(Component.translatable("nine_turn_ring.message.five_wither"));
                        data.syncToClient(killer);
                    }
                });
            }
        }

        // 九转：首次击杀末影龙，延迟生成于龙蛋上方三格
        if (entity instanceof EnderDragon dragon) {
            if (event.getSource().getEntity() instanceof Player killer) {
                killer.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                    if (!data.hasKilledEnderDragon()) {
                        data.setKilledEnderDragon(true);
                        pendingRot9Level = (ServerLevel) dragon.level();
                        pendingRot9Timer = 100;
                        killer.sendSystemMessage(Component.translatable("nine_turn_ring.message.nine_dragon"));
                        data.syncToClient(killer);
                    }
                });
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onUseTotem(LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            long now = System.currentTimeMillis();
            data.setTotemEffectActive(true);
            data.setTotemEffectEndTime(now + 45000L);
            if (!data.hasNatallyGotRot7()) {
                player.sendSystemMessage(Component.translatable("nine_turn_ring.message.totem_blessing"));
            }
            data.syncToClient(player);
        });
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        List<ItemStack> drops = event.getDrops();
        if (drops == null || drops.isEmpty()) return;
        boolean isJunk = false;
        for (ItemStack stack : drops) {
            if (isJunkItem(stack)) { isJunk = true; break; }
        }
        if (!isJunk) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.hasNatallyGotRot8()) {
                data.addJunkFish();
                if (data.getJunkFishCount() >= 5) {
                    giveItemToPlayer(player, ModItems.ROTATION_8_LUCK.get().getDefaultInstance(),
                            "item.nine_turn_ring.rotation_8_luck");
                    data.setNatallyGotRot8(true);
                } else {
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.eight_progress",
                            data.getJunkFishCount()));
                }
                data.syncToClient(player);
            }
        });
    }


    /**
     * 七转：玩家彻底死亡后复活时检测图腾效果期，叠一层
     * 图腾救回玩家不会触发此事件，只有真正死亡后复活才会触发
     */
    @SubscribeEvent
    public void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (data.hasNatallyGotRot7()) return;
            long now = System.currentTimeMillis();
            if (data.isTotemEffectActive() && now < data.getTotemEffectEndTime()) {
                data.addTotemDeath();
                data.setTotemEffectActive(false);
                data.setAnnouncedTotemBlessing(false);
                if (data.getTotemDeathCount() >= 3) {
                    giveItemToPlayer(player, ModItems.ROTATION_7_UNDYING.get().getDefaultInstance(),
                            "item.nine_turn_ring.rotation_7_undying");
                    data.setNatallyGotRot7(true);
                } else {
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.seven_challenge",
                            data.getTotemDeathCount()));
                }
                data.syncToClient(player);
            } else if (data.isTotemEffectActive()) {
                data.setTotemEffectActive(false);
                data.setAnnouncedTotemBlessing(false);
                data.syncToClient(player);
            }
        });
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 十转：同时装备1-9转 + 完成所有进度
            if (!data.hasNatallyGotRot10() && data.isRingEquipped()) {
                boolean allEquipped = areAllRotationsEquipped(player, 1, 9);
                boolean allAdvancements = areAllAdvancementsDone((ServerPlayer) player);
                if (allEquipped && allAdvancements) {
                    giveItemToPlayer(player, ModItems.ROTATION_10_ADAPTATION.get().getDefaultInstance(),
                            "item.nine_turn_ring.rotation_10_adaptation");
                    data.setNatallyGotRot10(true);
                    AdvancementUtil.grant((ServerPlayer) player, "rotation_10", "rot10");
                    data.syncToClient(player);
                }
            }
            // 登神之路：适应类型达到5层
            if (data.isActivated(10)) {
                int adaptedTypes = countAdaptedTypes(data);
                if (adaptedTypes >= 5) {
                    if (!data.hasAdvancement("ascension")) {
                        AdvancementUtil.grant((ServerPlayer) player, "ascension", "ascend");
                        data.setAdvancement("ascension", true);
                        data.syncToClient(player);
                    }
                }
            }
        });
    }

    private void spawnFloatingRot9(Level level, BlockPos dragonPos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos eggPos = findDragonEggPos(serverLevel);
        if (eggPos == null) {
            eggPos = new BlockPos(0, 65, 0);
        }
        BlockPos spawnPos = eggPos.above(3);
        ItemStack item = new ItemStack(ModItems.ROTATION_9_IMMORTAL.get());
        ItemEntity entity = new ItemEntity(
                serverLevel,
                spawnPos.getX() + 0.5,
                spawnPos.getY() + 0.2,
                spawnPos.getZ() + 0.5,
                item
        );
        entity.setNoGravity(true);
        entity.setDeltaMovement(0, 0, 0);
        entity.setInvulnerable(true);
        entity.setPickUpDelay(0);
        entity.setCustomName(Component.translatable("item.nine_turn_ring.rotation_9_immortal")
                .withStyle(ChatFormatting.DARK_PURPLE));
        entity.setCustomNameVisible(false);
        serverLevel.addFreshEntity(entity);
    }

    private BlockPos findDragonEggPos(ServerLevel level) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 60; y <= 72; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.DRAGON_EGG)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean isStarvationDeath(DamageSource source) {
        String id = source.getMsgId();
        return id.equals("starve") || id.equals("starvation");
    }

    /**
     * 检测玩家是否持有不死图腾（主手或副手）
     */
    private boolean hasTotem(Player player) {
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private boolean isDarkEnvironment(Player player) {
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)) return true;
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)) return true;
        // getMaxLocalRawBrightness 自动考虑昼夜天空亮度变化（白天室外=15，夜晚/洞穴=方块光照）
        int lightLevel = player.level().getMaxLocalRawBrightness(player.blockPosition());
        return lightLevel < 3;
    }

    private boolean isPoisonWitherDeath(Player player) {
        return player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.WITHER);
    }

    private boolean isMagicVoidTrueDamage(DamageSource source) {
        String id = source.getMsgId();
        if (id.equals("outOfWorld")) return true;
        if (id.equals("magic") || id.equals("indirectMagic") || id.equals("thorns")
                || id.equals("dragonBreath") || id.equals("wither")) return true;
        if (id.equals("genericKill") || id.equals("generic")) return true;
        return false;
    }

    private boolean isJunkItem(ItemStack stack) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath();
        return path.equals("ink_sac")
                || path.equals("fishing_rod")
                || path.equals("bamboo")
                || path.equals("bowl")
                || path.equals("stick")
                || path.equals("string")
                || path.equals("bone")
                || path.equals("rotten_flesh")
                || path.equals("cocoa_beans")
                || path.equals("leather_boots")
                || path.equals("tripwire_hook")
                || path.equals("potion")
                || path.equals("nautilus_shell");
    }

    private boolean areAllRotationsEquipped(Player player, int from, int to) {
        try {
            var curiosOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isEmpty()) return false;
            var inv = curiosOpt.get();
            boolean[] found = new boolean[11];
            for (var entry : inv.getCurios().entrySet()) {
                var handler = entry.getValue();
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStacks().getStackInSlot(i);
                    int rot = getRotationLevelFromStack(stack);
                    if (rot >= 1 && rot <= 10) found[rot] = true;
                }
            }
            for (int i = from; i <= to; i++) {
                if (!found[i]) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
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

    private boolean areAllAdvancementsDone(ServerPlayer player) {
        return AdvancementUtil.isDone(player, "root")
                && AdvancementUtil.isDone(player, "rotation_1")
                && AdvancementUtil.isDone(player, "rotation_2")
                && AdvancementUtil.isDone(player, "rotation_3")
                && AdvancementUtil.isDone(player, "rotation_4")
                && AdvancementUtil.isDone(player, "rotation_5")
                && AdvancementUtil.isDone(player, "rotation_6")
                && AdvancementUtil.isDone(player, "rotation_7")
                && AdvancementUtil.isDone(player, "rotation_8")
                && AdvancementUtil.isDone(player, "rotation_9");
    }

    private int countAdaptedTypes(IPlayerData data) {
        int count = 0;
        for (int level : data.getAllAdaptationLevels().values()) {
            if (level >= 10) count++;
        }
        return count;
    }

    private void giveItemToPlayer(Player player, ItemStack stack, String itemTranslationKey) {
        if (!player.getInventory().add(stack)) {
            player.spawnAtLocation(stack);
        }
        player.sendSystemMessage(Component.translatable("nine_turn_ring.message.rotation_obtained",
                Component.translatable(itemTranslationKey)));
    }
}
