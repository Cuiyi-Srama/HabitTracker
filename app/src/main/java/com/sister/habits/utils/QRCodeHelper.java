package com.sister.habits.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Hashtable;

/**
 * QR码生成与解析工具
 * 注意：扫码功能需在 Activity 中用 ActivityResultLauncher + ScanContract 实现
 */
public class QRCodeHelper {

    private static final int QR_SIZE = 512;
    /** 生成设备配对QR码内容（含本机IP，扫码后可直连同步，不依赖扫描发现） */
    public static String buildDeviceQrContent(Context context) {
        String deviceKey = DeviceIdentity.getDeviceKey(context);
        String deviceName = android.os.Build.MODEL;
        ProfileManager pm = ProfileManager.getInstance(context);
        String nickname = pm.getNickname();
        String ip = "";
        try {
            String localIp = com.sister.habits.sync.SyncManager.getInstance(context).getLocalIpv4();
            if (localIp != null) ip = localIp;
        } catch (Exception ignored) {}
        return "HABITPAIR:" + deviceKey + ":" + nickname + "@" + deviceName + "#" + ip;
    }

    // ==================== 同步配置二维码（v3.0.66：家人扫码零配置） ====================
    private static final String SYNC_QR_PREFIX = "HABITSYNC:";

    /** 生成同步配置QR码内容：HABITSYNC:<服务器URL>#<同步模式>#<家庭Token>（v3.0.67 带Token） */
    public static String buildSyncConfigQrContent(Context context) {
        try {
            com.sister.habits.sync.SyncManager sm = com.sister.habits.sync.SyncManager.getInstance(context);
            String url = sm.getHubSync().getServerUrl();
            if (url == null || url.isEmpty()) return null;
            int mode = sm.getSyncMode();
            String token = sm.getHubSync().getServerToken();
            if (token == null || token.isEmpty()) {
                return SYNC_QR_PREFIX + url + "#" + mode;
            }
            return SYNC_QR_PREFIX + url + "#" + mode + "#" + token;
        } catch (Exception e) {
            return null;
        }
    }
    /** 判断是否为同步配置二维码 */
    public static boolean isSyncConfigQr(String content) {
        return content != null && content.startsWith(SYNC_QR_PREFIX);
    }
    /** 解析服务器URL */
    public static String parseSyncConfigUrl(String content) {
        if (!isSyncConfigQr(content)) return null;
        String body = content.substring(SYNC_QR_PREFIX.length());
        int hash = body.indexOf('#');
        return hash > 0 ? body.substring(0, hash) : body;
    }
    /** 解析同步模式（默认2=自动）——格式：url#mode[#token] */
    public static int parseSyncConfigMode(String content) {
        if (!isSyncConfigQr(content)) return 2;
        String body = content.substring(SYNC_QR_PREFIX.length());
        int hash = body.indexOf('#');
        if (hash > 0) {
            String modePart = body.substring(hash + 1);
            int hash2 = modePart.indexOf('#');
            if (hash2 > 0) modePart = modePart.substring(0, hash2);
            try { return Integer.parseInt(modePart); } catch (Exception ignored) {}
        }
        return 2;
    }
    /** 解析家庭Token（可选，第三个字段） */
    public static String parseSyncConfigToken(String content) {
        if (!isSyncConfigQr(content)) return "";
        String body = content.substring(SYNC_QR_PREFIX.length());
        int hash1 = body.indexOf('#');
        if (hash1 < 0) return "";
        String rest = body.substring(hash1 + 1);
        int hash2 = rest.indexOf('#');
        return hash2 >= 0 ? rest.substring(hash2 + 1) : "";
    }

    /** 生成QR码Bitmap */
    public static Bitmap generateQrBitmap(String content) {
        try {
            Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
            Bitmap bitmap = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.RGB_565);
            for (int x = 0; x < QR_SIZE; x++) {
                for (int y = 0; y < QR_SIZE; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析QR码内容，提取设备Key */
    public static String parseDeviceKey(String qrContent) {
        if (qrContent == null) return null;
        if (qrContent.startsWith("HABITPAIR:")) {
            String[] parts = qrContent.split(":");
            if (parts.length >= 2) return parts[1];
        }
        return null;
    }

    /** 解析QR码内容，提取设备显示名 */
    public static String parseDeviceName(String qrContent) {
        if (qrContent == null) return "未知设备";
        if (qrContent.startsWith("HABITPAIR:")) {
            String[] parts = qrContent.split(":");
            if (parts.length >= 3) return parts[2];
        }
        return qrContent.length() > 20 ? qrContent.substring(0, 20) + "..." : qrContent;
    }

    /** 解析QR码内容，提取本机IP（#后段；旧格式无IP返回null） */
    public static String parseDeviceIp(String qrContent) {
        if (qrContent == null) return null;
        int idx = qrContent.indexOf('#');
        if (idx < 0 || idx == qrContent.length() - 1) return null;
        String ip = qrContent.substring(idx + 1).trim();
        return ip.isEmpty() ? null : ip;
    }
}
