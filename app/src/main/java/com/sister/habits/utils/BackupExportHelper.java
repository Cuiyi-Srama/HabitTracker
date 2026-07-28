package com.sister.habits.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密备份/恢复工具
 * 导出：SQLite导出JSON → ZIP压缩 → AES-256-GCM加密 → .habitbak文件
 * 导入：解密 → 解ZIP → 解析JSON → 恢复到数据库
 * 文件存放在 /storage/emulated/0/Download/
 */
public class BackupExportHelper {

    private static final String TAG = "BackupExport";
    private static final String BACKUP_DIR = "/storage/emulated/0/Download";
    private static final String BACKUP_EXT = ".habitbak";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final Context context;

    public BackupExportHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 导出加密备份文件 */
    public File exportBackup(String password) throws Exception {
        String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String deviceKey = DeviceIdentity.getDeviceKey(context);
        String shortKey = deviceKey != null && deviceKey.length() >= 8 ? deviceKey.substring(0, 8) : "UNKNOWN";
        File backupFile = new File(BACKUP_DIR, "HabitTracker_backup_" + shortKey + "_" + dateStr + BACKUP_EXT);

        // 1. 导出数据库为JSON
        String json = exportDbToJson();

        // 2. 压缩
        byte[] compressed = compress(json.getBytes("UTF-8"));

        // 3. AES-256-GCM加密
        byte[] encrypted = encrypt(compressed, password);

        // 4. 写入文件
        FileOutputStream fos = new FileOutputStream(backupFile);
        fos.write(encrypted);
        fos.close();

        Log.i(TAG, "备份已导出: " + backupFile.getAbsolutePath() + " (" + backupFile.length() + " bytes)");
        return backupFile;
    }

    /** 从加密备份文件恢复 */
    public boolean importBackup(File file, String password) throws Exception {
        // 1. 读取文件
        FileInputStream fis = new FileInputStream(file);
        byte[] encrypted = new byte[(int) file.length()];
        fis.read(encrypted);
        fis.close();

        // 2. 解密
        byte[] compressed = decrypt(encrypted, password);

        // 3. 解压
        byte[] jsonBytes = decompress(compressed);
        String json = new String(jsonBytes, "UTF-8");

        // 4. 恢复数据库
        importJsonToDb(json);

        Log.i(TAG, "备份已恢复: " + file.getName());
        return true;
    }

    /** 导出所有表为JSON */
    private String exportDbToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":4,\"exportedAt\":").append(System.currentTimeMillis()).append(",\"data\":{");

        // 获取数据库实例
        com.sister.habits.data.AppDatabase db = com.sister.habits.data.AppDatabase.getInstance(context);
        SQLiteDatabase sqldb = null;
        try {
            // 通过Room的OpenHelper获取底层SQLiteDatabase
            java.lang.reflect.Field field = com.sister.habits.data.AppDatabase.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            // 直接查询数据库文件
            String dbPath = context.getDatabasePath("habit_tracker.db").getAbsolutePath();
            sqldb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);

            String[] tables = {"check_ins", "coin_transactions", "tasks", "shop_items", "redemptions",
                "vocabulary", "economy_config", "word_reviews", "word_banks", "wishlist_items"};

            boolean first = true;
            for (String table : tables) {
                Cursor cursor = sqldb.rawQuery("SELECT * FROM " + table, null);
                if (cursor.getCount() > 0) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("\"").append(table).append("\":");
                    sb.append(cursorToJson(cursor));
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "导出失败", e);
        } finally {
            if (sqldb != null && sqldb.isOpen()) sqldb.close();
        }

        sb.append("}}");
        return sb.toString();
    }

    private String cursorToJson(Cursor cursor) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
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
                        sb.append("\"").append(android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP)).append("\"");
                        break;
                    default:
                        String val = cursor.getString(i);
                        if (val == null) sb.append("null");
                        else sb.append("\"").append(val.replace("\\","\\\\").replace("\"","\\\"")).append("\"");
                        break;
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private void importJsonToDb(String json) throws Exception {
        org.json.JSONObject root = new org.json.JSONObject(json);
        org.json.JSONObject data = root.getJSONObject("data");

        com.sister.habits.data.AppDatabase db = com.sister.habits.data.AppDatabase.getInstance(context);

        String dbPath = context.getDatabasePath("habit_tracker.db").getAbsolutePath();
        SQLiteDatabase sqldb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);

        try {
            sqldb.beginTransaction();
            String[] tables = {"check_ins", "coin_transactions", "tasks", "shop_items", "redemptions",
                "vocabulary", "economy_config", "word_reviews", "word_banks", "wishlist_items"};

            for (String table : tables) {
                if (!data.has(table)) continue;
                org.json.JSONArray arr = data.getJSONArray(table);
                if (arr.length() == 0) continue;

                // 清空旧数据
                sqldb.execSQL("DELETE FROM " + table);

                // 逐行插入
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject row = arr.getJSONObject(i);
                    StringBuilder cols = new StringBuilder();
                    StringBuilder vals = new StringBuilder();
                    java.util.List<String> colList = new java.util.ArrayList<>();
                    java.util.List<String> valList = new java.util.ArrayList<>();

                    org.json.JSONArray names = row.names();
                    for (int j = 0; j < names.length(); j++) {
                        String col = names.getString(j);
                        colList.add(col);
                        Object val = row.get(col);
                        if (val == null || val == org.json.JSONObject.NULL) {
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

    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("data.json"));
        zos.write(data);
        zos.closeEntry();
        zos.close();
        return baos.toByteArray();
    }

    private byte[] decompress(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ZipInputStream zis = new ZipInputStream(bais);
        zis.getNextEntry();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = zis.read(buf)) != -1) baos.write(buf, 0, len);
        zis.close();
        return baos.toByteArray();
    }

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
        // 使用PBKDF2派生密钥
        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        java.security.spec.KeySpec spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 10000, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** 查找所有备份文件 */
    public static File[] findBackupFiles() {
        File dir = new File(BACKUP_DIR);
        return dir.listFiles((d, name) -> name.endsWith(BACKUP_EXT));
    }
}
