package com.jiuzhuan.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * HUD位置配置 - 客户端配置
 * 保存图标HUD的位置坐标
 */
public class HudConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue ICONS_X;
    public static final ForgeConfigSpec.IntValue ICONS_Y;

    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "You can adjust the HUD position and scale of Nine Turn Ring in this config.",
                "你可以在此配置文件中调整九转戒的HUD位置和缩放.",
                "You can also adjust these in-game via the mod's config screen, no need to edit manually.",
                "你也可以在游戏内通过模组设置界面调整, 无需手动编辑此文件."
        ).push("hud_position");

        builder.push("icons");
        ICONS_X = builder
                .comment(
                        "X coordinate of the rotation icons HUD.",
                        "转生图标HUD的X坐标.",
                        "Range: 0 ~ 10000"
                )
                .defineInRange("x", 5, 0, 10000);
        ICONS_Y = builder
                .comment(
                        "Y coordinate of the rotation icons HUD.",
                        "转生图标HUD的Y坐标.",
                        "Range: 0 ~ 10000"
                )
                .defineInRange("y", 5, 0, 10000);
        builder.pop();

        builder.push("scale");
        HUD_SCALE = builder
                .comment(
                        "Overall scale of the HUD. (default 1.0)",
                        "HUD整体缩放. (默认1.0)",
                        "Range: 0.5 ~ 2.0"
                )
                .defineInRange("scale", 1.0, 0.5, 2.0);
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    public static int getIconsX() {
        try { return ICONS_X.get(); } catch (Exception e) { return 5; }
    }

    public static int getIconsY() {
        try { return ICONS_Y.get(); } catch (Exception e) { return 5; }
    }

    public static void setIconsPos(int x, int y) {
        ICONS_X.set(x);
        ICONS_Y.set(y);
    }

    public static double getHudScale() {
        try { return HUD_SCALE.get(); } catch (Exception e) { return 1.0; }
    }

    public static void setHudScale(double scale) {
        HUD_SCALE.set(scale);
    }

    public static void resetToDefault() {
        ICONS_X.set(5);
        ICONS_Y.set(5);
        HUD_SCALE.set(1.0);
    }
}
