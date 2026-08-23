package com.jiuzhuan.client;

import com.jiuzhuan.JiuZhuanMod;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class RotationOverlay {
    private static final ItemStack[] ICON_STACKS = new ItemStack[11];
    static {
        ICON_STACKS[1] = new ItemStack(ModItems.ROTATION_1_POWER.get());
        ICON_STACKS[2] = new ItemStack(ModItems.ROTATION_2_SATIETY.get());
        ICON_STACKS[3] = new ItemStack(ModItems.ROTATION_3_NIGHT_VISION.get());
        ICON_STACKS[4] = new ItemStack(ModItems.ROTATION_4_REGEN.get());
        ICON_STACKS[5] = new ItemStack(ModItems.ROTATION_5_HEALTH.get());
        ICON_STACKS[6] = new ItemStack(ModItems.ROTATION_6_RESISTANCE.get());
        ICON_STACKS[7] = new ItemStack(ModItems.ROTATION_7_UNDYING.get());
        ICON_STACKS[8] = new ItemStack(ModItems.ROTATION_8_LUCK.get());
        ICON_STACKS[9] = new ItemStack(ModItems.ROTATION_9_IMMORTAL.get());
        ICON_STACKS[10] = new ItemStack(ModItems.ROTATION_10_ADAPTATION.get());
    }

    public static String getDamageTypeName(String id) {
        // 归一化：玩家爆炸与普通爆炸统一显示
        if (id.equals("explosion.player")) id = "explosion";
        String key = "nine_turn_ring.damage_type." + id;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? id : translated;
    }

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (mc.options.hideGui) return;
        if (!(mc.screen instanceof InventoryScreen)) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;

            int cols = 5;
            int slotSize = 18;
            int spacing = 2;
            int startX = HudConfig.getIconsX();
            int startY = HudConfig.getIconsY();
            double mouseX = mc.mouseHandler.xpos() * screenWidth / mc.getWindow().getWidth();
            double mouseY = mc.mouseHandler.ypos() * screenHeight / mc.getWindow().getHeight();

            for (int i = 1; i <= 10; i++) {
                int col = (i - 1) % cols;
                int row = (i - 1) / cols;
                int x = startX + col * (slotSize + spacing);
                int y = startY + row * (slotSize + spacing);
                boolean activated = data.isActivated(i);

                if (activated) {
                    graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x90FFD700);
                    graphics.renderItem(ICON_STACKS[i], x, y);
                    graphics.fill(x - 1, y - 1, x + 17, y, 0xFFFFD700);
                    graphics.fill(x - 1, y + 16, x + 17, y + 17, 0xFFFFD700);
                    graphics.fill(x - 1, y - 1, x, y + 17, 0xFFFFD700);
                    graphics.fill(x + 16, y - 1, x + 17, y + 17, 0xFFFFD700);
                } else {
                    graphics.setColor(0.55f, 0.55f, 0.55f, 0.8f);
                    graphics.renderItem(ICON_STACKS[i], x, y);
                    graphics.setColor(1f, 1f, 1f, 1f);
                }

                if (mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16) {
                    String status;
                    if (activated) {
                        if (i == 7) {
                            long now = System.currentTimeMillis();
                            if (data.isInvincible(now)) {
                                double invRemain = (data.getInvincibleEnd() - now) / 1000.0;
                                double cdRemain = (data.getUndyingCooldownEnd() - now) / 1000.0;
                                status = Component.translatable("nine_turn_ring.hud.invincible",
                                        String.format("%.1f", invRemain),
                                        String.format("%.1f", cdRemain)).getString();
                            } else if (data.isInCooldown(now)) {
                                double remain = (data.getUndyingCooldownEnd() - now) / 1000.0;
                                status = Component.translatable("nine_turn_ring.hud.cooldown",
                                        String.format("%.1f", remain)).getString();
                            } else {
                                status = Component.translatable("nine_turn_ring.hud.ready").getString();
                            }
                        } else {
                            status = Component.translatable("nine_turn_ring.hud.activated").getString();
                        }
                    } else {
                        status = Component.translatable("nine_turn_ring.hud.not_activated").getString();
                    }
                    String name = ICON_STACKS[i].getHoverName().getString();
                    graphics.renderTooltip(mc.font, Component.literal(name + " " + status), (int) mouseX, (int) mouseY);
                }
            }
        });
    };
}
