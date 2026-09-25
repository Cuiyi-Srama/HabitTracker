package com.sister.habits.tv;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.sister.habits.utils.PinHelper;

/**
 * TV 模式判定与入口守卫。
 *
 * 设计要点（2026-09-25）：
 *   1. 判据分两层：「设备本身是电视」或「家长在设置里手动开启 TV 模式」。
 *      后者用于在手机上预览/调试 TV 界面，不依赖真实电视硬件。
 *   2. 进入 TV 模式前强制要求已设置应用 PIN ——
 *      TV 无指纹、无系统锁屏，PIN 是唯一防线；未设 PIN 则 TV 形同裸奔。
 *   3. TV 模式一旦开启会写入持久标记，重启后仍生效（见 setForceTvMode）。
 */
public final class TvMode {

    private TvMode() {}

    /** 当前是否应走 TV 界面（设备是电视 OR 家长手动开启） */
    public static boolean isTv(Context ctx) {
        return PinHelper.isTvMode(ctx);
    }

    /**
     * 尝试打开 TV 模式，带 PIN 前置校验。
     *
     * @return true 表示已通过校验并启动；false 表示被拦截（调用方无需再处理）
     */
    public static boolean launch(Context ctx) {
        if (!PinHelper.isAppPinEnabled(ctx) || !PinHelper.isPinSet(ctx)) {
            Toast.makeText(ctx,
                    "\u26a0\ufe0f 请先设置应用PIN码：\nTV 上无指纹/无系统锁屏，PIN 是唯一防线",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        Intent i = new Intent(ctx, TvMainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return true;
    }

    /** 是否已满足进入 TV 模式的安全前提 */
    public static boolean isReady(Context ctx) {
        return PinHelper.isAppPinEnabled(ctx) && PinHelper.isPinSet(ctx);
    }

    /** 读一下当前生效的 TV 模式来源，用于设置界面展示 */
    public static String describe(Context ctx) {
        boolean forced = PinHelper.isTvMode(ctx) && isForcedOnly(ctx);
        if (isForcedOnly(ctx)) return "已手动开启";
        if (PinHelper.isTvMode(ctx)) return "设备识别为电视";
        return "未开启";
    }

    private static boolean isForcedOnly(Context ctx) {
        try {
            return ctx.getSharedPreferences("pin_security", Context.MODE_PRIVATE)
                    .getBoolean("force_tv_mode", false);
        } catch (Exception e) {
            return false;
        }
    }
}
