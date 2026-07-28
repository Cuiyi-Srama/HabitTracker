package com.sister.habits.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.integration.android.IntentIntegrator;

import java.util.Hashtable;

import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;

/**
 * QR码生成与扫描工具
 * 生成：设备标识 + 设备名称 → QR码图片
 * 扫描：解析QR码 → 获取对端设备信息
 */
public class QRCodeHelper {

    private static final int QR_SIZE = 512;

    /** 生成设备配对QR码内容 */
    public static String buildDeviceQrContent(Context context) {
        String deviceKey = DeviceIdentity.getDeviceKey(context);
        String deviceName = android.os.Build.MODEL;
        ProfileManager pm = ProfileManager.getInstance(context);
        String nickname = pm.getNickname();
        return "HABITPAIR:" + deviceKey + ":" + nickname + "@" + deviceName;
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

    /** 启动ZXing扫码（需安装条码扫描器或使用内置） */
    public static void startScan(AppCompatActivity activity) {
        IntentIntegrator integrator = new IntentIntegrator(activity);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("扫描对方设备的配对码");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }
}
