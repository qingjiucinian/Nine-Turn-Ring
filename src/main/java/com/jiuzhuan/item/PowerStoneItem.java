package com.jiuzhuan.item;

/**
 * 一转·屠戮石 - 装备到轮转槽位激活1转力量效果
 * tooltip 由父类 RotationItem 根据 rotationLevel=1 统一生成
 */
public class PowerStoneItem extends RotationItem {
    public PowerStoneItem(Properties properties) {
        super(1, "一转·屠戮", properties);
    }
}
