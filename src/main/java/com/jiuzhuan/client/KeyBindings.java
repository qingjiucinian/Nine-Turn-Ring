package com.jiuzhuan.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定定义
 */
public class KeyBindings {
    public static final String CATEGORY = "key.categories.nine_turn_ring";

    /** 打开HUD位置设置屏幕 */
    public static final KeyMapping OPEN_HUD_CONFIG = new KeyMapping(
            "key.nine_turn_ring.open_hud_config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );
    /** 打开适应详情界面 */
    public static final KeyMapping OPEN_ADAPTATION_SCREEN = new KeyMapping(
            "key.nine_turn_ring.open_adaptation_screen",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );
    /** 切换三转夜视开关 */
    public static final KeyMapping TOGGLE_NIGHT_VISION = new KeyMapping(
            "key.nine_turn_ring.toggle_night_vision",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY
    );
}
