package com.jiuzhuan.client;

import com.jiuzhuan.network.NetworkHandler;
import com.jiuzhuan.network.ToggleNightVisionPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端Forge总线事件处理器
 * 处理按键输入，打开HUD设置屏幕
 */
@Mod.EventBusSubscriber(modid = "nine_turn_ring", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 检测打开HUD配置按键
        while (KeyBindings.OPEN_HUD_CONFIG.consumeClick()) {
            mc.setScreen(new HudPositionScreen());
        }
        // 检测打开适应详情界面按键
        while (KeyBindings.OPEN_ADAPTATION_SCREEN.consumeClick()) {
            mc.setScreen(new AdaptationScreen());
        }
        // 检测切换三转夜视按键
        while (KeyBindings.TOGGLE_NIGHT_VISION.consumeClick()) {
            NetworkHandler.INSTANCE.sendToServer(new ToggleNightVisionPacket());
        }
    }
}
