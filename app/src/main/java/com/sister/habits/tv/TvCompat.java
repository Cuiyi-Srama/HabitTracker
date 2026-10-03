package com.sister.habits.tv;

import android.app.Activity;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.sister.habits.R;

/**
 * TV 遥控器适配器。
 * 手机版 UI 的问题：大量控件是「可点击但不可聚焦」（clickable=true, focusable=false），
 * 触摸能用，遥控器 D-pad 直接跳过 → 电视上点不动。
 * 本类在 Activity 显示时遍历视图树，把这类控件补成可聚焦，并顺带放大字号便于远距离观看。
 * 操作幂等：重复调用不会反复放大字号。
 *
 * v4.1.1 新增：焦点增强——聚焦时「放大 1.08x + 阴影」，失焦还原；
 * 并关闭列表类容器的子视图裁剪，避免放大边缘被切。
 */
public final class TvCompat {

    /**
     * 字号放大倍率。
     *
     * ★ 2026-09-25 调整：1.30 → 1.15。
     *   原 1.30 在「固定高度按钮 + layout_weight 容器」混排的布局上会把容器撑溢出，
     *   导致权重区被挤到高度趋近 0（实测：TV 背单词页单词区完全空白）。
     */
    private static final float TEXT_SCALE = 1.15f;
    /** 放大后的字号上限（sp），避免标题被撑爆 */
    private static final float TEXT_MAX_SP = 34f;

    /** 已安装焦点增强的视图（防止重复安装；Weak 防泄漏） */
    private static final java.util.WeakHashMap<View, Boolean> FOCUS_DONE = new java.util.WeakHashMap<View, Boolean>();

    private TvCompat() {
    }

    public static void apply(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        View root = activity.getWindow().getDecorView();
        if (root == null) {
            return;
        }
        walk(root, root);
    }

    /**
     * 判断视图是否被标记为「跳过字号放大」。
     *
     * ★ 2026-09-25 修复：XML 中的 android:tag="xxx" 写入的是 getTag()（无 id 版），
     *   而原代码读的是 getTag(int key)（id 索引版）—— 两者是不同的存储槽，
     *   导致 tv_compat_skip 标记完全未生效，TV 背单词布局仍被 ×1.3 撑爆。
     *   现同时支持两种存储方式（字符串标记 / id 标记）。
     */
    private static boolean isSkipMarked(View v) {
        try {
            Object tag = v.getTag();
            if (tag instanceof String && "tv_compat_skip".equals(((String) tag).trim())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return Boolean.TRUE.equals(v.getTag(R.id.tv_compat_skip));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void walk(View v, View root) {
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            // RecyclerView（含 ViewPager2 内部）优先把焦点交给子项
            if (v instanceof RecyclerView) {
                group.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            } else {
                group.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            }
            // v4.1.1：列表/滚动容器不裁剪子视图，焦点放大不被切边
            if (v instanceof RecyclerView
                    || v instanceof android.widget.ScrollView
                    || v instanceof android.widget.HorizontalScrollView) {
                try {
                    group.setClipChildren(false);
                    group.setClipToPadding(false);
                } catch (Throwable ignored) {
                }
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(group.getChildAt(i), root);
            }
        }

        // 1) 可点击但不可聚焦 → 补上焦点，让遥控器能选中
        if (v.isClickable() && !v.isFocusable()) {
            v.setFocusable(true);
        }
        // 2) 列表/控件获得焦点时高亮（API 26+ 默认开启，这里显式打开）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.setDefaultFocusHighlightEnabled(true);
        }

        // ★ v4.1.1：焦点增强（聚焦放大 + 阴影；幂等安装）
        installFocusEffect(v);

        // 3) 放大字号（每视图只做一次）
        //    ★ 2026-09-25 修正：TV 专属布局（src/tv/res/layout/）已按大屏调好字号，
        //       若再被 ×1.3 会撑破紧凑布局（典型：背单词的 64sp 单词变 83sp 后溢出）。
        //       故支持 tv_compat_skip 标记，标记后跳过字号放大。
        boolean skipScale = isSkipMarked(v);
        if (v instanceof TextView && !skipScale) {
            TextView tv = (TextView) v;
            if (!Boolean.TRUE.equals(tv.getTag(R.id.tv_compat_flag))) {
                tv.setTag(R.id.tv_compat_flag, Boolean.TRUE);
                float density = tv.getResources().getDisplayMetrics().density;
                float px = tv.getTextSize();
                float sp = px / tv.getResources().getDisplayMetrics().scaledDensity;
                if (sp > 0f && sp < TEXT_MAX_SP) {
                    float target = Math.min(sp * TEXT_SCALE, TEXT_MAX_SP);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, target);
                }
            }
        }
    }

    /** v4.1.1：为可聚焦视图安装「聚焦放大 + 阴影」效果（幂等） */
    private static void installFocusEffect(final View v) {
        if (!v.isFocusable() && !v.isClickable()) {
            return;
        }
        if (FOCUS_DONE.containsKey(v)) {
            return;
        }
        FOCUS_DONE.put(v, Boolean.TRUE);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                try {
                    if (hasFocus) {
                        view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                        view.setElevation(10f * view.getResources().getDisplayMetrics().density);
                    } else {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        view.setElevation(0f);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
