package com.sister.habits.utils;

import android.app.AlertDialog;
import android.content.Context;

/**
 * 菜单路由工具 — 消除手动 switch/case 索引错误
 * 
 * 用法：
 *   MenuHelper.show(this, "标题", new String[]{"选项1", "选项2"},
 *       () -> action1(),
 *       () -> action2()
 *   );
 */
public class MenuHelper {

    /** 显示菜单，自动绑定标签→动作 */
    public static void show(Context ctx, String title, String[] labels, Runnable... actions) {
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setItems(labels, (d, which) -> {
                    if (which >= 0 && which < actions.length && actions[which] != null) {
                        actions[which].run();
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> {})
                .show();
    }

    /** 显示菜单 + 自定义返回动作 */
    public static void showWithBack(Context ctx, String title, String[] labels, Runnable backAction, Runnable... actions) {
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setItems(labels, (d, which) -> {
                    if (which >= 0 && which < actions.length && actions[which] != null) {
                        actions[which].run();
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> backAction.run())
                .show();
    }
}
