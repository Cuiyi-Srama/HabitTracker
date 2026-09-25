package com.sister.habits.tv;

import android.content.Context;
import android.content.res.Configuration;

import com.sister.habits.R;
import com.sister.habits.utils.PinHelper;

/**
 * \u5e03\u5c40\u8fd0\u884c\u65f6\u9009\u62e9\u5668\uff08\u5355 APK \u5316\u540e\u65b0\u589e\uff0c2026-09-25\uff09\u3002
 *
 * \u80cc\u666f\uff1a
 *   \u5408\u5e76\u4e3a\u5355 APK \u540e\uff0c src/main/res/layout/ \u4e0b\u540c\u540d\u5e03\u5c40\u4e0d\u518d\u80fd\u88ab flavor \u8986\u76d6\u3002
 *   \u6545 TV \u5e03\u5c40\u6539\u4e3a\u72ec\u7acb\u6587\u4ef6\u540d\uff08xxx_tv\uff09\uff0c\u7531\u672c\u7c7b\u5728\u8fd0\u884c\u65f6\u51b3\u5b9a\u7528\u54ea\u4e00\u4e2a\u3002
 *
 * \u5224\u5b9a\u4f9d\u636e\uff1aPinHelper.isTvMode()\n *   =\u300c\u8bbe\u5907\u672c\u8eab\u662f\u7535\u89c6\u300d(UI_MODE_TYPE_TELEVISION) \u6216 \u300c\u5bb6\u957f\u624b\u52a8\u5f00\u542f TV \u6a21\u5f0f\u300d\u3002
 */
public final class TvLayouts {

    private TvLayouts() {}

    /** \u80cc\u5355\u8bcd\u9875\u5e03\u5c40\uff1aTV \u6a2a\u5c4f\u53cc\u680f / \u624b\u673a\u7ad6\u5c4f\u5355\u5217 */
    public static int wordLayout(Context ctx) {
        return isTv(ctx) ? R.layout.fragment_word_tv : R.layout.fragment_word;
    }

    /** \u5b69\u5b50\u7aef\u9996\u9875\uff1aTV \u7248\u5345\u6362\u4e86\u5feb\u6377\u5165\u53e3\uff08\u542b\u770b\u7535\u89c6\uff09 */
    public static int childLayout(Context ctx) {
        return isTv(ctx) ? R.layout.activity_child_tv : R.layout.activity_child;
    }

    /** \u5f53\u524d\u662f\u5426\u5e94\u4f7f\u7528 TV \u5e03\u5c40 */
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
