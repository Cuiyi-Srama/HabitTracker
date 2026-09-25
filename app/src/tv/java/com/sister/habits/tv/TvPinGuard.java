package com.sister.habits.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sister.habits.parent.ParentActivity;
import com.sister.habits.utils.PinHelper;

/**
 * TV 端家长界面守卫（步骤③）。
 *
 * 背景：
 *   手机上有指纹 + 系统锁屏可兜底，电视上两者皆无 ——
 *   若不加拦截，妹妹拿起遥控器即可进入家长界面改积分/改规则。
 *   故 TV 渠道必须在进入 ParentActivity 前强制校验应用 PIN。
 *
 * 设计：
 *   1. 不侵入 ParentActivity（它在 src/main，两个 flavor 共用）——
 *      改为在 TvApp 的 Activity 生命周期中统一拦截。
 *   2. TV 上只认「应用 PIN」：PinHelper.isSystemLockEnabled 在电视上恒不可靠，
 *      故 isAnyEnabled 在 TV 模式下已改为只看 use_app_pin。
 *   3. 校验通过后置一次性放行标记，避免 onResume 反复弹窗。
 *   4. 未设 PIN 时直接拒绝进入，并引导去设置（进入设置需先过 TV 看片页，不构成绕过）。
 */
public final class TvPinGuard {

    /** 本次放行的 Activity 弱引用标记（按 Activity hashCode 记录） */
    private static final java.util.Set<Integer> PASSED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());

    private TvPinGuard() {}

    /** 需要 PIN 保护的界面（家长管理入口） */
    public static boolean isProtected(Activity a) {
        return a instanceof ParentActivity;
    }

    /**
     * 在 onResume 时调用。若当前 Activity 需要保护且未放行，则弹 PIN。
     *
     * @return true 表示已通过（或无需保护），可正常使用；false 表示已发起验证流程
     */
    public static boolean check(Activity a) {
        if (!isProtected(a)) return true;
        if (PASSED.contains(System.identityHashCode(a))) return true;

        if (!PinHelper.isPinSet(a) || !PinHelper.isAppPinEnabled(a)) {
            showNeedSetupDialog(a);
            return false;
        }
        showPinDialog(a);
        return false;
    }

    /** 离开时清理标记，下次进入需重新验证 */
    public static void forget(Activity a) {
        PASSED.remove(System.identityHashCode(a));
    }

    private static void showNeedSetupDialog(final Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("\uD83D\uDD12 \u9700\u8981\u5148\u8bbe\u7f6e PIN \u7801")
                .setMessage("\u7535\u89c6\u4e0a\u6ca1\u6709\u6307\u7eb9\u4e5f\u6ca1\u6709\u7cfb\u7edf\u9501\u5c4f\uff0c\u5bb6\u957f\u754c\u9762\u5fc5\u987b\u7528 PIN \u7801\u4fdd\u62a4\u3002\n\n"
                        + "\u8bf7\u5148\u5728\u624b\u673a\u7248\u7684\u300c\u2699\ufe0f \u7cfb\u7edf\u8bbe\u7f6e \u2192 \uD83D\uDD10 \u5b89\u5168\u9632\u62a4\u300d\u4e2d\u8bbe\u7f6e PIN \u7801\u3002")
                .setCancelable(false)
                .setPositiveButton("\u77e5\u9053\u4e86", (d, w) -> a.finish())
                .show();
    }

    private static void showPinDialog(final Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 32, 48, 16);

        TextView tip = new TextView(a);
        tip.setText("\u8f93\u5165\u5bb6\u957f PIN \u7801");
        tip.setTextSize(20);
        tip.setTextColor(Color.parseColor("#333333"));
        box.addView(tip);

        final EditText et = new EditText(a);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("4~6 \u4f4d\u6570\u5b57");
        et.setTextSize(28);
        et.setGravity(Gravity.CENTER);
        et.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        et.setLayoutParams(lp);
        box.addView(et);

        final AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("\uD83D\uDD12 \u5bb6\u957f\u9a8c\u8bc1")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("\u786e\u5b9a", null)
                .setNegativeButton("\u8fd4\u56de", (d, w) -> a.finish())
                .create();
        dlg.show();

        // 覆盖正向按钮：校验失败不关闭对话框，避免“点一下就进去”的竞态
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = et.getText().toString().trim();
            if (PinHelper.verifyAndUpgrade(a, pin)) {
                PASSED.add(System.identityHashCode(a));
                dlg.dismiss();
            } else {
                et.setText("");
                et.setHint("PIN \u9519\u8bef\uff0c\u8bf7\u91cd\u8bd5");
                android.widget.Toast.makeText(a, "PIN \u7801\u9519\u8bef", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        et.requestFocus();
    }
}
