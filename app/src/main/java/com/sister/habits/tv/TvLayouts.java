package com.sister.habits.tv;

import android.content.Context;
import android.content.res.Configuration;

import com.sister.habits.R;
import com.sister.habits.utils.PinHelper;

/**
 * 布局运行时选择器（单 APK 化后新增，2026-09-25）。
 * 背景：合并为单 APK 后，src/main/res/layout/ 下同名布局不再能被 flavor 覆盖。
 * 故 TV 布局改为独立文件名（xxx_tv），由本类在运行时决定用哪一个。
 * 判定依据：PinHelper.isTvMode() = 设备本身是电视 或 家长手动开启 TV 模式。
 *
 * v4.1.1：新增商城页布局选择（shopLayout / shopItemLayout）。
 */
public final class TvLayouts {

    private TvLayouts() {
    }

    /** 背单词页布局：TV 横屏双栏 / 手机竖屏单列 */
    public static int wordLayout(Context ctx) {
        return isTv(ctx) ? R.layout.fragment_word_tv : R.layout.fragment_word;
    }

    /** 孩子端首页：TV 版替换了快捷入口（含看电视） */
    public static int childLayout(Context ctx) {
        return isTv(ctx) ? R.layout.activity_child_tv : R.layout.activity_child;
    }

    /** v4.1.1：商城页布局——TV 放大版（大分类按钮 / 大卡商品） */
    public static int shopLayout(Context ctx) {
        return isTv(ctx) ? R.layout.fragment_shop_tv : R.layout.fragment_shop;
    }

    /** v4.1.1：商城商品卡布局——TV 大卡版 */
    public static int shopItemLayout(Context ctx) {
        return isTv(ctx) ? R.layout.item_shop_tv : R.layout.item_shop;
    }

    /** 当前是否应使用 TV 布局 */
    public static boolean isTv(Context ctx) {
        try {
            if (PinHelper.isTvMode(ctx)) return true;
        } catch (Throwable ignored) {
        }
        try {
            return (ctx.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
        } catch (Throwable e) {
            return false;
        }
    }
}
