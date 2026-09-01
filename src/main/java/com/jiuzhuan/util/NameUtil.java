package com.jiuzhuan.util;

import net.minecraft.network.chat.Component;

/**
 * 通用名称翻译工具类
 * 客户端和服务端都可使用，避免服务端引用客户端类
 */
public class NameUtil {

    /**
     * 根据伤害类型ID获取翻译名称
     */
    public static String getDamageTypeName(String id) {
        // 归一化：玩家爆炸与普通爆炸统一显示
        if (id.equals("explosion.player")) id = "explosion";
        String key = "nine_turn_ring.damage_type." + id;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? id : translated;
    }

    /**
     * 根据效果ID获取翻译名称
     */
    public static String getEffectName(String id) {
        String shortName = id.contains(":") ? id.substring(id.indexOf(":") + 1) : id;
        String key = "nine_turn_ring.effect." + shortName;
        String translated = Component.translatable(key).getString();
        // 如果翻译不存在，返回原始短名
        return translated.equals(key) ? shortName : translated;
    }
}
