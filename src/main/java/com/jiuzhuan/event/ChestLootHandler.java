package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.level.block.ChestBlock;

public class ChestLootHandler {

    // 玩家第一次打开箱子时，必定放入一转·屠戮石
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // 检查是否是箱子（普通箱子、陷阱箱、末影箱不算）
        if (!(state.getBlock() instanceof ChestBlock)) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 每个玩家仅第一次开箱子时触发
            if (data.hasOpenedFirstChest()) return;
            // 1转未激活时才给（避免重复）
            if (data.isActivated(1)) {
                data.setOpenedFirstChest(true);
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChestBlockEntity chest) {
                // 往箱子里添加力量石
                ItemStack powerStone = new ItemStack(ModItems.ROTATION_1_POWER.get());
                // 找一个空槽位放入
                boolean inserted = false;
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    if (chest.getItem(i).isEmpty()) {
                        chest.setItem(i, powerStone);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) {
                    // 箱子满了，掉在箱子前
                    player.spawnAtLocation(powerStone);
                }

                data.setOpenedFirstChest(true);
                data.syncToClient(player);
                player.sendSystemMessage(Component.translatable("nine_turn_ring.message.chest_hint"));
            }
        });
    }
}
