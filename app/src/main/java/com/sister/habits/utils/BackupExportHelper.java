package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.Log;
import android.provider.MediaStore;
import android.os.Build;
import android.net.Uri;
import java.io.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 全量加密备份/恢复工具
 * 导出：SQLite全表JSON + 图片文件 + SharedPreferences → ZIP → AES-256-GCM → .habitbak
 * 导入：解密 → 解ZIP → 恢复DB + 还原图片 + 还原Preferences
 */
public class BackupExportHelper {
    private static final String TAG = "BackupExport";
    private static final String BACKUP_DIR = "/storage/emulated/0/Download";
    private static final String BACKUP_EXT = ".habitbak";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    /** 自动备份固定密码（文件名以 auto_ 开头的备份使用） */
    public static final String AUTO_PWD = "habit_auto_backup";
    private static final String PREFS_AUTO = "auto_backup_prefs";
    private static final String KEY_LAST_DATE = "last_auto_backup_date";
    /** 自动备份滚动上限：超过则删除最早的 */
    private static final int MAX_AUTO_BACKUPS = 10;
    private final Context context;
    private ProfileManager profile;

    public BackupExportHelper(Context context) {
        this.context = context.getApplicationContext();
        this.profile = ProfileManager.getInstance(context);
    }

    /** 全量导出加密备份 */
    public File exportBackup(String password) throws Exception {
        String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String deviceKey = DeviceIdentity.getDeviceKey(context);
        String shortKey = deviceKey != null && deviceKey.length() >= 8 ? deviceKey.substring(0, 8) : "UNKNOWN";
        File backupFile = new File(BACKUP_DIR, "HabitTracker_backup_" + shortKey + "_" + dateStr + BACKUP_EXT);

        // 1. 构建完整ZIP包（数据库JSON + 图片 + Preferences）
        byte[] zipData = buildFullZip();

        // 2. AES-256-GCM加密
        byte[] encrypted = encrypt(zipData, password);

        // 3. 写入文件
        FileOutputStream fos = new FileOutputStream(backupFile);
        fos.write(encrypted);
        fos.close();
        Log.i(TAG, "备份已导出: " + backupFile.getAbsolutePath() + " (" + backupFile.length() + " bytes)");
        return backupFile;
    }
    /** 生成默认备份文件名（用于SAF导出默认名） */
    public String generateDefaultFileName() {
        String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String deviceKey = DeviceIdentity.getDeviceKey(context);
        String shortKey = deviceKey != null && deviceKey.length() >= 8 ? deviceKey.substring(0, 8) : "UNKNOWN";
        return "HabitTracker_backup_" + shortKey + "_" + dateStr + BACKUP_EXT;
    }
    /** 生成加密备份字节流（用于SAF自定义位置导出） */
    public byte[] createEncryptedBackup(String password) throws Exception {
        byte[] zipData = buildFullZip();
        return encrypt(zipData, password);
    }
    /** 获取默认备份目录（用于界面提示） */
    public static String getDefaultBackupDir() {
        return BACKUP_DIR;
    }
    /** 获取备份扩展名 */
    public static String getBackupExt() {
        return BACKUP_EXT;
    }

    /** 从加密备份全量恢复 */
    public boolean importBackup(File file, String password) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] encrypted = new byte[(int) file.length()];
        fis.read(encrypted);
        fis.close();

        byte[] zipData = decrypt(encrypted, password);
        restoreFullZip(zipData);

        Log.i(TAG, "备份已恢复: " + file.getName());
        return true;
    }
    /** 从SAF选择的字节流恢复（Android 11+） */
    public boolean importBackupBytes(byte[] encrypted, String password) throws Exception {
        byte[] zipData = decrypt(encrypted, password);
        restoreFullZip(zipData);
        Log.i(TAG, "备份已恢复(SAF): " + encrypted.length + " bytes");
        return true;
    }

    /**
     * 自动备份（静默）：每天最多一次，滚动保留最近 MAX_AUTO_BACKUPS 份
     * 达到上限自动删除最早的备份；文件名以 auto_ 开头，使用固定密码 AUTO_PWD
     */
    public void autoBackupIfNeeded() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_AUTO, Context.MODE_PRIVATE);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(new java.util.Date());
            if (today.equals(prefs.getString(KEY_LAST_DATE, ""))) return; // 今天已备份过
            File dir = new File(BACKUP_DIR);
            if (!dir.exists()) dir.mkdirs();
            File[] autos = dir.listFiles((d, name) -> name.startsWith("auto_") && name.endsWith(BACKUP_EXT));
            if (autos != null && autos.length >= MAX_AUTO_BACKUPS) {
                // 按修改时间排序，删除最旧的（保持最多10份）
                java.util.Arrays.sort(autos, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                int toDelete = autos.length - MAX_AUTO_BACKUPS + 1;
                for (int i = 0; i < toDelete; i++) {
                    if (autos[i].delete()) Log.i(TAG, "自动备份滚动删除: " + autos[i].getName());
                }
            }
            String name = "auto_" + today + "_" +
                    new java.text.SimpleDateFormat("HHmmss", java.util.Locale.CHINA).format(new java.util.Date()) + BACKUP_EXT;
            byte[] data = createEncryptedBackup(AUTO_PWD);
            FileOutputStream fos = new FileOutputStream(new File(dir, name));
            fos.write(data);
            fos.close();
            prefs.edit().putString(KEY_LAST_DATE, today).apply();
            Log.i(TAG, "自动备份完成: " + name + " (" + data.length + " bytes)");
        } catch (Exception e) {
            Log.e(TAG, "自动备份失败", e);
        }
    }

    /** 构建完整ZIP：数据库JSON + 图片文件 + Preferences */
    private byte[] buildFullZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        // ---- 第一部分：数据库JSON ----
        String dbJson = exportDbToJson();
        zos.putNextEntry(new ZipEntry("data.json"));
        byte[] jsonBytes = dbJson.getBytes("UTF-8");
        // 带大小头，方便大文件恢复
        zos.write(jsonBytes);
        zos.closeEntry();

        // ---- 第二部分：图片文件 ----
        // 收集所有图片路径
        Set<String> imagePaths = new LinkedHashSet<>();
        SQLiteDatabase sqldb = null;
        try {
            String dbPath = context.getDatabasePath("habit_tracker.db").getAbsolutePath();
            sqldb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
            
            // shop_items中的imagePath
            Cursor c1 = sqldb.rawQuery("SELECT DISTINCT imagePath FROM shop_items WHERE imagePath IS NOT NULL AND imagePath != ''", null);
            while (c1.moveToNext()) {
                String path = c1.getString(0);
                if (path != null && !path.isEmpty()) imagePaths.add(path);
            }
            c1.close();
        } catch (Exception e) {
            Log.e(TAG, "收集图片路径失败", e);
        } finally {
            if (sqldb != null && sqldb.isOpen()) sqldb.close();
        }

        // 头像路径
        String avatarPath = profile.getAvatarPath();
        if (avatarPath != null && !avatarPath.isEmpty()) imagePaths.add(avatarPath);

        // 打包图片
        for (String imgPath : imagePaths) {
            File imgFile = new File(imgPath);
            if (imgFile.exists() && imgFile.isFile()) {
                try {
                    zos.putNextEntry(new ZipEntry("images/" + imgFile.getName()));
                    FileInputStream fis = new FileInputStream(imgFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) != -1) zos.write(buf, 0, len);
                    fis.close();
                    zos.closeEntry();
                } catch (Exception e) {
                    Log.w(TAG, "跳过不可读图片: " + imgPath, e);
                }
            }
        }

        // ---- 第三部分：SharedPreferences ----
        zos.putNextEntry(new ZipEntry("prefs.json"));
        String prefsJson = exportPreferences();
        zos.write(prefsJson.getBytes("UTF-8"));
        zos.closeEntry();

        zos.close();
        return baos.toByteArray();
    }

    /** 从完整ZIP恢复 */
    private void restoreFullZip(byte[] zipData) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(zipData);
        ZipInputStream zis = new ZipInputStream(bais);
        
        String dbJson = null;
        Map<String, byte[]> imageFiles = new HashMap<>();
        String prefsJson = null;

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            ByteArrayOutputStream entryBaos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = zis.read(buf)) != -1) entryBaos.write(buf, 0, len);
            
            if ("data.json".equals(name)) {
                dbJson = new String(entryBaos.toByteArray(), "UTF-8");
            } else if ("prefs.json".equals(name)) {
                prefsJson = new String(entryBaos.toByteArray(), "UTF-8");
            } else if (name.startsWith("images/")) {
                String fileName = name.substring(7);
                imageFiles.put(fileName, entryBaos.toByteArray());
            }
            zis.closeEntry();
        }
        zis.close();

        // 1. 恢复数据库
        if (dbJson != null) {
            importJsonToDb(dbJson);
        }

        // 2. 恢复图片到应用私有目录
        if (!imageFiles.isEmpty()) {
            File imagesDir = new File(context.getFilesDir(), "backup_images");
            imagesDir.mkdirs();
            for (Map.Entry<String, byte[]> img : imageFiles.entrySet()) {
                File out = new File(imagesDir, img.getKey());
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(img.getValue());
                fos.close();
            }
            // 更新shop_items中的imagePath指向新位置
            String dbPath = context.getDatabasePath("habit_tracker.db").getAbsolutePath();
            SQLiteDatabase sqldb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);
            try {
                for (String fileName : imageFiles.keySet()) {
                    String newPath = new File(imagesDir, fileName).getAbsolutePath();
                    sqldb.execSQL("UPDATE shop_items SET imagePath = '" + newPath.replace("'", "''") + 
                                  "' WHERE imagePath LIKE '%" + fileName.replace("'", "''") + "'");
                }
            } finally {
                if (sqldb.isOpen()) sqldb.close();
            }
            // 头像路径重定向（头像文件也在备份中 → 指向新位置）
            try {
                String avatarPath = profile.getAvatarPath();
                if (avatarPath != null && !avatarPath.isEmpty()) {
                    String avName = new File(avatarPath).getName();
                    if (imageFiles.containsKey(avName)) {
                        profile.setAvatarPath(new File(imagesDir, avName).getAbsolutePath());
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. 恢复Preferences
        if (prefsJson != null) {
            importPreferences(prefsJson);
        }
    }

    /** 导出所有 SharedPreferences（枚举 shared_prefs 目录，全量覆盖） */
    private String exportPreferences() {
        try {
            JSONObject all = new JSONObject();
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            File[] files = prefsDir.listFiles((d, name) -> name.endsWith(".xml"));
            if (files != null) {
                for (File f : files) {
                    String prefsName = f.getName().replace(".xml", "");
                    if ("auto_backup_prefs".equals(prefsName)) continue; // 跳过自动备份自身状态
                    SharedPreferences sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                    JSONObject obj = new JSONObject();
                    for (java.util.Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                        Object v = e.getValue();
                        if (v instanceof String) obj.put(e.getKey(), v);
                        else if (v instanceof Integer) obj.put(e.getKey(), (Integer) v);
                        else if (v instanceof Long) obj.put(e.getKey(), (Long) v);
                        else if (v instanceof Boolean) obj.put(e.getKey(), (Boolean) v);
                        else if (v instanceof Float) obj.put(e.getKey(), (Float) v);
                        else if (v instanceof java.util.Set) {
                            JSONArray arr = new JSONArray();
                            for (Object s : (java.util.Set<?>) v) arr.put(String.valueOf(s));
                            obj.put(e.getKey(), arr);
                        }
                    }
                    all.put(prefsName, obj);
                }
            }
            return all.toString();
        } catch (Exception e) {
            Log.e(TAG, "导出Preferences失败", e);
            return "{}";
        }
    }

    /** 恢复所有 SharedPreferences（全量覆盖） */
    private void importPreferences(String json) {
        try {
            JSONObject all = new JSONObject(json);
            java.util.Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String prefsName = keys.next();
                JSONObject obj = all.optJSONObject(prefsName);
                if (obj == null) continue;
                if ("auto_backup_prefs".equals(prefsName)) continue; // 恢复时不覆盖自动备份状态
                SharedPreferences sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                SharedPreferences.Editor ed = sp.edit();
                java.util.Iterator<String> k2 = obj.keys();
                while (k2.hasNext()) {
                    String key = k2.next();
                    Object v = obj.opt(key);
                    if (v instanceof String) ed.putString(key, (String) v);
                    else if (v instanceof Integer) ed.putInt(key, (Integer) v);
                    else if (v instanceof Long) ed.putLong(key, (Long) v);
                    else if (v instanceof Boolean) ed.putBoolean(key, (Boolean) v);
                    else if (v instanceof Double) ed.putFloat(key, ((Number) v).floatValue());
                    else if (v instanceof org.json.JSONArray) {
                        java.util.Set<String> set = new java.util.HashSet<>();
                        org.json.JSONArray arr = (org.json.JSONArray) v;
                        for (int i = 0; i < arr.length(); i++) set.add(arr.optString(i));
                        ed.putStringSet(key, set);
                    }
                }
                ed.apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "恢复Preferences失败", e);
        }
    }

    /** 导出所有表为JSON（包含 coin_earnings） */
    private String exportDbToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":5,\"exportedAt\":").append(System.currentTimeMillis()).append(",\"data\":{");

        SQLiteDatabase sqldb = null;
        try {
            String dbPath = context.getDatabasePath("habit_tracker.db").getAbsolutePath();
            sqldb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);

            // 全部17张表（含作业关卡配置与每日记录）
            String[] tables = {
                "check_ins", "coin_transactions", "tasks", "shop_items", "redemptions",
                "vocabulary", "economy_config", "word_reviews", "word_banks", "wishlist_items",
                "coin_earnings", "laundry_tasks", "lottery_prizes", "lottery_records",
                "school_rewards", "gate_config", "daily_gates"
            };

            boolean first = true;
            for (String table : tables) {
                Cursor cursor = null;
                try {
                    cursor = sqldb.rawQuery("SELECT * FROM " + table, null);
                    if (cursor.getCount() > 0) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("\"").append(table).append("\":");
                        sb.append(cursorToJson(cursor));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "跳过表 " + table + ": " + e.getMessage());
                } finally {
                    if (cursor != null) cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "导出数据库失败", e);
        } finally {
            if (sqldb != null && sqldb.isOpen()) sqldb.close();
        }

        sb.append("}}");
        return sb.toString();
    }

    private String cursorToJson(Cursor cursor) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        while (cursor.moveToNext()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                if (i > 0) sb.append(",");
                String colName = cursor.getColumnName(i);
                sb.append("\"").append(colName).append("\":");
                switch (cursor.getType(i)) {
                    case Cursor.FIELD_TYPE_NULL: sb.append("null"); break;
                    case Cursor.FIELD_TYPE_INTEGER: sb.append(cursor.getLong(i)); break;
                    case Cursor.FIELD_TYPE_FLOAT: sb.append(cursor.getDouble(i)); break;
                    case Cursor.FIELD_TYPE_BLOB:
                        byte[] blob = cursor.getBlob(i);
                        sb.append("\"").append(Base64.encodeToString(blob, Base64.NO_WRAP)).append("\"");
                        break;
                    default:
                        String val = cursor.getString(i);
                        if (val == null) sb.append("null");
                        else {
                            sb.append("\"").append(
                                val.replace("\\","\\\\").replace("\"","\\\"")
                                   .replace("\n","\\n").replace("\r","\\r")
                            ).append("\"");
                        }
                        break;
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private void importJsonToDb(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.getJSONObject("data");
        String dbPath = context.getDatabasePath("habit_tracker.db").getAbsolutePath();
        SQLiteDatabase sqldb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);
        try {
            sqldb.beginTransaction();

            // 按依赖顺序：先清空再插入
            String[] tables = {
                "check_ins", "coin_transactions", "tasks", "shop_items", "redemptions",
                "vocabulary", "economy_config", "word_reviews", "word_banks", "wishlist_items",
                "coin_earnings", "laundry_tasks", "lottery_prizes", "lottery_records",
                "school_rewards"
            };

            for (String table : tables) {
                if (!data.has(table)) continue;
                JSONArray arr = data.getJSONArray(table);
                if (arr.length() == 0) continue;

                sqldb.execSQL("DELETE FROM " + table);

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    StringBuilder cols = new StringBuilder();
                    StringBuilder vals = new StringBuilder();
                    List<String> colList = new ArrayList<>();
                    List<String> valList = new ArrayList<>();

                    JSONArray names = row.names();
                    for (int j = 0; names != null && j < names.length(); j++) {
                        String col = names.getString(j);
                        colList.add(col);
                        Object val = row.get(col);
                        if (val == null || JSONObject.NULL.equals(val)) {
                            valList.add(null);
                        } else if (val instanceof Number) {
                            valList.add(val.toString());
                        } else {
                            valList.add("'" + val.toString().replace("'","''") + "'");
                        }
                    }
                    for (int j = 0; j < colList.size(); j++) {
                        if (j > 0) { cols.append(","); vals.append(","); }
                        cols.append("`").append(colList.get(j)).append("`");
                        vals.append(valList.get(j) != null ? valList.get(j) : "NULL");
                    }
                    sqldb.execSQL("INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ")");
                }
            }
            sqldb.setTransactionSuccessful();
        } finally {
            sqldb.endTransaction();
            if (sqldb.isOpen()) sqldb.close();
        }
    }

    // ============ 加密/解密 ============

    private byte[] encrypt(byte[] data, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[GCM_IV_LENGTH];
        random.nextBytes(iv);
        SecretKey key = deriveKey(password, iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] ciphertext = cipher.doFinal(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(iv);
        baos.write(ciphertext);
        return baos.toByteArray();
    }

    private byte[] decrypt(byte[] encrypted, String password) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(encrypted);
        byte[] iv = new byte[GCM_IV_LENGTH];
        bais.read(iv);
        byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
        bais.read(ciphertext);
        SecretKey key = deriveKey(password, iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        java.security.spec.KeySpec spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 10000, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** 查找所有备份文件（兼容Android 11+ Scoped Storage） */
    public static File[] findBackupFiles(Context context) {
        java.util.List<File> files = new java.util.ArrayList<>();
        
        // 方案1: MediaStore (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                String[] projection = {MediaStore.Downloads.DISPLAY_NAME};
                String selection = MediaStore.Downloads.DISPLAY_NAME + " LIKE ?";
                String[] selectionArgs = {"%" + BACKUP_EXT};
                android.database.Cursor cursor = context.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(0);
                        if (name != null && name.endsWith(BACKUP_EXT)) {
                            files.add(new File(BACKUP_DIR, name));
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "MediaStore query failed, fallback to File API", e);
            }
        }
        
        // 方案2: File API fallback
        if (files.isEmpty()) {
            File dir = new File(BACKUP_DIR);
            File[] result = dir.listFiles((d, name) -> name.endsWith(BACKUP_EXT));
            if (result != null) java.util.Collections.addAll(files, result);
        }
        
        return files.toArray(new File[0]);
    }
}