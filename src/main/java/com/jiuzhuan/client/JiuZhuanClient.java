package com.jiuzhuan.client;

import com.jiuzhuan.JiuZhuanMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JiuZhuanMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class JiuZhuanClient {
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("rotation_overlay", RotationOverlay.OVERLAY);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_HUD_CONFIG);
        event.register(KeyBindings.OPEN_ADAPTATION_SCREEN);
        event.register(KeyBindings.TOGGLE_NIGHT_VISION);
    }
}
