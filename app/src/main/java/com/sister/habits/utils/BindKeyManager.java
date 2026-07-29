package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Key绑定管理器
 * - 孩子端：生成Child Key + 查看已绑定家长
 * - 家长端：生成Parent Key + 输入Child Key绑定孩子
 * Key格式: HABIT-C-XXXX (孩子) / HABIT-P-XXXX (家长)
 */
public class BindKeyManager {
    private static final String PREFS = "bind_keys";
    private static final String KEY_CHILD = "child_key";
    private static final String KEY_PARENT = "parent_key";
    private static final String KEY_BOUND_PARENTS = "bound_parents";
    private static final String KEY_BOUND_CHILDREN = "bound_children";
    private static final String KEY_IS_PARENT = "is_parent_mode";

    /** 生成Child Key */
    public static String generateChildKey(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_CHILD, null);
        if (existing != null) return existing;
        String key = "HABIT-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        prefs.edit().putString(KEY_CHILD, key).apply();
        return key;
    }

    /** 生成Parent Key */
    public static String generateParentKey(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_PARENT, null);
        if (existing != null) return existing;
        String key = "HABIT-P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        prefs.edit().putString(KEY_PARENT, key).putBoolean(KEY_IS_PARENT, true).apply();
        return key;
    }

    /** 获取Child Key（不自动生成） */
    public static String getChildKey(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHILD, null);
    }

    /** 获取Parent Key */
    public static String getParentKey(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PARENT, null);
    }

    /** 家长端：绑定一个孩子（输入孩子端的Child Key） */
    public static boolean bindChild(Context ctx, String childKey) {
        if (childKey == null || !childKey.startsWith("HABIT-C-")) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> children = new HashSet<>(prefs.getStringSet(KEY_BOUND_CHILDREN, new HashSet<>()));
        if (children.contains(childKey)) return false; // 已绑定
        children.add(childKey);
        prefs.edit().putStringSet(KEY_BOUND_CHILDREN, children).apply();
        return true;
    }

    /** 孩子端：绑定一个家长（输入家长端的Parent Key） */
    public static boolean bindParent(Context ctx, String parentKey) {
        if (parentKey == null || !parentKey.startsWith("HABIT-P-")) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> parents = new HashSet<>(prefs.getStringSet(KEY_BOUND_PARENTS, new HashSet<>()));
        if (parents.contains(parentKey)) return false;
        parents.add(parentKey);
        prefs.edit().putStringSet(KEY_BOUND_PARENTS, parents).apply();
        return true;
    }

    /** 获取已绑定的孩子列表 */
    public static Set<String> getBoundChildren(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_BOUND_CHILDREN, new HashSet<>());
    }

    /** 获取已绑定的家长列表 */
    public static Set<String> getBoundParents(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_BOUND_PARENTS, new HashSet<>());
    }

    /** 解绑孩子 */
    public static void unbindChild(Context ctx, String childKey) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> children = new HashSet<>(prefs.getStringSet(KEY_BOUND_CHILDREN, new HashSet<>()));
        children.remove(childKey);
        prefs.edit().putStringSet(KEY_BOUND_CHILDREN, children).apply();
    }

    /** 解绑家长 */
    public static void unbindParent(Context ctx, String parentKey) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> parents = new HashSet<>(prefs.getStringSet(KEY_BOUND_PARENTS, new HashSet<>()));
        parents.remove(parentKey);
        prefs.edit().putStringSet(KEY_BOUND_PARENTS, parents).apply();
    }

    /** 家长审批时验证：检查当前家长Key是否存在 */
    public static boolean verifyParentKey(Context ctx) {
        String parentKey = getParentKey(ctx);
        return parentKey != null && parentKey.startsWith("HABIT-P-");
    }

    /** 验证Child Key格式 */
    public static boolean isValidChildKey(String key) {
        return key != null && key.startsWith("HABIT-C-") && key.length() == 17;
    }

    /** 验证Parent Key格式 */
    public static boolean isValidParentKey(String key) {
        return key != null && key.startsWith("HABIT-P-") && key.length() == 17;
    }
}
