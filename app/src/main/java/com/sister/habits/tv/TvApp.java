package com.sister.habits.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.sister.habits.HabitApp;
import com.sister.habits.R;

/**
 * TV 渠道 Application。
 * 继承 {@link HabitApp}：数据库初始化、同步服务、LanSync 全部沿用手机版逻辑，零改动。
 * 额外注入两件事：
 * 1) 遥控器焦点适配（TvCompat）——让 D-pad 能选中列表项与按钮；
 * 2) 把孩子模式首页的「看电视」入口接上（该 id 仅存在于 TV 渠道的 activity_child 布局）。
 */
public class TvApp extends HabitApp {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                // ★ 2026-09-25 关键修正：
                //   TvApp 已从 src/tv 迁入 src/main，手机与电视共用。
                //   以下三件事（PIN 守卫 / 字号放大 / 看片入口）均为 TV 专属，
                //   若不加判定在手机上执行，会导致手机进不了家长界面、
                //   字体被意外放大。故先做电视设备硬判定。
                if (!TvPinGuard.isRealTv(activity)) {
                    return;
                }
                try {
                    // TV 上家长界面先行 PIN 守卫，未通过则不做其余处理
                    if (!TvPinGuard.check(activity)) {
                        return;
                    }
                    TvCompat.apply(activity);
                    bindTvEntries(activity);
                } catch (Throwable ignored) {
                }
            }

            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

            @Override
            public void onActivityDestroyed(Activity a) {
                // 离开时清理放行标记：下次进入需重新验证
                try {
                    TvPinGuard.forget(a);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void bindTvEntries(final Activity activity) {
        View watch = activity.findViewById(R.id.btn_tv_watch);
        if (watch != null) {
            watch.setOnClickListener(v -> {
                Intent i = new Intent(activity, TvVideoActivity.class);
                activity.startActivity(i);
            });
        }
    }
}