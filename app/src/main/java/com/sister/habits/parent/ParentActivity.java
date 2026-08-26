package com.sister.habits.parent;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.EconomyConfig;
import com.sister.habits.data.models.Redemption;
import com.sister.habits.data.models.ShopItem;
import com.sister.habits.data.models.Task;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.sister.habits.sync.SyncManager;
import com.sister.habits.sync.EarningService;
import com.sister.habits.sync.RedemptionApprovalService;
import com.sister.habits.data.models.CoinEarning;
import com.sister.habits.utils.SoundHelper;
import com.sister.habits.utils.BindKeyManager;
import com.sister.habits.utils.NotificationHelper;
import com.sister.habits.utils.ProfileManager;
import com.sister.habits.data.models.GateConfig;
import com.sister.habits.data.models.DailyGate;
import com.sister.habits.data.models.LaundryTask;
import com.sister.habits.data.models.LotteryPrize;
import com.sister.habits.data.models.LotteryRecord;
import com.sister.habits.data.models.SchoolReward;
import com.sister.habits.utils.GateHelper;
import com.sister.habits.utils.PinHelper;
import android.app.KeyguardManager;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * 家长模式——统一管理界面
 * 看板/审批兑换/发布任务/管理商城/调整参数
 */
public class ParentActivity extends AppCompatActivity {

    private AppDatabase db;
    private SyncManager syncManager;
    private SoundHelper soundHelper;
    private ShopManager shopManager;
    private ProfileManager profile;

    private TextView tvStats;
    private RecyclerView rvPendingApprovals, rvPendingTasks, rvPendingEarnings, rvApprovalHub;
    private View btnAddTask, btnAddShopItem, btnSettings, btnSync;
    private View btnGateManage;
    private View btnApproveSelected, btnRejectSelected;
    private final java.util.Set<String> selectedApprovalIds = new java.util.HashSet<>();
    private final java.util.Set<String> selectedApprovalHub = new java.util.HashSet<>();
    private java.util.List<ApprovalItem> currentApprovalItems = new java.util.ArrayList<>();
    private final java.util.Set<String> selectedTaskIds = new java.util.HashSet<>();
    private final java.util.Set<String> selectedEarningIds = new java.util.HashSet<>();

    // 相册选图 — 当前选中的商品图片路径
    private String pendingBackupPassword;  // SAF导出等待中的备份密码
    // 当前打开的商品对话框View（用于图片预览更新）

    private Runnable deviceLockSuccessCallback = null;
    private final java.util.concurrent.atomic.AtomicBoolean deviceLockPending = new java.util.concurrent.atomic.AtomicBoolean(false);

    // 设备锁验证启动器
    private final ActivityResultLauncher<android.content.Intent> deviceLockLauncher =
            registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (deviceLockSuccessCallback != null) {
                            deviceLockSuccessCallback.run();
                            deviceLockSuccessCallback = null;
                        }
                    } else {
                        Toast.makeText(this, "\u274c 设备锁验证失败", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });

    // 备份导出位置选择器（SAF创建文档）
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> createBackupLauncher =
            registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        android.net.Uri uri = result.getData().getData();
                        if (uri != null && pendingBackupPassword != null) {
                            final String pwd = pendingBackupPassword;
                            pendingBackupPassword = null;
                            new Thread(() -> {
                                try {
                                    com.sister.habits.utils.BackupExportHelper helper = new com.sister.habits.utils.BackupExportHelper(this);
                                    byte[] data = helper.createEncryptedBackup(pwd);
                                    java.io.OutputStream os = getContentResolver().openOutputStream(uri, "w");
                                    if (os != null) {
                                        os.write(data);
                                        os.flush();
                                        os.close();
                                        runOnUiThread(() -> Toast.makeText(this, "✅ 备份成功！已保存到你选择的位置", Toast.LENGTH_LONG).show());
                                    }
                                } catch (Exception e) {
                                    runOnUiThread(() -> Toast.makeText(this, "❌ 备份失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                            }).start();
                        }
                    }
                });
    // 备份文件选择器（SAF，无需存储权限）
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> openBackupLauncher =
            registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        android.net.Uri uri = result.getData().getData();
                        if (uri != null) {
                            showRestorePasswordDialog(uri);
                        }
                    }
                });

    // 相册选图启动器
    private final ActivityResultLauncher<String> pickShopImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> shopManager.onImagePicked(uri));

    // 词库JSON导入 — 使用 bankId 隔离，不丢失学习进度
    private final ActivityResultLauncher<String[]> wordbankImportLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    is.close();
                    String json = new String(buffer, "UTF-8");

                    java.lang.reflect.Type type = new TypeToken<List<JsonImportWord>>(){}.getType();
                    List<JsonImportWord> importWords = new Gson().fromJson(json, type);

                    if (importWords == null || importWords.isEmpty()) {
                        Toast.makeText(this, "词库文件格式有误，未找到有效单词", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // 创建新词库记录
                    String bankId = "import_" + System.currentTimeMillis();
                    com.sister.habits.data.models.WordBank bank = com.sister.habits.data.models.WordBank.fromImport("自定义词库", importWords.size());
                    bank.id = bankId;
                    db.wordBankDao().insert(bank);

                    // 批量入库（带 bankId）
                    List<com.sister.habits.data.models.Vocabulary> words = new java.util.ArrayList<>();
                    for (JsonImportWord jw : importWords) {
                        com.sister.habits.data.models.Vocabulary v = new com.sister.habits.data.models.Vocabulary();
                        v.id = UUID.randomUUID().toString();
                        v.word = jw.w;
                        v.meaning = jw.m;
                        v.phonetic = jw.p;
                        v.category = jw.c;
                        v.gradeLevel = jw.g;
                        v.level = jw.l;
                        v.mastered = false;
                        v.active = true;
                        v.bankId = bankId;
                        words.add(v);
                    }

                    int batchSize = 50;
                    for (int i = 0; i < words.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, words.size());
                        db.vocabularyDao().insertAll(words.subList(i, end));
                    }

                    // 切换到新词库
                    SharedPreferences prefs = getSharedPreferences("wordbank_prefs", MODE_PRIVATE);
                    prefs.edit().putString("active_bank_id", bankId).apply();
                    db.wordBankDao().deactivateAll();
                    db.wordBankDao().setActive(bankId);

                    Toast.makeText(this, "✅ 导入成功！共 " + words.size() + " 个单词，已切换到新词库", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    android.util.Log.e("ParentActivity", "词库导入失败", e);
                    Toast.makeText(this, "❌ 导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

    /** QR扫码配对启动器 */
    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result != null && result.getContents() != null) {
                    String qrContent = result.getContents();
                    // v3.0.66：同步配置二维码（家人扫码零配置）—— 最高优先级识别
                    if (com.sister.habits.utils.QRCodeHelper.isSyncConfigQr(qrContent)) {
                        String serverUrl = com.sister.habits.utils.QRCodeHelper.parseSyncConfigUrl(qrContent);
                        int mode = com.sister.habits.utils.QRCodeHelper.parseSyncConfigMode(qrContent);
                        if (serverUrl != null) {
                            String qrToken = com.sister.habits.utils.QRCodeHelper.parseSyncConfigToken(qrContent);
                            syncManager.getHubSync().setServerConfig(serverUrl, qrToken);
                            syncManager.setSyncMode(mode);
                            Toast.makeText(this, "✅ 已自动配置同步服务器\n" + serverUrl + "\n模式: " + syncManager.getSyncModeText(), Toast.LENGTH_LONG).show();
                            // 立即同步
                            new Thread(() -> {
                                boolean ok = syncManager.getHubSync().syncToServer();
                                runOnUiThread(() -> Toast.makeText(this, ok ? "✅ 服务器同步成功，数据已合并" : "❌ 服务器暂不可达（电脑需开机）", Toast.LENGTH_LONG).show());
                            }).start();
                        }
                        return;
                    }
                    String deviceKey = com.sister.habits.utils.QRCodeHelper.parseDeviceKey(qrContent);
                    String deviceName = com.sister.habits.utils.QRCodeHelper.parseDeviceName(qrContent);
                    if (deviceKey != null) {
                        String deviceIp = com.sister.habits.utils.QRCodeHelper.parseDeviceIp(qrContent);
                        Toast.makeText(this, "📡 已配对: " + deviceName + "\n正在同步数据...", Toast.LENGTH_LONG).show();
                        // 把配对设备信息存到SharedPreferences
                        getSharedPreferences("paired_devices", MODE_PRIVATE)
                                .edit()
                                .putString("paired_" + deviceKey, deviceName)
                                .apply();
                        // ① 直连同步：QR码携带对方IP，直接连接交换（不依赖扫描发现），带进度显示
                        if (deviceIp != null) {
                            final android.app.ProgressDialog syncPd = new android.app.ProgressDialog(this);
                            syncPd.setTitle("🔄 正在同步");
                            syncPd.setMessage("📡 正在连接 " + deviceIp + "...");
                            syncPd.setCancelable(false);
                            syncPd.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
                            syncPd.show();
                            syncManager.getLanSync().syncToDevice(deviceIp, new com.sister.habits.sync.SyncCallback() {
                                @Override
                                public void onStatusUpdate(String status) {
                                    runOnUiThread(() -> {
                                        if (syncPd.isShowing()) syncPd.setMessage(status);
                                    });
                                }
                                @Override
                                public void onHubFound(String ip, String deviceId) {}
                                @Override
                                public void onSyncComplete(boolean success, String message) {
                                    runOnUiThread(() -> {
                                        if (syncPd.isShowing()) syncPd.dismiss();
                                        Toast.makeText(ParentActivity.this, message, Toast.LENGTH_LONG).show();
                                    });
                                }
                                @Override
                                public void onScanProgress(int scanned, int total) {}
                            });
                        }
                        // ② 全同步兜底（Hub+局域网扫描+云端），完成后给反馈
                        syncManager.triggerFullSyncAsync(() -> runOnUiThread(() ->
                                Toast.makeText(this, "🔄 后台同步已结束，请检查数据是否更新（需同一WiFi）", Toast.LENGTH_LONG).show()));
                    } else {
                        Toast.makeText(this, "❌ 无效的配对码: " + qrContent.substring(0, Math.min(30, qrContent.length())), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "❌ 扫码取消或失败", Toast.LENGTH_SHORT).show();
                }
            });

    private static class JsonImportWord {
        String g; String c; String w; String m; String p; int l;
    }

    /** 从静态源下载词库 */
    private void downloadFromStaticSource(String id, String name, String url, String format, String gradeLabel) {
        ExternalSource src = new ExternalSource();
        src.id = id; src.name = name; src.url = url; src.format = format; src.gradeLabel = gradeLabel;
        downloadAndPreview(src);
    }

    /** 外部词库源 */
    private static class ExternalSource {
        String id;
        String name;
        String description;
        String url;
        String backupUrl;
        String backupUrl2;
        String format;
        String gradeLabel;
    }

    /** 下载外部词库 → 预览 → 确认后应用 */
            private void downloadAndPreview(ExternalSource source) {
        soundHelper.playClickSound();
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setTitle("📥 下载词库中...");
        progress.setMessage("正在连接: " + source.name);
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.show();

        final int MAX_RETRIES = 2;
        new Thread(() -> {
            // 三路URL池（在Thread内部构建，避免lambda捕获非final变量）
            String[] urls = new String[3];
            int urlCount = 0;
            urls[urlCount++] = source.url;
            if (source.backupUrl != null && !source.backupUrl.isEmpty()) urls[urlCount++] = source.backupUrl;
            if (source.backupUrl2 != null && !source.backupUrl2.isEmpty()) urls[urlCount++] = source.backupUrl2;

            byte[] rawData = null;
            String errorMsg = null;
            int urlIdx = 0;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                if (urlIdx >= urlCount) break;
                String dlUrl = urls[urlIdx];
                try {
                    final String label = urlIdx == 0 ? "CDN主线路" : urlIdx == 1 ? "备用CDN" : "GitHub原始源";
                    final int curIdx = urlIdx;
                    final int curCount = urlCount;
                    runOnUiThread(() -> progress.setMessage(label + " (" + (curIdx+1) + "/" + curCount + "): " + source.name));

                    java.net.URI uri = new java.net.URI(dlUrl);
                    java.net.URL url = uri.toURL();
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) HabitTracker/2.0");
                    conn.setInstanceFollowRedirects(true);

                    int responseCode = conn.getResponseCode();
                    if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                        throw new java.io.IOException("HTTP " + responseCode);
                    }

                    int contentLength = conn.getContentLength();
                    java.io.InputStream is = conn.getInputStream();
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(contentLength > 0 ? contentLength : 65536);
                    byte[] buf = new byte[8192];
                    int len;
                    long totalRead = 0;
                    long lastUpdate = 0;

                    while ((len = is.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                        totalRead += len;
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 200) {
                            lastUpdate = now;
                            final long read = totalRead;
                            final int pct = contentLength > 0 ? (int) (read * 100 / contentLength) : -1;
                            runOnUiThread(() -> {
                                if (pct >= 0) {
                                    progress.setProgress(Math.min(pct, 99));
                                    progress.setMessage("下载中: " + (read / 1024) + "KB / " + (contentLength / 1024) + "KB");
                                } else {
                                    // 服务器未返回Content-Length：切换不确定进度条，避免"进度条不走"
                                    if (!progress.isIndeterminate()) progress.setIndeterminate(true);
                                    progress.setMessage("下载中: " + (read / 1024) + "KB...");
                                }
                            });
                        }
                    }
                    is.close();
                    rawData = baos.toByteArray();
                    break;

                } catch (java.net.SocketException e) {
                    errorMsg = "连接超时";
                    android.util.Log.w("Download", "[" + dlUrl.substring(0, Math.min(40, dlUrl.length())) + "] " + e.getMessage());
                    urlIdx++;
                    if (urlIdx < urlCount) {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    }
                } catch (java.io.IOException e) {
                    errorMsg = e.getMessage();
                    android.util.Log.w("Download", "[" + dlUrl.substring(0, Math.min(40, dlUrl.length())) + "] " + e.getMessage());
                    urlIdx++;
                    if (urlIdx < urlCount) {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    }
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                    android.util.Log.w("Download", "Unexpected: " + e.getMessage());
                    break;
                }
            }

            if (rawData == null) {
                final String msg = errorMsg;
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(ParentActivity.this, "❌ 下载失败: " + msg, Toast.LENGTH_LONG).show();
                });
                return;
            }

            try {
                String json = new String(rawData, "UTF-8");
                String grade = source.gradeLabel != null ? source.gradeLabel : "external";
                java.util.List<com.sister.habits.data.models.Vocabulary> words = com.sister.habits.utils.WordBankParser.parse(json, grade);
                for (com.sister.habits.data.models.Vocabulary w : words) { w.bankId = source.id; w.active = true; }
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (words.isEmpty()) {
                        Toast.makeText(ParentActivity.this, "❌ 词库解析失败：格式不兼容或为空", Toast.LENGTH_LONG).show();
                        return;
                    }
                    StringBuilder samples = new StringBuilder();
                    int sampleCount = Math.min(5, words.size());
                    for (int i = 0; i < sampleCount; i++) {
                        com.sister.habits.data.models.Vocabulary v = words.get(i);
                        samples.append("• ").append(v.word).append(" — ").append(v.meaning).append("\n");
                    }
                    new AlertDialog.Builder(ParentActivity.this)
                            .setTitle("📖 预览词库")
                            .setMessage("来源: " + source.name + "\n" +
                                    "共 " + words.size() + " 个单词\n" +
                                    "格式: " + source.format + "\n\n" +
                                    "📝 示例:\n" + samples.toString())
                            .setPositiveButton("✅ 确认使用", (d, w) -> applyExternalWordbank(words, source))
                            .setNegativeButton("取消", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(ParentActivity.this, "❌ 解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    /** 应用外部词库到数据库 — 使用 bankId 隔离，学习进度独立保存 */
    private void applyExternalWordbank(java.util.List<com.sister.habits.data.models.Vocabulary> words, ExternalSource source) {
        try {
            String bankId = "ext_" + source.id;
            for (com.sister.habits.data.models.Vocabulary v : words) {
                v.bankId = bankId;
            }

            // 创建或更新词库记录
            com.sister.habits.data.models.WordBank bank = com.sister.habits.data.models.WordBank.fromExternal(
                    source.id, source.name, source.url, source.gradeLabel, words.size());
            bank.id = bankId;
            db.wordBankDao().insert(bank);

            // 分批插入（不清除旧词库）
            int batchSize = 100;
            for (int i = 0; i < words.size(); i += batchSize) {
                int end = Math.min(i + batchSize, words.size());
                db.vocabularyDao().insertAll(words.subList(i, end));
            }

            // 切换到新词库
            SharedPreferences prefs = getSharedPreferences("wordbank_prefs", MODE_PRIVATE);
            prefs.edit().putString("active_bank_id", bankId).apply();
            db.wordBankDao().deactivateAll();
            db.wordBankDao().setActive(bankId);

            Toast.makeText(this, "✅ 已启用: " + source.name + "（" + words.size() + "词）\n学习进度独立保存，切回旧词库不丢失", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.util.Log.e("ParentActivity", "应用外部词库失败", e);
            Toast.makeText(this, "❌ 应用失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        // ✅ 初始化数据库和服务
        db = AppDatabase.getInstance(this);
        syncManager = SyncManager.getInstance(this);
        // 双保险：Activity 打开即确保 18080 监听（即使 HabitApp 线程被初始化阻塞）
        try { syncManager.getLanSync().start(); } catch (Exception ignored) {}
        soundHelper = SoundHelper.getInstance(this);
        shopManager = new ShopManager(this, db, soundHelper);
        profile = ProfileManager.getInstance(this);

        // ✅ 初始化UI控件
        tvStats = findViewById(R.id.tv_parent_stats);
        rvApprovalHub = findViewById(R.id.rv_approval_hub);
        rvApprovalHub.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        btnAddTask = findViewById(R.id.btn_add_task);
        btnAddShopItem = findViewById(R.id.btn_add_shop_item);
        btnSettings = findViewById(R.id.btn_settings);
        btnSync = findViewById(R.id.btn_sync);

        // ✅ 设置监听器
        btnAddTask.setOnClickListener(v -> { soundHelper.playClickSound(); showAddTaskDialog(); });
        btnAddShopItem.setOnClickListener(v -> { soundHelper.playClickSound(); showAddShopItemDialog(); });
        btnSettings.setOnClickListener(v -> { soundHelper.playClickSound(); showSettingsDialog(); });
        btnSync.setOnClickListener(v -> {
            soundHelper.playClickSound();
            Toast.makeText(this, "🔄 全同步已触发（Hub+局域网+云端）", Toast.LENGTH_SHORT).show();
            syncManager.triggerFullSyncAsync();
        });
        // ❤️ 打赏支持（低调入口，家长端底部小字）
        TextView tvDonate = findViewById(R.id.tv_donate);
        if (tvDonate != null) {
            tvDonate.setOnClickListener(v -> showDonateDialog());
        }
        btnGateManage = findViewById(R.id.btn_gate_manage);
        btnApproveSelected = findViewById(R.id.btn_approve_selected);
        btnRejectSelected = findViewById(R.id.btn_reject_selected);
        if (btnGateManage != null) {
            btnGateManage.setOnClickListener(v -> { soundHelper.playClickSound(); showGateManageDialog(); });
        }
        if (btnApproveSelected != null) {
            btnApproveSelected.setOnClickListener(v -> { soundHelper.playClickSound(); approveSelected(true); });
        }
        if (btnRejectSelected != null) {
            btnRejectSelected.setOnClickListener(v -> { soundHelper.playClickSound(); approveSelected(false); });
        }
        View btnApproveAll = findViewById(R.id.btn_approve_all);
        View btnRejectAll = findViewById(R.id.btn_reject_all);
        if (btnApproveAll != null) {
            btnApproveAll.setOnClickListener(v -> { soundHelper.playClickSound(); approveAll(true); });
        }
        if (btnRejectAll != null) {
            btnRejectAll.setOnClickListener(v -> { soundHelper.playClickSound(); approveAll(false); });
        }
        // ✅ 集成审批中心标题可点击 → 打开Hub对话框（快速单项审批）

        // ✅ 通知渠道
        NotificationHelper.createChannel(this);

        // 🔐 安全防护（横竖屏切换跳过）
        if (savedInstanceState != null) {
            refreshAll();
            return;
        }
        if (PinHelper.isSystemLockEnabled(this)) {
            android.app.KeyguardManager kgm = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (kgm != null && kgm.isKeyguardSecure()) {
                deviceLockSuccessCallback = () -> {
                    if (db != null) {
                        if (PinHelper.isAppPinEnabled(this)) showPinVerifyDialog(() -> refreshAll());
                        refreshAll();
                    }
                };
                android.content.Intent intent = kgm.createConfirmDeviceCredentialIntent("🔐 家长验证", "请验证身份以进入家长管理");
                if (intent != null) { deviceLockPending.set(true); deviceLockLauncher.launch(intent); return; }
            }
            if (PinHelper.isAppPinEnabled(this)) { showPinVerifyDialog(() -> refreshAll()); return; }
            refreshAll();
        } else if (PinHelper.isAppPinEnabled(this)) {
            showPinVerifyDialog(() -> refreshAll());
            return;
        } else {
            Toast.makeText(this, "⚠️ 安全防护已全部关闭，建议开启至少一种验证", Toast.LENGTH_LONG).show();
            refreshAll();
        }
        // 📦 每天自动备份一次（静默，滚动保留最近10份，不阻塞UI）
        new Thread(() -> {
            try {
                new com.sister.habits.utils.BackupExportHelper(this).autoBackupIfNeeded();
            } catch (Exception ignored) {}
        }).start();
    }

        /** 打开图片选择器（供 ShopManager 使用） */
    public void launchShopImagePicker() { pickShopImageLauncher.launch("image/*"); }

    protected void onResume() {
        super.onResume();
        com.sister.habits.utils.SnapshotHelper.register(this);
        if (db != null) refreshAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SoundHelper.releaseInstance();
    }

    // ========== 🔐 安全防护（PIN/指纹/设备锁） ==========

    /** 首次设置：双开关 */
    private void showAuthSetupDialog() {
        showPinManageDialog();
    }

    /** 已废弃 - 验证逻辑合并到onCreate */
    private void showAuthVerifyDialog(Runnable onSuccess) {
        showPinVerifyDialog(onSuccess);
    }



    /** PIN码设置 */
    private void showPinSetupDialog() {
        final android.widget.EditText etPin1 = new android.widget.EditText(this);
        etPin1.setHint("请设置4~6位数字PIN码");
        etPin1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        final android.widget.EditText etPin2 = new android.widget.EditText(this);
        etPin2.setHint("再次输入确认");
        etPin2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.addView(etPin1);
        layout.addView(etPin2);

        new AlertDialog.Builder(this)
                .setTitle("🔐 设置家长PIN码")
                .setMessage("首次进入家长模式，请设置一个PIN码。\n孩子不知道PIN码就无法进入家长界面。")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("确认设置", (d, w) -> {
                    String p1 = etPin1.getText().toString().trim();
                    String p2 = etPin2.getText().toString().trim();
                    if (p1.isEmpty() || p2.isEmpty()) {
                        Toast.makeText(this, "⚠️ PIN码不能为空", Toast.LENGTH_SHORT).show();
                        showPinSetupDialog();
                        return;
                    }
                    if (!p1.equals(p2)) {
                        Toast.makeText(this, "⚠️ 两次输入不一致", Toast.LENGTH_SHORT).show();
                        showPinSetupDialog();
                        return;
                    }
                    if (PinHelper.setPin(this, p1)) {
                        Toast.makeText(this, "✅ PIN码设置成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "⚠️ PIN码格式错误（需4~6位数字）", Toast.LENGTH_SHORT).show();
                        showPinSetupDialog();
                    }
                })
                .setNegativeButton("退出", (d, w) -> finish())
                .show();
    }

    /** PIN码验证（非递归，错误时清空输入框重试） */
    private void showPinVerifyDialog(Runnable onSuccess) {
        final android.widget.EditText etPin = new android.widget.EditText(this);
        etPin.setHint("请输入PIN码");
        etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.addView(etPin);

        final boolean[] verified = {false};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🔐 验证PIN码")
                .setMessage("请输入家长PIN码以进入管理界面")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("确认", null)
                .setNegativeButton("退出", (d, w) -> finish())
                .create();
        dialog.setOnShowListener(di -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String pin = etPin.getText().toString().trim();
                if (PinHelper.verifyPin(ParentActivity.this, pin)) {
                    verified[0] = true;
                    dialog.dismiss();
                    if (onSuccess != null) onSuccess.run();
                } else {
                    Toast.makeText(ParentActivity.this, "❌ PIN码错误", Toast.LENGTH_SHORT).show();
                    etPin.setText("");
                }
            });
        });
        dialog.setOnDismissListener(di -> {
            if (!verified[0]) finish();
        });
        dialog.show();
    }





    private void refreshAll() {
        refreshStats();
        loadApprovalHub();
    }

        private void refreshStats() {
        int totalCheckIns = db.checkInDao().getTotalCheckIns("sister");
        int pendingEarningCount = db.coinEarningDao().getPendingCount();
        int maxStreak = db.checkInDao().getMaxStreak("sister");
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        int pendingTaskCount = db.taskDao().getByStatus("pending").size();
        int earningPendingCount = db.coinEarningDao().getPendingCount();
        Integer balance = db.coinTransactionDao().getBalance("sister");

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        boolean checkedInToday = db.checkInDao().getByDate("sister", today) != null;

        long todayStart = 0;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            todayStart = sdf.parse(today).getTime();
        } catch (Exception e) { }
        int todayEarned = db.coinTransactionDao().getTotalEarnedSince("sister", todayStart);
        int todaySpent = db.coinTransactionDao().getTotalSpentSince("sister", todayStart);

        String nickname = profile.getNickname();
        tvStats.setText(
                "\uD83D\uDCCA " + nickname + "\u7684\u4E60\u60EF\u6570\u636E\n" +
                "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                "\u4ECA\u65E5\u6253\u5361: " + (checkedInToday ? "\u2705 \u5DF2\u6253\u5361" : "\u2B55 \u672A\u6253\u5361") + "\n" +
                "\u603B\u6253\u5361: " + totalCheckIns + " \u5929  |  \u6700\u957F\u8FDE\u7EED: " + maxStreak + " \u5929 \uD83C\uDFC6\n" +
                "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                "\uD83D\uDCB0 \u91D1\u5E01\u4F59\u989D: " + (balance != null ? balance : 0) + "\n" +
                "\uD83D\uDDE5 \u4ECA\u65E5\u6536\u5165: +" + todayEarned + "  |  \u4ECA\u65E5\u6D88\u8D39: -" + todaySpent + "\n" +
                "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                "\u5F85\u5BA1\u6279\u5151\u6362: " + pendingCount + " \u9879  |  \u5F85\u786E\u8BA4\u4EFB\u52A1: " + pendingTaskCount + " \u9879\n" +
                "\uD83D\uDCB0 \u5F85\u5BA1\u79EF\u5206: " + pendingEarningCount + " \u9879"
        );
    }

    // ================= 集成审批中心（v3.0.8） =================
    private static class ApprovalItem {
        int type;   // 0兑换 1积分 2任务 3洗衣
        String id;
        String title;
        String timeText;
        long ts;
    }
    /** 加载集成审批列表（兑换+积分+任务+洗衣 合并） */
    public void loadApprovalHub() {
        java.util.List<ApprovalItem> items = new java.util.ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        List<Redemption> reds = db.redemptionDao().getByStatus("pending");
        for (Redemption r : reds) {
            ApprovalItem it = new ApprovalItem();
            it.type = 0; it.id = r.id;
            it.title = "🪙 兑换 " + r.itemName + "（" + r.coinsCost + "金币）";
            it.ts = r.requestedAt;
            it.timeText = sdf.format(new Date(r.requestedAt));
            items.add(it);
        }
        List<com.sister.habits.data.models.CoinEarning> earns = db.coinEarningDao().getPending();
        for (com.sister.habits.data.models.CoinEarning e : earns) {
            if ("task".equals(e.sourceType)) continue;  // 任务类积分由任务审批(type2)统一处理，避免重复
            ApprovalItem it = new ApprovalItem();
            it.type = 1; it.id = e.id;
            it.title = "💰 +" + e.amount + "分 " + (e.description != null ? e.description : "额外积分");
            it.ts = e.requestedAt;
            it.timeText = sdf.format(new Date(e.requestedAt));
            items.add(it);
        }
        List<Task> tasks = db.taskDao().getPending();
        for (Task t : tasks) {
            ApprovalItem it = new ApprovalItem();
            it.type = 2; it.id = t.id;
            it.title = "📋 " + t.title + " 🪙+" + t.rewardCoins;
            it.ts = t.completedAt > 0 ? t.completedAt : t.createdAt;
            it.timeText = sdf.format(new Date(t.createdAt));
            items.add(it);
        }
        List<LaundryTask> laundries = db.laundryDao().getPending();
        for (LaundryTask lt : laundries) {
            ApprovalItem it = new ApprovalItem();
            it.type = 3; it.id = String.valueOf(lt.id);
            it.title = "🧺 " + lt.clothingType + " ×" + lt.quantity + " = " + lt.totalPoints + "分";
            it.ts = lt.submittedAt;
            it.timeText = sdf.format(new Date(lt.submittedAt));
            items.add(it);
        }
        List<DailyGate> gates = db.dailyGateDao().getPending();
        for (DailyGate g : gates) {
            ApprovalItem it = new ApprovalItem();
            it.type = 4; it.id = g.date;
            it.title = "📝 作业提交 " + g.date;
            it.ts = g.submittedAt;
            it.timeText = sdf.format(new Date(g.submittedAt));
            items.add(it);
        }
        items.sort((a, b) -> Long.compare(b.ts, a.ts));
        if (rvApprovalHub != null) {
            currentApprovalItems = items;
            rvApprovalHub.setAdapter(new ApprovalHubAdapter(items, selectedApprovalHub, this));
        }
        android.widget.TextView tvHub = findViewById(R.id.tv_hub_title);
        if (tvHub != null) {
            tvHub.setText("✅ 审批中心（" + items.size() + "项待处理 · 点击勾选 · 长按单项审批 · 支持批量）");
        }
    }
    /** 单项审批详情对话框（操作后刷新列表，不跳转） */
    public void showApprovalDetail(ApprovalItem item) {
        if (item.type == 0) {
            Redemption r = null;
            for (Redemption x : db.redemptionDao().getByStatus("pending")) { if (x.id.equals(item.id)) { r = x; break; } }
            if (r == null) return;
            final Redemption fr = r;
            new AlertDialog.Builder(this)
                    .setTitle("审批兑换申请")
                    .setMessage("兑换: " + r.itemName + "\n消耗: " + r.coinsCost + " 金币\n申请时间: " + item.timeText)
                    .setPositiveButton("✅ 确认", (d, w) -> { processApproval(fr, true); loadApprovalHub(); })
                    .setNegativeButton("❌ 拒绝", (d, w) -> { processApproval(fr, false); loadApprovalHub(); })
                    .setNeutralButton("稍后", null)
                    .show();
        } else if (item.type == 1) {
            com.sister.habits.data.models.CoinEarning e = null;
            for (com.sister.habits.data.models.CoinEarning x : db.coinEarningDao().getPending()) { if (x.id.equals(item.id)) { e = x; break; } }
            if (e == null) return;
            final com.sister.habits.data.models.CoinEarning fe = e;
            new AlertDialog.Builder(this)
                    .setTitle("审批积分申请")
                    .setMessage("金额: +" + e.amount + "分\n来源: " + e.sourceType + "\n" + item.timeText)
                    .setPositiveButton("✅ 确认", (d, w) -> { processEarningApproval(fe, true); loadApprovalHub(); })
                    .setNegativeButton("❌ 拒绝", (d, w) -> { processEarningApproval(fe, false); loadApprovalHub(); })
                    .setNeutralButton("稍后", null)
                    .show();
        } else if (item.type == 2) {
            Task t = null;
            for (Task x : db.taskDao().getPending()) { if (x.id.equals(item.id)) { t = x; break; } }
            if (t == null) return;
            final Task ft = t;
            new AlertDialog.Builder(this)
                    .setTitle("确认任务完成")
                    .setMessage("任务: " + t.title + "\n奖励: 🪙" + t.rewardCoins + "\n" + item.timeText)
                    .setPositiveButton("✅ 确认发金币", (d, w) -> { processTaskApproval(ft, true); loadApprovalHub(); })
                    .setNegativeButton("❌ 拒绝", (d, w) -> { processTaskApproval(ft, false); loadApprovalHub(); })
                    .setNeutralButton("稍后", null)
                    .show();
        } else if (item.type == 3) {
            LaundryTask lt = null;
            for (LaundryTask x : db.laundryDao().getPending()) { if (String.valueOf(x.id).equals(item.id)) { lt = x; break; } }
            if (lt == null) return;
            final LaundryTask flt = lt;
            new AlertDialog.Builder(this)
                    .setTitle("审批洗衣任务")
                    .setMessage("衣物: " + lt.clothingType + " ×" + lt.quantity + "\n积分: " + lt.totalPoints + "分\n" + item.timeText)
                    .setPositiveButton("✅ 通过", (d, w) -> { laundryApprove(flt, true); loadApprovalHub(); })
                    .setNegativeButton("❌ 拒绝", (d, w) -> { laundryApprove(flt, false); loadApprovalHub(); })
                    .setNeutralButton("稍后", null)
                    .show();
        } else if (item.type == 4) {
            DailyGate g = db.dailyGateDao().getByDate(item.id);
            if (g == null) return;
            final DailyGate fg = g;
            new AlertDialog.Builder(this)
                    .setTitle("审批作业提交")
                    .setMessage("作业日期: " + g.date + "\n提交时间: " + item.timeText)
                    .setPositiveButton("✅ 确认完成", (d, w) -> { gateApprove(fg, true); loadApprovalHub(); })
                    .setNegativeButton("❌ 未完成", (d, w) -> { gateApprove(fg, false); loadApprovalHub(); })
                    .setNeutralButton("稍后", null)
                    .show();
        }
    }
    /** 洗衣审批（批量与单项共用） */
    private void laundryApprove(LaundryTask task, boolean approved) {
        if (approved) {
            task.status = LaundryTask.STATUS_APPROVED;
            task.reviewedAt = System.currentTimeMillis();
            db.laundryDao().update(task);
            Integer balance = db.coinTransactionDao().getBalance("sister");
            int newBalance = (balance != null ? balance : 0) + task.totalPoints;
            com.sister.habits.data.models.CoinTransaction tx = new com.sister.habits.data.models.CoinTransaction(
                    "sister", task.totalPoints, newBalance, "laundry",
                    task.clothingType + "×" + task.quantity, syncManager.getDeviceId());
            db.coinTransactionDao().insert(tx);
        } else {
            task.status = LaundryTask.STATUS_REJECTED;
            task.reviewedAt = System.currentTimeMillis();
            db.laundryDao().update(task);
        }
        syncManager.onDataChanged();
    }

    /** 作业提交审批（批量与单项共用） */
    private void gateApprove(DailyGate gate, boolean approved) {
        if (approved) {
            gate.status = DailyGate.STATUS_COMPLETED;
        } else {
            gate.status = DailyGate.STATUS_INCOMPLETE;
        }
        db.dailyGateDao().update(gate);
    }
    /** 集成审批适配器：点击=勾选，长按=单项审批 */
    private static class ApprovalHubAdapter extends RecyclerView.Adapter<ApprovalHubAdapter.ViewHolder> {
        private final java.util.List<ApprovalItem> items;
        private final java.util.Set<String> selected;
        private final ParentActivity activity;
        ApprovalHubAdapter(java.util.List<ApprovalItem> items, java.util.Set<String> selected, ParentActivity activity) {
            this.items = items;
            this.selected = selected;
            this.activity = activity;
        }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ApprovalItem item = items.get(position);
            final String key = item.type + ":" + item.id;
            holder.textView.setText(item.title + "\n" + item.timeText);
            boolean isSel = selected.contains(key);
            holder.itemView.setActivated(isSel);
            ((android.widget.CheckedTextView) holder.textView).setChecked(isSel);
            holder.itemView.setOnClickListener(v -> {
                boolean now;
                if (selected.contains(key)) { selected.remove(key); now = false; }
                else { selected.add(key); now = true; }
                holder.itemView.setActivated(now);
                ((android.widget.CheckedTextView) holder.textView).setChecked(now);
            });
            holder.itemView.setOnLongClickListener(v -> {
                activity.showApprovalDetail(item);
                return true;
            });
        }
        @Override
        public int getItemCount() { return items.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textView;
            ViewHolder(View v) { super(v); textView = v.findViewById(android.R.id.text1); }
        }
    }
    public void loadPendingApprovals() {
        if (rvPendingApprovals == null) return;  // 集成审批模式已移除旧rv
        List<Redemption> pending = db.redemptionDao().getByStatus("pending");
        selectedApprovalIds.clear();
        rvPendingApprovals.setAdapter(new ApprovalAdapter(pending, selectedApprovalIds, this::processApproval));
    }

    private void processApproval(Redemption redemption, boolean approved) {
        if (approved) {
            // v3.0.61：审批通过 → 权威余额校验 + 扣款（防多设备双花）
            RedemptionApprovalService.ApproveResult result =
                    RedemptionApprovalService.approve(db.coinTransactionDao(), db.redemptionDao(),
                            redemption, syncManager.getDeviceId());
            syncManager.onDataChanged();
            refreshAll();
            if (result == RedemptionApprovalService.ApproveResult.APPROVED) {
                Toast.makeText(this, "✅ 已确认 " + redemption.itemName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ 余额不足，已自动拒绝「" + redemption.itemName + "」",
                        Toast.LENGTH_LONG).show();
            }
        } else {
            // v3.0.61：拒绝不再退款（孩子端提交时已不扣款，扣款在审批通过时）
            RedemptionApprovalService.reject(db.redemptionDao(), redemption);
            syncManager.onDataChanged();
            refreshAll();
            Toast.makeText(this, "❌ 已拒绝 " + redemption.itemName, Toast.LENGTH_SHORT).show();
        }
    }

    // ===== 任务审批 =====
    private void loadPendingTasks() {
        if (rvPendingTasks == null) return;  // 集成审批模式已移除旧rv
        List<Task> pending = db.taskDao().getPending();
        selectedTaskIds.clear();
        rvPendingTasks.setAdapter(new TaskApprovalAdapter(pending, selectedTaskIds, this::processTaskApproval));
    }
    /** 🧺 洗衣任务并入待确认任务列表 */
    private void showLaundryInApproval() {
        List<LaundryTask> laundryPending = db.laundryDao().getPending();
        if (laundryPending.isEmpty()) {
            Toast.makeText(this, "无待审核洗衣任务", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[laundryPending.size()];
        for (int i = 0; i < laundryPending.size(); i++) {
            LaundryTask t = laundryPending.get(i);
            labels[i] = "🧺 " + t.clothingType + " ×" + t.quantity + " = " + t.totalPoints + "分 (" + t.date + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle("🧺 待确认洗衣任务 (" + laundryPending.size() + "项)")
            .setItems(labels, (dialog, which) -> showLaundryApproveDialog(laundryPending.get(which)))
            .setNegativeButton("← 返回", null)
            .show();
    }
    /** ✅ 审批中心统一Hub（三区：兑换/积分/任务） */
    private void showApprovalHubDialog(int tab) {
        java.util.List<Redemption> reds = db.redemptionDao().getByStatus("pending");
        java.util.List<com.sister.habits.data.models.CoinEarning> earns = db.coinEarningDao().getPending();
        java.util.List<Task> tasks = db.taskDao().getPending();
        java.util.List<LaundryTask> laundries = db.laundryDao().getPending();
        final float den = getResources().getDisplayMetrics().density;
        final android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding((int)(14*den), (int)(10*den), (int)(14*den), (int)(10*den));
        final AlertDialog[] hubRef = new AlertDialog[1];
        // 区1: 兑换
        container.addView(makeHubSectionTitle("💳 待审批兑换（" + reds.size() + "）", den));
        if (reds.isEmpty()) container.addView(makeHubEmpty("暂无待审批兑换", den));
        for (Redemption r : reds) {
            final Redemption fr = r;
            container.addView(makeHubRow("🪙 " + r.itemName + "（" + r.coinsCost + "金币）", r.requestedAt,
                    () -> { processApproval(fr, true); if (hubRef[0] != null) hubRef[0].dismiss(); },
                    () -> { processApproval(fr, false); if (hubRef[0] != null) hubRef[0].dismiss(); }, den));
        }
        // 区2: 积分
        container.addView(makeHubSectionTitle("💰 待审批积分（" + earns.size() + "）", den));
        if (earns.isEmpty()) container.addView(makeHubEmpty("暂无待审批积分", den));
        for (com.sister.habits.data.models.CoinEarning e : earns) {
            final com.sister.habits.data.models.CoinEarning fe = e;
            String desc = e.description != null ? e.description : "额外积分";
            container.addView(makeHubRow("💰 +" + e.amount + "分 " + desc, e.requestedAt,
                    () -> { processEarningApproval(fe, true); if (hubRef[0] != null) hubRef[0].dismiss(); },
                    () -> { processEarningApproval(fe, false); if (hubRef[0] != null) hubRef[0].dismiss(); }, den));
        }
        // 区3: 任务（含洗衣）
        int taskTotal = tasks.size() + laundries.size();
        container.addView(makeHubSectionTitle("⏳ 待确认任务（" + taskTotal + "）", den));
        if (taskTotal == 0) container.addView(makeHubEmpty("暂无待确认任务", den));
        for (Task t : tasks) {
            final Task ft = t;
            container.addView(makeHubRow("📋 " + t.title + " 🪙+" + t.rewardCoins, t.createdAt,
                    () -> { processTaskApproval(ft, true); if (hubRef[0] != null) hubRef[0].dismiss(); },
                    () -> { processTaskApproval(ft, false); if (hubRef[0] != null) hubRef[0].dismiss(); }, den));
        }
        for (LaundryTask lt : laundries) {
            container.addView(makeHubRow("🧺 " + lt.clothingType + " ×" + lt.quantity + " = " + lt.totalPoints + "分", lt.submittedAt,
                    () -> showLaundryApproveDialog(lt), null, den));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(container);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("✅ 审批中心")
                .setView(sv)
                .setNegativeButton("关闭", null)
                .create();
        hubRef[0] = dlg;
        dlg.show();
    }
    /** Hub分区标题 */
    private TextView makeHubSectionTitle(String text, float den) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(0xFF2E7D32);
        tv.setPadding(0, (int)(10*den), 0, (int)(4*den));
        return tv;
    }
    /** Hub空提示 */
    private TextView makeHubEmpty(String text, float den) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF999999);
        tv.setPadding(0, 0, 0, (int)(8*den));
        return tv;
    }
    /** Hub审批行：文字 + ✅/❌ 按钮 */
    private android.widget.LinearLayout makeHubRow(String text, long time, Runnable onOk, Runnable onNo, float den) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int)(4*den), 0, (int)(4*den));
        android.widget.LinearLayout.LayoutParams rp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rp);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(text + (time > 0 ? "\n" + new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(time)) : ""));
        tv.setTextSize(14);
        tv.setTextColor(0xFF333333);
        android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tv.setLayoutParams(tp);
        row.addView(tv);
        if (onOk != null) {
            Button bOk = new Button(this);
            bOk.setText("✅");
            bOk.setTextSize(12);
            bOk.setAllCaps(false);
            bOk.setMinWidth(0); bOk.setMinHeight(0);
            bOk.setPadding((int)(6*den), 2, (int)(6*den), 2);
            bOk.setOnClickListener(v -> onOk.run());
            row.addView(bOk);
        }
        if (onNo != null) {
            Button bNo = new Button(this);
            bNo.setText("❌");
            bNo.setTextSize(12);
            bNo.setAllCaps(false);
            bNo.setMinWidth(0); bNo.setMinHeight(0);
            bNo.setPadding((int)(6*den), 2, (int)(6*den), 2);
            bNo.setOnClickListener(v -> onNo.run());
            row.addView(bNo);
        }
        return row;
    }
    /** 💰 加载待审批积分 */
    private void loadPendingEarnings() {
        if (rvPendingEarnings == null) return;  // 集成审批模式已移除旧rv
        List<com.sister.habits.data.models.CoinEarning> pendings = db.coinEarningDao().getPending();
        selectedEarningIds.clear();
        rvPendingEarnings.setAdapter(new EarningApprovalAdapter(pendings, selectedEarningIds, this::processEarningApproval));
    }
    private void processEarningApproval(com.sister.habits.data.models.CoinEarning e, boolean approved) {
        if (approved) {
            e.status = "confirmed";
            e.confirmedAt = System.currentTimeMillis();
            db.coinEarningDao().update(e);
            Integer balance = db.coinTransactionDao().getBalance("sister");
            int newBal = (balance != null ? balance : 0) + e.amount;
            com.sister.habits.data.models.CoinTransaction ct = new com.sister.habits.data.models.CoinTransaction(
                    "sister", e.amount, newBal, e.sourceType, e.description,
                    com.sister.habits.sync.SyncManager.getInstance(ParentActivity.this).getDeviceId());
            db.coinTransactionDao().insert(ct);
        } else {
            e.status = "rejected";
            e.rejectedAt = System.currentTimeMillis();
            db.coinEarningDao().update(e);
        }
        syncManager.onDataChanged();
        refreshAll();
        Toast.makeText(this, (approved ? "✅ 已确认 +" : "❌ 已拒绝 ") + e.amount + "分", Toast.LENGTH_SHORT).show();
    }
    /** 批量批准/拒绝 */
    private void approveSelected(boolean approved) {
        if (selectedApprovalHub.isEmpty()) {
            Toast.makeText(this, "请先在列表中勾选要审批的项目", Toast.LENGTH_SHORT).show();
            return;
        }
        int total = selectedApprovalHub.size();
        java.util.List<Redemption> reds = db.redemptionDao().getByStatus("pending");
        java.util.List<Task> tasks = db.taskDao().getPending();
        java.util.List<com.sister.habits.data.models.CoinEarning> earns = db.coinEarningDao().getPending();
        java.util.List<LaundryTask> laundries = db.laundryDao().getPending();
        for (String key : new java.util.ArrayList<>(selectedApprovalHub)) {
            processByKey(key, approved, reds, tasks, earns, laundries);
        }
        selectedApprovalHub.clear();
        loadApprovalHub();
        Toast.makeText(this, (approved ? "✅ 已批量批准 " : "❌ 已批量拒绝 ") + total + " 项", Toast.LENGTH_SHORT).show();
    }

    /** 按 key 分发到对应类型的审批处理（批量/全部共用） */
    private void processByKey(String key, boolean approved,
                              java.util.List<Redemption> reds, java.util.List<Task> tasks,
                              java.util.List<com.sister.habits.data.models.CoinEarning> earns,
                              java.util.List<LaundryTask> laundries) {
        String[] p = key.split(":", 2);
        if (p.length != 2) return;
        int type = Integer.parseInt(p[0]);
        String id = p[1];
        if (type == 0) { for (Redemption r : reds) if (r.id.equals(id)) processApproval(r, approved); }
        else if (type == 1) { for (com.sister.habits.data.models.CoinEarning e : earns) if (e.id.equals(id)) processEarningApproval(e, approved); }
        else if (type == 2) { for (Task t : tasks) if (t.id.equals(id)) processTaskApproval(t, approved); }
        else if (type == 3) { for (LaundryTask lt : laundries) if (String.valueOf(lt.id).equals(id)) laundryApprove(lt, approved); }
        else if (type == 4) { DailyGate g = db.dailyGateDao().getByDate(id); if (g != null) gateApprove(g, approved); }
    }

    /** ⚡ 一键全部批准/拒绝（不依赖勾选） */
    private void approveAll(boolean approved) {
        java.util.List<Redemption> reds = db.redemptionDao().getByStatus("pending");
        java.util.List<Task> tasks = db.taskDao().getPending();
        java.util.List<com.sister.habits.data.models.CoinEarning> earns = db.coinEarningDao().getPending();
        java.util.List<LaundryTask> laundries = db.laundryDao().getPending();
        int total = currentApprovalItems.size();
        if (total == 0) {
            Toast.makeText(this, "没有待审批的项目", Toast.LENGTH_SHORT).show();
            return;
        }
        // 先统计积分净变化（收入 - 兑换支出），再逐个处理
        int earnSum = 0;
        int spendSum = 0;
        if (approved) {
            for (ApprovalItem item : currentApprovalItems) {
                if (item.type == 0) {
                    for (Redemption r : reds) if (r.id.equals(item.id)) { spendSum += r.coinsCost; break; }
                } else if (item.type == 1) {
                    for (com.sister.habits.data.models.CoinEarning e : earns) if (e.id.equals(item.id)) { earnSum += e.amount; break; }
                } else if (item.type == 2) {
                    for (Task t : tasks) if (t.id.equals(item.id)) { earnSum += t.rewardCoins; break; }
                } else if (item.type == 3) {
                    for (LaundryTask lt : laundries) if (String.valueOf(lt.id).equals(item.id)) { earnSum += lt.totalPoints; break; }
                }
                // type4 作业提交：不直接产生积分变动，跳过
            }
        }
        for (ApprovalItem item : new java.util.ArrayList<>(currentApprovalItems)) {
            processByKey(item.type + ":" + item.id, approved, reds, tasks, earns, laundries);
        }
        selectedApprovalHub.clear();
        loadApprovalHub();
        if (approved) {
            int net = earnSum - spendSum;
            String netStr = net >= 0 ? "+" + net : String.valueOf(net);
            Toast.makeText(this, "✅ 已全部批准 " + total + " 项，今日积分净变化 " + netStr + "分", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "❌ 已全部拒绝 " + total + " 项", Toast.LENGTH_SHORT).show();
        }
    }

    private void processTaskApproval(Task task, boolean approved) {
        if (approved) {
            // 确认 → 发金币
            db.taskDao().confirmTask(task.id, System.currentTimeMillis());
            // 同步更新对应的CoinEarning状态
            java.util.List<com.sister.habits.data.models.CoinEarning> earnings = db.coinEarningDao().getBySource("task", task.id);
            for (com.sister.habits.data.models.CoinEarning e : earnings) {
                if ("pending".equals(e.status)) {
                    e.status = "confirmed";
                    e.confirmedAt = System.currentTimeMillis();
                    db.coinEarningDao().update(e);
                }
            }
            Integer balance = db.coinTransactionDao().getBalance("sister");
            int newBalance = (balance != null ? balance : 0) + task.rewardCoins;
            com.sister.habits.data.models.CoinTransaction ct =
                    new com.sister.habits.data.models.CoinTransaction(
                            "sister", task.rewardCoins, newBalance,
                            "task_reward", "任务奖励: " + task.title,
                            syncManager.getDeviceId());
            db.coinTransactionDao().insert(ct);
            String nickname = profile.getNickname();
            Toast.makeText(this, "✅ 已确认 " + task.title + "，" + nickname + "获得 🪙+" + task.rewardCoins, Toast.LENGTH_SHORT).show();
        } else {
            // 拒绝 → 退回待完成状态
            db.taskDao().reactivate(task.id);
            java.util.List<com.sister.habits.data.models.CoinEarning> taskEarns = db.coinEarningDao().getBySource("task", task.id);
            for (com.sister.habits.data.models.CoinEarning te : taskEarns) {
                if ("pending".equals(te.status)) {
                    te.status = "rejected";
                    te.confirmedAt = System.currentTimeMillis();
                    db.coinEarningDao().update(te);
                }
            }
            Toast.makeText(this, "❌ 已拒绝 " + task.title + "，任务退回", Toast.LENGTH_SHORT).show();
        }
        syncManager.onDataChanged();
        refreshAll();
    }

    private static class TaskApprovalAdapter extends RecyclerView.Adapter<TaskApprovalAdapter.ViewHolder> {
        private final List<Task> tasks;
        private final java.util.Set<String> selected;
        private final OnTaskApprovalListener listener;
        interface OnTaskApprovalListener { void onApprove(Task task, boolean approved); }
        TaskApprovalAdapter(List<Task> tasks, java.util.Set<String> selected, OnTaskApprovalListener listener) {
            this.tasks = tasks;
            this.selected = selected;
            this.listener = listener;
        }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Task task = tasks.get(position);
            holder.textView.setText("📋 " + task.title + "  🪙+" + task.rewardCoins);
            holder.itemView.setActivated(selected.contains(task.id));
            holder.itemView.setOnClickListener(v -> {
                if (selected.contains(task.id)) selected.remove(task.id);
                else selected.add(task.id);
                holder.itemView.setActivated(selected.contains(task.id));
            });
            holder.itemView.setOnLongClickListener(v -> {
                String nickname = ProfileManager.getInstance(v.getContext()).getNickname();
                new AlertDialog.Builder(v.getContext())
                        .setTitle("确认任务完成")
                        .setMessage("任务: " + task.title + "\n描述: " + task.description + "\n奖励: 🪙" + task.rewardCoins + "\n\n确认" + nickname + "已完成此任务吗？")
                        .setPositiveButton("✅ 确认发金币", (d, w) -> listener.onApprove(task, true))
                        .setNegativeButton("❌ 拒绝", (d, w) -> listener.onApprove(task, false))
                        .setNeutralButton("稍后", null)
                        .show();
                return true;
            });
        }
        @Override
        public int getItemCount() { return tasks.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textView;
            ViewHolder(View v) { super(v); textView = v.findViewById(android.R.id.text1); }
        }
    }
    /** 💰 待审批积分适配器（点击=勾选，长按=单项审批） */
    private static class EarningApprovalAdapter extends RecyclerView.Adapter<EarningApprovalAdapter.ViewHolder> {
        private final List<com.sister.habits.data.models.CoinEarning> items;
        private final java.util.Set<String> selected;
        private final OnEarningApprovalListener listener;
        interface OnEarningApprovalListener { void onApprove(com.sister.habits.data.models.CoinEarning item, boolean approved); }
        EarningApprovalAdapter(List<com.sister.habits.data.models.CoinEarning> items, java.util.Set<String> selected, OnEarningApprovalListener listener) {
            this.items = items;
            this.selected = selected;
            this.listener = listener;
        }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            com.sister.habits.data.models.CoinEarning item = items.get(position);
            String desc = item.description != null ? item.description : "未知";
            holder.textView.setText("💰 +" + item.amount + "分 " + desc);
            holder.itemView.setActivated(selected.contains(item.id));
            holder.itemView.setOnClickListener(v -> {
                if (selected.contains(item.id)) selected.remove(item.id);
                else selected.add(item.id);
                holder.itemView.setActivated(selected.contains(item.id));
            });
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("审批: " + desc)
                        .setMessage("金额: +" + item.amount + "分\n来源: " + item.sourceType)
                        .setPositiveButton("✅ 确认", (d, w) -> listener.onApprove(item, true))
                        .setNegativeButton("❌ 拒绝", (d, w) -> listener.onApprove(item, false))
                        .setNeutralButton("稍后", null)
                        .show();
                return true;
            });
        }
        @Override
        public int getItemCount() { return items.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textView;
            ViewHolder(View v) { super(v); textView = v.findViewById(android.R.id.text1); }
        }
    }

    

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("open_approval")) {
            String type = intent.getStringExtra("open_approval");
            int itemId = intent.getIntExtra("item_id", -1);
            showApprovalCenterDialog(type, itemId);
        }
    }

    /** 从通知直达审批中心 */
    private void showApprovalCenterDialog(String focusType, int focusId) {
        // 验证家长身份
        String parentKey = BindKeyManager.generateParentKey(this);
        if (!BindKeyManager.verifyParentKey(this)) {
            Toast.makeText(this, "⚠️ 家长身份未确认", Toast.LENGTH_SHORT).show();
            return;
        }
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        int pendingTaskCount = db.taskDao().getByStatus("pending").size();
        int earningPendingCount = db.coinEarningDao().getPendingCount();
        String[] items = {
            "💳 兑换审批（" + pendingCount + "项待处理）",
            "📋 任务确认（" + pendingTaskCount + "项待确认）",
            "💰 积分审批（" + earningPendingCount + "项待审）",
            "📜 历史记录"
        };
        new AlertDialog.Builder(this)
                .setTitle("✅ 审批中心")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: loadPendingApprovals(); Toast.makeText(this, "已刷新审批列表", Toast.LENGTH_SHORT).show(); break;
                        case 1: loadPendingTasks(); Toast.makeText(this, "已刷新任务列表", Toast.LENGTH_SHORT).show(); break;
                        case 2: showEarningApprovals(); break;
                        case 3: Toast.makeText(this, "历史记录（待实现）", Toast.LENGTH_SHORT).show(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
                .show();
    }

    /** 显示日期+时间选择器，更新按钮文字 */
    private void showDateTimePicker(android.widget.EditText etDate) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            java.util.Calendar dateCal = java.util.Calendar.getInstance();
            dateCal.set(year, month, day);
            // 选择时间
            new android.app.TimePickerDialog(this, (view2, hour, minute) -> {
                String dateStr = year + "-" + (month+1) + "-" + day + " " + hour + ":" + (minute < 10 ? "0"+minute : minute);
                etDate.setText("📅 " + dateStr);
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void showAddTaskDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        android.widget.EditText etTitle = view.findViewById(R.id.et_task_title);
        android.widget.EditText etDesc = view.findViewById(R.id.et_task_desc);
        android.widget.EditText etReward = view.findViewById(R.id.et_task_reward);
        RadioGroup rgType = view.findViewById(R.id.rg_task_type);
        com.google.android.material.textfield.TextInputLayout tilDeadline = view.findViewById(R.id.til_deadline);
        android.widget.EditText etDeadline = view.findViewById(R.id.et_task_deadline);

        // 选择"限时"时显示截止时间输入
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            view.findViewById(R.id.til_deadline).setVisibility(checkedId == R.id.rb_type_timed ? View.VISIBLE : View.GONE);
        });
        // 日期时间选择器（使用til_deadline的EditText点击触发）
        android.widget.EditText etDate = view.findViewById(R.id.et_task_deadline);
        etDate.setOnClickListener(v -> showDateTimePicker(etDate));
        etDate.setFocusable(false);

        new AlertDialog.Builder(this)
                .setTitle("发布新任务")
                .setView(view)
                .setPositiveButton("发布", (d, w) -> {
                    Task task = new Task();
                    task.title = etTitle.getText().toString();
                    task.description = etDesc.getText().toString();
                    try { task.rewardCoins = Integer.parseInt(etReward.getText().toString()); }
                    catch (Exception e) { task.rewardCoins = 10; }
                    task.type = "custom";
                    // 读取任务类型
                    int typeId = rgType.getCheckedRadioButtonId();
                    if (typeId == R.id.rb_type_weekly) task.recurrenceType = "weekly";
                    else if (typeId == R.id.rb_type_monthly) task.recurrenceType = "monthly";
                    else if (typeId == R.id.rb_type_permanent) task.recurrenceType = "permanent";
                    else if (typeId == R.id.rb_type_timed) {
                        task.recurrenceType = "timed";
                        // 解析截止时间
                        String deadlineStr = etDeadline.getText().toString().trim();
                        if (!deadlineStr.isEmpty()) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA);
                                java.util.Date deadlineDate = sdf.parse(deadlineStr);
                                task.deadline = deadlineDate.getTime();
                            } catch (Exception ex) {
                                Toast.makeText(this, "⚠️ 日期格式错误，将使用默认7天后", Toast.LENGTH_SHORT).show();
                                task.deadline = System.currentTimeMillis() + 7L * 24 * 3600 * 1000;
                            }
                        } else {
                            task.deadline = System.currentTimeMillis() + 7L * 24 * 3600 * 1000; // 默认7天
                        }
                    } else {
                        task.recurrenceType = "once";
                    }
                    task.deviceId = syncManager.getDeviceId();
                    db.taskDao().insert(task);
                    syncManager.onDataChanged();
                    Toast.makeText(this, "任务已发布 🎯 (" + task.recurrenceType + ")", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }



    /**
     * 家长管理主菜单 — 按层级重新组织
     * 一级：总览 | 学习 | 商城 | 任务 | 系统
     */
    /** 商城模块（委托 ShopManager） */
    private void showShopMenu() { shopManager.showShopMenu(); }
    private void showAddShopItemDialog() { shopManager.showAddShopItemDialog(); }
    private void showManageShopDialog() { shopManager.showManageShopDialog(); }
    private void showEditShopItemDialog(ShopItem item) { shopManager.showEditShopItemDialog(item); }


    /** ❤️ 打赏支持（低调入口：家长端界面底部小字，点击展示收款码） */
    private void showDonateDialog() {
        soundHelper.playClickSound();
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(android.view.Gravity.CENTER);
        container.setPadding(32, 16, 32, 16);

        // 微信收款码
        TextView wxLabel = new TextView(this);
        wxLabel.setText("微信支付");
        wxLabel.setTextSize(13);
        wxLabel.setTextColor(0xFF07C160);
        wxLabel.setGravity(android.view.Gravity.CENTER);
        wxLabel.setPadding(0, 4, 0, 8);
        ImageView wxImg = new ImageView(this);
        wxImg.setImageResource(R.drawable.donate_wechat);
        wxImg.setAdjustViewBounds(true);
        wxImg.setMaxWidth(720);
        container.addView(wxLabel);
        container.addView(wxImg);

        // 支付宝收款码
        TextView aliLabel = new TextView(this);
        aliLabel.setText("支付宝");
        aliLabel.setTextSize(13);
        aliLabel.setTextColor(0xFF1677FF);
        aliLabel.setGravity(android.view.Gravity.CENTER);
        aliLabel.setPadding(0, 20, 0, 8);
        ImageView aliImg = new ImageView(this);
        aliImg.setImageResource(R.drawable.donate_alipay);
        aliImg.setAdjustViewBounds(true);
        aliImg.setMaxWidth(720);
        container.addView(aliLabel);
        container.addView(aliImg);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(container);

        new AlertDialog.Builder(this)
                .setTitle("💖 打赏支持一下开发者吧")
                .setMessage("个人开发者做这个App不容易，你的支持是我最大的鼓励～")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }
    public void showSettingsDialog() {
        soundHelper.playClickSound();
        int pendingTotal = db.redemptionDao().getByStatus("pending").size()
            + db.taskDao().getByStatus("pending").size()
            + db.coinEarningDao().getPendingCount()
            + db.laundryDao().getPending().size();
        String[] mainLabels = {
                "📚 学习管理",
                "🏪 商城管理",
                "📋 任务与作业",
                "🧺 洗衣任务",
                "🎰 抽奖管理",
                "🏆 学校奖励",
                "💰 积分账单",
            "⚙️ 系统设置"
        };
        com.sister.habits.utils.MenuHelper.show(this, "📱 家长管理中心", mainLabels,
                this::showLearningMenu,
                this::showShopMenu,
                this::showTaskMenu,
                this::showLaundryManageDialog,
                this::showLotteryManageDialog,
                this::showSchoolRewardDialog,
                this::showCoinBillDialog,
                this::showSystemMenu
        );
    }

    /** 💰 积分账单（历史流水明细） */
    private void showCoinBillDialog() {
        soundHelper.playClickSound();
        java.util.List<com.sister.habits.data.models.CoinTransaction> bills =
                db.coinTransactionDao().getRecent("sister", 200);
        if (bills == null || bills.isEmpty()) {
            Toast.makeText(this, "暂无积分流水记录", Toast.LENGTH_SHORT).show();
            return;
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        int income = 0, spend = 0;
        for (com.sister.habits.data.models.CoinTransaction ct : bills) {
            if (ct.amount >= 0) income += ct.amount; else spend += -ct.amount;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("💰 总收入 ").append(income).append(" 分  ·  支出 ").append(spend).append(" 分  ·  当前余额 ")
          .append(db.coinTransactionDao().getBalance("sister")).append(" 分\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        int shown = 0;
        for (com.sister.habits.data.models.CoinTransaction ct : bills) {
            if (shown >= 100) { sb.append("……（更早的记录省略）\n"); break; }
            String sign = ct.amount >= 0 ? "+" : "";
            String desc = ct.description != null && !ct.description.isEmpty() ? ct.description : ct.type;
            sb.append(sdf.format(new java.util.Date(ct.createdAt))).append("  ")
              .append(sign).append(ct.amount).append("  ")
              .append(desc).append("  ")
              .append("(余").append(ct.balanceAfter).append(")\n");
            shown++;
        }
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setTextSize(13);
        tv.setTextColor(0xFF333333);
        tv.setPadding(32, 24, 32, 24);
        tv.setText(sb.toString());
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(tv);
        new AlertDialog.Builder(this)
            .setTitle("💰 积分账单（近100条）")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .setNegativeButton("← 返回", (d, w) -> showSettingsDialog())
            .show();
    }

    /** 二级菜单：📊 总览与审批 */
    private void showDashboardMenu() {
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        int pendingTaskCount = db.taskDao().getByStatus("pending").size();
        int earningPendingCount = db.coinEarningDao().getPendingCount();
        String[] items = {
                "📊 查看统计数据",
                "✅ 兑换审批（" + pendingCount + "项待处理）",
                "📋 任务审批（" + pendingTaskCount + "项待确认）",
                "🔄 手动同步"
        };
        new AlertDialog.Builder(this)
                .setTitle("📊 总览与审批")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: refreshStats(); Toast.makeText(this, tvStats.getText(), Toast.LENGTH_LONG).show(); break;
                        case 1: loadPendingApprovals(); Toast.makeText(this, "已刷新审批列表", Toast.LENGTH_SHORT).show(); break;
                        case 2: loadPendingTasks(); Toast.makeText(this, "已刷新任务列表", Toast.LENGTH_SHORT).show(); break;
                        case 3: syncManager.triggerFullSyncAsync(); Toast.makeText(this, "全同步已触发（Hub+局域网+云端）", Toast.LENGTH_SHORT).show(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
                .show();
    }

    /** 二级菜单：📚 学习管理 */
    private void showLearningMenu() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        int dailyWords = config != null ? config.maxDailyWords : 10;
        int dailyReview = config != null ? config.maxDailyReview : 20;
        String[] learnLabels = {
                "📖 词库管理（年级/下载/切换）",
                "📝 每日学习限额（新词:" + dailyWords + " 复习:" + dailyReview + "）",
                "💰 学习奖励参数"
        };
        com.sister.habits.utils.MenuHelper.showWithBack(this, "📚 学习管理", learnLabels,
                this::showSettingsDialog,
                this::showWordbankDialog,
                this::showLearningLimitDialog,
                this::showLearningRewardDialog
        );
    }

    /** 📝 每日学习限额 */
    private void showLearningLimitDialog() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        EconomyConfig fc = config;
        View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        android.widget.EditText etWords = new android.widget.EditText(this);
        etWords.setHint("每日新词上限");
        etWords.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etWords.setText(String.valueOf(fc.maxDailyWords));
        android.widget.EditText etReview = new android.widget.EditText(this);
        etReview.setHint("每日复习上限");
        etReview.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etReview.setText(String.valueOf(fc.maxDailyReview));
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(32,16,32,16);
        ll.addView(etWords);
        ll.addView(etReview);
        new AlertDialog.Builder(this)
                .setTitle("📝 每日学习限额")
                .setView(ll)
                .setPositiveButton("保存", (d,w2)->{
                    fc.maxDailyWords = parseInt(etWords,10);
                    fc.maxDailyReview = parseInt(etReview,20);
                    fc.updatedAt = System.currentTimeMillis();  // LWW时间戳（v3.0.62）
                    db.economyConfigDao().setConfig(fc);
                    Toast.makeText(this,"学习限额已更新",Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回上级",(d,w2)->showLearningMenu()).show();
    }

    /** 💰 学习奖励参数 */
    private void showLearningRewardDialog() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        EconomyConfig fc = config;
        android.widget.EditText etLearn = new android.widget.EditText(this);
        etLearn.setHint("新词答对奖励");
        etLearn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etLearn.setText(String.valueOf(fc.wordLearnReward));
        android.widget.EditText etPass = new android.widget.EditText(this);
        etPass.setHint("复习通关奖励");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPass.setText(String.valueOf(fc.reviewPassReward));
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(32,16,32,16);
        ll.addView(etLearn);
        ll.addView(etPass);
        new AlertDialog.Builder(this)
                .setTitle("💰 学习奖励参数")
                .setView(ll)
                .setPositiveButton("保存", (d,w2)->{
                    fc.wordLearnReward = parseInt(etLearn,2);
                    fc.reviewPassReward = parseInt(etPass,2);
                    fc.updatedAt = System.currentTimeMillis();  // LWW时间戳（v3.0.62）
                    db.economyConfigDao().setConfig(fc);
                    Toast.makeText(this,"学习奖励已更新",Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回上级",(d,w2)->showLearningMenu()).show();
    }

    /** 📥 AI批量导入（读取AI生成的 items.json + 图片） */

    /** 递归删除（清理导入目录） */


    /** 读取文件为UTF-8字符串（兼容API24） */
    private String readFileAsString(java.io.File f) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
        fis.close();
        return new String(bos.toByteArray(), "UTF-8");
    }
    /** 二级菜单：🏪 商城管理 */


    /** 二级菜单：📋 任务管理 */
    private void showTaskMenu() {
        int pendingCount = db.taskDao().getByStatus("pending").size();
        String[] taskLabels = {
                "📋 任务列表（管理/删除任务）",
                "➕ 发布新任务",
                "⏳ 待确认任务（" + pendingCount + "项）",
                "📋 任务模板库",
                "📝 作业管理（审核/截止日期）"
        };
        com.sister.habits.utils.MenuHelper.showWithBack(this, "📋 任务与作业", taskLabels,
                this::showSettingsDialog,
                this::showManageTasksDialog,
                this::showAddTaskDialog,
                () -> { loadPendingTasks(); Toast.makeText(this, "已刷新任务列表", Toast.LENGTH_SHORT).show(); },
                this::showTaskTemplates,
                this::showGateManageDialog
        );
    }

    /** 📋 作业管理——关卡打折系统（自定义布局，避免 setMessage+setItems 兼容问题） */
    private void showGateManageDialog() {
        soundHelper.playClickSound();
        com.sister.habits.data.models.GateConfig config = db.gateConfigDao().getConfig();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(new java.util.Date());
        com.sister.habits.data.models.DailyGate todayGate = db.dailyGateDao().getByDate(today);

        String todayStatus = "未设置";
        if (todayGate != null) {
            switch (todayGate.status) {
                case "COMPLETED": todayStatus = "✅ 已完成"; break;
                case "INCOMPLETE": todayStatus = "❌ 未完成（明天打折）"; break;
                case "AI_DETECTED": todayStatus = "🤖 AI作弊（明天打折）"; break;
                case "SKIPPED": todayStatus = "🏥 已免检"; break;
                default: todayStatus = "⏳ 待审核";
            }
        }

        double multiplier = com.sister.habits.utils.GateHelper.getTodayMultiplier(this);
        String ml = "×" + String.format("%.0f%%", multiplier * 100);
        if (multiplier >= 1.0) ml = "正常";

        // ===== 自定义布局：状态文字 + 4个操作按钮 =====
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        android.widget.TextView tvStatus = new android.widget.TextView(this);
        tvStatus.setText("📅 今日状态: " + todayStatus + "\n📊 今日积分乘数: " + ml);
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(0xFF333333);
        tvStatus.setPadding(0, 0, 0, 16);
        layout.addView(tvStatus);

        String[] labels = {
            "⚙️ 假期配置（日期范围/周末开关）",
            "✏️ 审核今日作业",
            "🏖 赦免配置（外出/旅行免检）",
            (config != null && config.enabled ? "🔴 关闭打折系统" : "🟢 开启打折系统")
        };
        Runnable[] actions = new Runnable[]{
            this::showGateConfigDialog,
            this::showTodayGateReviewDialog,
            this::showExcuseConfigDialog,
            () -> {
                if (config != null) {
                    config.enabled = !config.enabled;
                    config.updatedAt = System.currentTimeMillis();
                    db.gateConfigDao().update(config);
                    syncManager.onDataChanged();
                    Toast.makeText(this, config.enabled ? "🟢 打折系统已开启" : "🔴 打折系统已关闭", Toast.LENGTH_SHORT).show();
                }
                showGateManageDialog(); // 留在作业管理，继续操作
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            android.widget.Button btn = new android.widget.Button(this);
            btn.setText(labels[i]);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> actions[idx].run());
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 4, 0, 4);
            btn.setLayoutParams(lp);
            layout.addView(btn);
        }

        new AlertDialog.Builder(this)
            .setTitle("📋 作业管理")
            .setView(layout)
            .setNegativeButton("← 返回上级", (d2, w2) -> showSettingsDialog())
            .show();
    }

    /** 🏖 赦免配置对话框（外出/旅行等特殊情况免检，不打折） */
    private void showExcuseConfigDialog() {
        soundHelper.playClickSound();
        com.sister.habits.data.models.GateConfig cfg = db.gateConfigDao().getConfig();
        if (cfg == null) {
            cfg = new com.sister.habits.data.models.GateConfig();
            db.gateConfigDao().insert(cfg);
        }
        final com.sister.habits.data.models.GateConfig config = cfg;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        android.widget.TextView tvTip = new android.widget.TextView(this);
        tvTip.setText("在赦免日期内，即使前一天未提交作业，今天也不打折（外出/旅行/生病等）");
        tvTip.setTextSize(13);
        tvTip.setTextColor(0xFF666666);
        tvTip.setPadding(0, 0, 0, 12);
        layout.addView(tvTip);

        // ⚡ 快速赦免今天：一键把今日作业标记为免检（明天积分不打折）
        android.widget.Button btnExcuseToday = new android.widget.Button(this);
        btnExcuseToday.setText("⚡ 快速赦免今天（今日作业免检）");
        btnExcuseToday.setAllCaps(false);
        btnExcuseToday.setOnClickListener(v -> {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(new java.util.Date());
            com.sister.habits.data.models.DailyGate g = db.dailyGateDao().getByDate(today);
            if (g == null) {
                g = new com.sister.habits.data.models.DailyGate();
                g.date = today;
                g.status = com.sister.habits.data.models.DailyGate.STATUS_PENDING;
                db.dailyGateDao().insert(g);
            }
            if (com.sister.habits.data.models.DailyGate.STATUS_SKIPPED.equals(g.status)) {
                Toast.makeText(this, "今日作业已是免检状态", Toast.LENGTH_SHORT).show();
            } else if (com.sister.habits.data.models.DailyGate.STATUS_COMPLETED.equals(g.status)) {
                Toast.makeText(this, "今日作业已完成，无需赦免", Toast.LENGTH_SHORT).show();
            } else {
                g.status = com.sister.habits.data.models.DailyGate.STATUS_SKIPPED;
                g.reviewedAt = System.currentTimeMillis();
                g.isLateSubmission = false;
                g.deviceId = syncManager.getDeviceId();
                g.synced = false;
                g.syncTimestamp = System.currentTimeMillis();
                db.dailyGateDao().insert(g);
                syncManager.onDataChanged();
                Toast.makeText(this, "⚡ 今日作业已赦免，明天积分正常", Toast.LENGTH_SHORT).show();
            }
        });
        android.widget.LinearLayout.LayoutParams excuseBtnLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        excuseBtnLp.setMargins(0, 0, 0, 8);
        btnExcuseToday.setLayoutParams(excuseBtnLp);
        layout.addView(btnExcuseToday);

        // 解析已有赦免范围
        java.util.List<String[]> excuseList = new java.util.ArrayList<>();
        String exJson = config.excuseRanges;
        if (exJson != null && !exJson.isEmpty() && !"[]".equals(exJson)) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.sister.habits.utils.GateHelper.HolidayRange>>(){}.getType();
                java.util.List<com.sister.habits.utils.GateHelper.HolidayRange> exs = new com.google.gson.Gson().fromJson(exJson, listType);
                if (exs != null) {
                    for (com.sister.habits.utils.GateHelper.HolidayRange hr : exs) {
                        excuseList.add(new String[]{hr.start, hr.end});
                    }
                }
            } catch (Exception ignored) {}
        }
        final java.util.List<String[]> finalExcuseList = excuseList;

        android.widget.TextView tvRanges = new android.widget.TextView(this);
        tvRanges.setTextSize(13);
        tvRanges.setPadding(8, 4, 8, 4);
        tvRanges.setTextColor(0xFF333333);
        updateExcuseRangesText(tvRanges, finalExcuseList);
        layout.addView(tvRanges);

        // 添加按钮
        android.widget.Button btnAdd = new android.widget.Button(this);
        btnAdd.setText("➕ 添加赦免范围");
        btnAdd.setOnClickListener(v -> {
            java.util.Calendar calStart = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                String startDate = year + "-" + String.format("%02d", month+1) + "-" + String.format("%02d", day);
                new android.app.DatePickerDialog(this, (view2, year2, month2, day2) -> {
                    String endDate = year2 + "-" + String.format("%02d", month2+1) + "-" + String.format("%02d", day2);
                    finalExcuseList.add(new String[]{startDate, endDate});
                    updateExcuseRangesText(tvRanges, finalExcuseList);
                }, calStart.get(java.util.Calendar.YEAR), calStart.get(java.util.Calendar.MONTH), calStart.get(java.util.Calendar.DAY_OF_MONTH)).show();
            }, calStart.get(java.util.Calendar.YEAR), calStart.get(java.util.Calendar.MONTH), calStart.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(btnAdd);

        // 清除按钮
        android.widget.Button btnClear = new android.widget.Button(this);
        btnClear.setText("🗑 清除所有赦免");
        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("确认")
                .setMessage("确定清除所有赦免范围？")
                .setPositiveButton("确定", (dd, ww) -> {
                    finalExcuseList.clear();
                    updateExcuseRangesText(tvRanges, finalExcuseList);
                })
                .setNegativeButton("取消", null).show();
        });
        layout.addView(btnClear);

        new AlertDialog.Builder(this)
            .setTitle("🏖 赦免配置")
            .setView(layout)
            .setPositiveButton("💾 保存", (d2, w2) -> {
                if (finalExcuseList.isEmpty()) {
                    config.excuseRanges = "[]";
                } else {
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < finalExcuseList.size(); i++) {
                        if (i > 0) sb.append(",");
                        String[] range = finalExcuseList.get(i);
                        sb.append("{\"start\":\"").append(range[0]).append("\",\"end\":\"").append(range[1]).append("\"}");
                    }
                    sb.append("]");
                    config.excuseRanges = sb.toString();
                }
                config.updatedAt = System.currentTimeMillis();
                config.deviceId = syncManager.getDeviceId();
                db.gateConfigDao().update(config);
                syncManager.onDataChanged();
                Toast.makeText(this, "✅ 赦免配置已保存", Toast.LENGTH_SHORT).show();
                showGateManageDialog(); // 留在作业管理，继续操作
            })
            .setNegativeButton("← 返回", (d2, w2) -> showGateManageDialog())
            .show();
    }

    /** 更新赦免范围显示文本 */
    private void updateExcuseRangesText(android.widget.TextView tv, java.util.List<String[]> list) {
        if (list.isEmpty()) {
            tv.setText("（暂无赦免日期）");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            String[] r = list.get(i);
            if (i > 0) sb.append("\n");
            sb.append("🏖 ").append(r[0]).append(" ~ ").append(r[1]);
        }
        tv.setText(sb.toString());
    }

    /** ⚙️ 假期配置对话框 */
    private void showGateConfigDialog() {
        soundHelper.playClickSound();
        com.sister.habits.data.models.GateConfig cfg = db.gateConfigDao().getConfig();
        if (cfg == null) {
            cfg = new com.sister.habits.data.models.GateConfig();
            db.gateConfigDao().insert(cfg);
        }
        final com.sister.habits.data.models.GateConfig config = cfg;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        // 开关
        android.widget.CheckBox cbWeekend = new android.widget.CheckBox(this);
        cbWeekend.setText("🗓 周末启用打折制");
        cbWeekend.setChecked(config.weekendMode);
        layout.addView(cbWeekend);

        // 截止时间
        android.widget.TextView tvDeadline = new android.widget.TextView(this);
        tvDeadline.setText("⏰ 截止时间（HH:mm）:");
        tvDeadline.setPadding(0, 20, 0, 4);
        layout.addView(tvDeadline);
        android.widget.EditText etDeadline = new android.widget.EditText(this);
        etDeadline.setText(config.deadlineTime != null ? config.deadlineTime : "12:00");
        etDeadline.setHint("如 12:00");
        layout.addView(etDeadline);

        // 完成奖励
        android.widget.TextView tvReward = new android.widget.TextView(this);
        tvReward.setText("🎁 完成奖励分:");
        tvReward.setPadding(0, 20, 0, 4);
        layout.addView(tvReward);
        android.widget.EditText etReward = new android.widget.EditText(this);
        etReward.setText(String.valueOf(config.completionReward));
        etReward.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etReward);

        // 惩罚比例
        android.widget.TextView tvPenalty = new android.widget.TextView(this);
        tvPenalty.setText("📉 未完成打折（%）:");
        tvPenalty.setPadding(0, 20, 0, 4);
        layout.addView(tvPenalty);
        android.widget.EditText etPenalty = new android.widget.EditText(this);
        etPenalty.setText(String.valueOf(config.defaultPenaltyPercent));
        etPenalty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etPenalty);

        // 补交减免
        android.widget.TextView tvMakeup = new android.widget.TextView(this);
        tvMakeup.setText("🔧 补交减免（%）:");
        tvMakeup.setPadding(0, 20, 0, 4);
        layout.addView(tvMakeup);
        android.widget.EditText etMakeup = new android.widget.EditText(this);
        etMakeup.setText(String.valueOf(config.makeupPercent));
        etMakeup.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etMakeup);

        // 假期范围（日期选择器）
        android.widget.TextView tvHoliday = new android.widget.TextView(this);
        tvHoliday.setText("📅 假期范围:");
        tvHoliday.setPadding(0, 20, 0, 4);
        layout.addView(tvHoliday);

        // 解析已有假期范围
        java.util.List<String[]> holidayList = new java.util.ArrayList<>();
        String hrJson = config.holidayRanges;
        if (hrJson != null && !hrJson.isEmpty() && !"[]".equals(hrJson)) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.sister.habits.utils.GateHelper.HolidayRange>>(){}.getType();
                java.util.List<com.sister.habits.utils.GateHelper.HolidayRange> hrs = new com.google.gson.Gson().fromJson(hrJson, listType);
                if (hrs != null) {
                    for (com.sister.habits.utils.GateHelper.HolidayRange hr : hrs) {
                        holidayList.add(new String[]{hr.start, hr.end});
                    }
                }
            } catch (Exception ignored) {}
        }
        final java.util.List<String[]> finalHolidayList = holidayList;

        // 显示已有范围
        android.widget.TextView tvRanges = new android.widget.TextView(this);
        tvRanges.setTextSize(13);
        tvRanges.setPadding(8, 4, 8, 4);
        tvRanges.setTextColor(0xFF333333);
        updateHolidayRangesText(tvRanges, finalHolidayList);
        layout.addView(tvRanges);

        // 添加按钮
        android.widget.Button btnAddRange = new android.widget.Button(this);
        btnAddRange.setText("➕ 添加假期范围");
        btnAddRange.setOnClickListener(v -> {
            java.util.Calendar calStart = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                String startDate = year + "-" + String.format("%02d", month+1) + "-" + String.format("%02d", day);
                new android.app.DatePickerDialog(this, (view2, year2, month2, day2) -> {
                    String endDate = year2 + "-" + String.format("%02d", month2+1) + "-" + String.format("%02d", day2);
                    finalHolidayList.add(new String[]{startDate, endDate});
                    updateHolidayRangesText(tvRanges, finalHolidayList);
                }, calStart.get(java.util.Calendar.YEAR), calStart.get(java.util.Calendar.MONTH), calStart.get(java.util.Calendar.DAY_OF_MONTH)).show();
            }, calStart.get(java.util.Calendar.YEAR), calStart.get(java.util.Calendar.MONTH), calStart.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(btnAddRange);

        // 清除按钮
        android.widget.Button btnClearRanges = new android.widget.Button(this);
        btnClearRanges.setText("🗑 清除所有假期");
        btnClearRanges.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("确认")
                .setMessage("确定清除所有假期范围？")
                .setPositiveButton("确定", (dd, ww) -> {
                    finalHolidayList.clear();
                    updateHolidayRangesText(tvRanges, finalHolidayList);
                })
                .setNegativeButton("取消", null).show();
        });
        layout.addView(btnClearRanges);

        new AlertDialog.Builder(this)
            .setTitle("⚙️ 假期配置")
            .setView(layout)
            .setPositiveButton("💾 保存", (d2, w2) -> {
                config.weekendMode = cbWeekend.isChecked();
                config.deadlineTime = etDeadline.getText().toString().trim();
                try { config.completionReward = Integer.parseInt(etReward.getText().toString()); } catch (Exception e) {}
                try { config.defaultPenaltyPercent = Integer.parseInt(etPenalty.getText().toString()); } catch (Exception e) {}
                try { config.makeupPercent = Integer.parseInt(etMakeup.getText().toString()); } catch (Exception e) {}
                // 将假期列表转为JSON
                if (finalHolidayList.isEmpty()) {
                    config.holidayRanges = "[]";
                } else {
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < finalHolidayList.size(); i++) {
                        if (i > 0) sb.append(",");
                        String[] range = finalHolidayList.get(i);
                        sb.append("{\"start\":\"").append(range[0]).append("\",\"end\":\"").append(range[1]).append("\"}");
                    }
                    sb.append("]");
                    config.holidayRanges = sb.toString();
                }
                config.updatedAt = System.currentTimeMillis();
                config.deviceId = syncManager.getDeviceId();
                db.gateConfigDao().update(config);
                syncManager.onDataChanged();
                Toast.makeText(this, "✅ 配置已保存", Toast.LENGTH_SHORT).show();
                showGateManageDialog(); // 留在作业管理，继续操作
            })
            .setNegativeButton("← 返回", (d2, w2) -> showGateManageDialog())
            .show();
    }

    /** ✏️ 审核今日作业 */
    private void showTodayGateReviewDialog() {
        soundHelper.playClickSound();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(new java.util.Date());
        com.sister.habits.data.models.DailyGate gt = db.dailyGateDao().getByDate(today);
        if (gt == null) {
            gt = new com.sister.habits.data.models.DailyGate();
            gt.date = today;
            gt.status = com.sister.habits.data.models.DailyGate.STATUS_PENDING;
            db.dailyGateDao().insert(gt);
        }
        final com.sister.habits.data.models.DailyGate gate = gt;

        String currentStatus = "⏳ 待审核";
        switch (gate.status) {
            case "COMPLETED": currentStatus = "✅ 已完成"; break;
            case "INCOMPLETE": currentStatus = "❌ 未完成"; break;
            case "AI_DETECTED": currentStatus = "🤖 AI作弊"; break;
            case "SKIPPED": currentStatus = "🏥 已免检"; break;
        }

        String nickname = profile.getNickname();
        com.sister.habits.data.models.GateConfig gateCfg = db.gateConfigDao().getConfig();
        if (gateCfg == null) {
            Toast.makeText(this, "请先在「作业管理 → 假期配置」保存一次配置", Toast.LENGTH_LONG).show();
            showGateManageDialog();
            return;
        }
        String[] items = {
            "✅ 确认完成（+" + gateCfg.completionReward + "分）",
            "❌ 标记未完成（明天打" + gateCfg.defaultPenaltyPercent + "折）",
            "🤖 AI作弊（明天打" + gateCfg.defaultPenaltyPercent + "折，不获得作业分）",
            "🏥 免检（生病/外出）",
            "📝 补交（明天减免至" + gateCfg.makeupPercent + "折）"
        };

        // 自定义布局：TextView状态 + 实体Button，规避 vivo setMessage+setItems 列表不渲染
        android.widget.LinearLayout reviewLayout = new android.widget.LinearLayout(this);
        reviewLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        reviewLayout.setPadding(40, 20, 40, 20);
        android.widget.TextView tvStatus = new android.widget.TextView(this);
        tvStatus.setText("当前状态: " + currentStatus);
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(0xFF333333);
        tvStatus.setPadding(0, 0, 0, 12);
        reviewLayout.addView(tvStatus);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            android.widget.Button btn = new android.widget.Button(this);
            btn.setText(items[i]);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> {
                switch (which) {
                    case 0: // 确认完成（防重复发奖：已是完成状态则拦截）
                        if (com.sister.habits.data.models.DailyGate.STATUS_COMPLETED.equals(gate.status)) {
                            Toast.makeText(this, "今日作业已确认完成，请勿重复操作（不会重复发分）", Toast.LENGTH_SHORT).show();
                            break;
                        }
                        gate.status = com.sister.habits.data.models.DailyGate.STATUS_COMPLETED;
                        gate.reviewedAt = System.currentTimeMillis();
                        gate.isLateSubmission = false;
                        // 发放作业完成奖
                        com.sister.habits.data.models.GateConfig cfg = db.gateConfigDao().getConfig();
                        if (cfg != null && cfg.completionReward > 0) {
                            Integer bal = db.coinTransactionDao().getBalance("sister");
                            int newBal = (bal != null ? bal : 0) + cfg.completionReward;
                            com.sister.habits.data.models.CoinTransaction ct =
                                new com.sister.habits.data.models.CoinTransaction(
                                    "sister", cfg.completionReward, newBal,
                                    "task_reward", "作业完成奖",
                                    syncManager.getDeviceId());
                            db.coinTransactionDao().insert(ct);
                            Toast.makeText(this, "✅ 作业完成！" + nickname + "获得 🪙+" + cfg.completionReward, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 1: // 标记未完成
                        gate.status = com.sister.habits.data.models.DailyGate.STATUS_INCOMPLETE;
                        gate.reviewedAt = System.currentTimeMillis();
                        gate.isLateSubmission = false;
                        Toast.makeText(this, "❌ 已标记未完成，明天积分打" + db.gateConfigDao().getConfig().defaultPenaltyPercent + "折", Toast.LENGTH_LONG).show();
                        break;
                    case 2: // AI作弊
                        gate.status = com.sister.habits.data.models.DailyGate.STATUS_AI_DETECTED;
                        gate.reviewedAt = System.currentTimeMillis();
                        gate.isLateSubmission = false;
                        Toast.makeText(this, "🤖 已标记AI作弊，明天积分打" + db.gateConfigDao().getConfig().defaultPenaltyPercent + "折", Toast.LENGTH_LONG).show();
                        break;
                    case 3: // 免检
                        gate.status = com.sister.habits.data.models.DailyGate.STATUS_SKIPPED;
                        gate.reviewedAt = System.currentTimeMillis();
                        gate.isLateSubmission = false;
                        Toast.makeText(this, "🏥 已免检，明天正常积分", Toast.LENGTH_SHORT).show();
                        break;
                    case 4: // 补交
                        gate.status = com.sister.habits.data.models.DailyGate.STATUS_COMPLETED;
                        gate.isLateSubmission = true;
                        gate.reviewedAt = System.currentTimeMillis();
                        Toast.makeText(this, "📝 已标记补交，明天减免至" + db.gateConfigDao().getConfig().makeupPercent + "折", Toast.LENGTH_SHORT).show();
                        break;
                }
                gate.deviceId = syncManager.getDeviceId();
                gate.synced = false;
                gate.syncTimestamp = System.currentTimeMillis();
                db.dailyGateDao().insert(gate);
                syncManager.onDataChanged();
                showGateManageDialog(); // 留在作业管理，继续操作
            });
            android.widget.LinearLayout.LayoutParams reviewLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            reviewLp.setMargins(0, 0, 0, 8);
            btn.setLayoutParams(reviewLp);
            reviewLayout.addView(btn);
        }

        new AlertDialog.Builder(this)
            .setTitle("✏️ 审核 " + nickname + " 的作业（" + today + "）")
            .setView(reviewLayout)
            .setNegativeButton("← 返回", (d2, w2) -> showGateManageDialog())
            .show();
    }


    /** 🧺 洗衣任务管理 */
    private void showLaundryManageDialog() {
        soundHelper.playClickSound();
        SharedPreferences prefs = getSharedPreferences("laundry_prefs", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("laundry_enabled", true);
        
        List<LaundryTask> pending = db.laundryDao().getPending();
        int pendingCount = pending.size();
        int todayApproved = db.laundryDao().getApprovedCountForDate(
            new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()));
        
        String[] items = {
            "📊 今日已通过: " + todayApproved + " 项",
            "📋 待审核: " + pendingCount + " 项",
            enabled ? "🔴 关闭洗衣功能" : "🟢 开启洗衣功能"
        };
        
        new AlertDialog.Builder(this)
            .setTitle("🧺 洗衣任务管理")
            .setItems(items, (d, which) -> {
                switch (which) {
                    case 0:
                        Toast.makeText(this, "今日已通过 " + todayApproved + " 项洗衣任务", Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        showLaundryReviewDialog(pending);
                        break;
                    case 2:
                        prefs.edit().putBoolean("laundry_enabled", !enabled).apply();
                        String msg = !enabled ? "✅ 洗衣功能已开启" : "🚫 洗衣功能已关闭";
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .setNegativeButton("← 返回", (d2, w2) -> showSettingsDialog())
            .show();
    }
    
    /** 洗衣任务审核 */
    private void showLaundryReviewDialog(List<LaundryTask> pending) {
        if (pending.isEmpty()) {
            Toast.makeText(this, "没有待审核的洗衣任务", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] labels = new String[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            LaundryTask t = pending.get(i);
            labels[i] = "🧺 " + t.clothingType + " ×" + t.quantity + " = " + t.totalPoints + "分 (" + t.date + ")";
        }
        
        new AlertDialog.Builder(this)
            .setTitle("🧺 洗衣任务审核 (" + pending.size() + "项)")
            .setItems(labels, (dialog, which) -> {
                LaundryTask task = pending.get(which);
                showLaundryApproveDialog(task);
            })
            .setNegativeButton("← 返回", (d2, w2) -> showLaundryManageDialog())
            .show();
    }
    
    /** 单项审核：通过/拒绝 */
    private void showLaundryApproveDialog(LaundryTask task) {
        new AlertDialog.Builder(this)
            .setTitle("审核: " + task.clothingType + " ×" + task.quantity)
            .setMessage(
                "衣物类型: " + task.clothingType + "\n" +
                "件数: " + task.quantity + "件\n" +
                "积分: " + task.totalPoints + "分\n" +
                "提交时间: " + new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(task.submittedAt)))
            .setPositiveButton("✅ 通过", (d, w) -> {
                task.status = LaundryTask.STATUS_APPROVED;
                task.reviewedAt = System.currentTimeMillis();
                db.laundryDao().update(task);
                
                // 发放积分
                Integer balance = db.coinTransactionDao().getBalance("sister");
                int newBalance = (balance != null ? balance : 0) + task.totalPoints;
                com.sister.habits.data.models.CoinTransaction tx = new com.sister.habits.data.models.CoinTransaction(
                    "sister", task.totalPoints, newBalance, "laundry",
                    task.clothingType + "×" + task.quantity, syncManager.getDeviceId());
                db.coinTransactionDao().insert(tx);
                syncManager.onDataChanged();
                
                Toast.makeText(this, "✅ 已通过！+ " + task.totalPoints + "分", Toast.LENGTH_SHORT).show();
                
                // 刷新列表
                List<LaundryTask> remaining = db.laundryDao().getPending();
                if (!remaining.isEmpty()) {
                    showLaundryReviewDialog(remaining);
                }
            })
            .setNegativeButton("❌ 拒绝", (d, w) -> {
                task.status = LaundryTask.STATUS_REJECTED;
                task.reviewedAt = System.currentTimeMillis();
                db.laundryDao().update(task);
                Toast.makeText(this, "已拒绝", Toast.LENGTH_SHORT).show();
                
                List<LaundryTask> remaining = db.laundryDao().getPending();
                if (!remaining.isEmpty()) {
                    showLaundryReviewDialog(remaining);
                }
            })
            .setNeutralButton("取消", null)
            .show();
    }

    /** 🎰 抽奖管理 */
    private void showLotteryManageDialog() {
        soundHelper.playClickSound();
        List<LotteryPrize> prizes = db.lotteryDao().getAllPrizes();
        int totalDraws = db.lotteryDao().getTotalDraws();
        
        String[] items = new String[prizes.size() + 2];
        for (int i = 0; i < prizes.size(); i++) {
            LotteryPrize p = prizes.get(i);
            String status = p.enabled ? "" : " [已禁用]";
            items[i] = p.icon + " " + p.name + " (" + p.probability + "%)" + status;
        }
        items[prizes.size()] = "➕ 添加奖品";
        items[prizes.size() + 1] = "📊 统计: 共抽奖" + totalDraws + "次";
        
        new AlertDialog.Builder(this)
            .setTitle("🎰 抽奖管理")
            .setItems(items, (d, which) -> {
                if (which < prizes.size()) {
                    showLotteryPrizeEditDialog(prizes.get(which));
                } else if (which == prizes.size()) {
                    showAddLotteryPrizeDialog();
                }
            })
            .setNegativeButton("← 返回", (d2, w2) -> showSettingsDialog())
            .show();
    }
    
    private void showAddLotteryPrizeDialog() {
        final android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("奖品名称");
        final android.widget.EditText etIcon = new android.widget.EditText(this);
        etIcon.setHint("图标emoji (如 ⭐)");
        final android.widget.EditText etCost = new android.widget.EditText(this);
        etCost.setHint("消耗积分 (默认10)");
        etCost.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final android.widget.EditText etProb = new android.widget.EditText(this);
        etProb.setHint("概率权重 1-100 (默认50)");
        etProb.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.addView(etName);
        layout.addView(etIcon);
        layout.addView(etCost);
        layout.addView(etProb);
        
        new AlertDialog.Builder(this)
            .setTitle("➕ 添加奖品")
            .setView(layout)
            .setPositiveButton("添加", (d, w) -> {
                String name = etName.getText().toString().trim();
                String icon = etIcon.getText().toString().trim();
                String costStr = etCost.getText().toString().trim();
                String probStr = etProb.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(this, "请输入奖品名称", Toast.LENGTH_SHORT).show(); return; }
                LotteryPrize prize = new LotteryPrize();
                prize.name = name;
                prize.icon = icon.isEmpty() ? "🎁" : icon;
                prize.cost = costStr.isEmpty() ? 10 : Integer.parseInt(costStr);
                prize.probability = probStr.isEmpty() ? 50 : Integer.parseInt(probStr);
                db.lotteryDao().insertPrize(prize);
                Toast.makeText(this, "✅ 奖品已添加", Toast.LENGTH_SHORT).show();
                showLotteryManageDialog();
            })
            .setNegativeButton("取消", (d2, w2) -> showLotteryManageDialog())
            .show();
    }
    
    private void showLotteryPrizeEditDialog(LotteryPrize prize) {
        final android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("奖品名称");
        etName.setText(prize.name);
        final android.widget.EditText etIcon = new android.widget.EditText(this);
        etIcon.setHint("图标emoji");
        etIcon.setText(prize.icon);
        final android.widget.EditText etProb = new android.widget.EditText(this);
        etProb.setHint("中奖概率 % 0-100");
        etProb.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etProb.setText(String.valueOf(prize.probability));
        final android.widget.EditText etPoints = new android.widget.EditText(this);
        etPoints.setHint("积分奖品价值 (积分+多少)");
        etPoints.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPoints.setText(String.valueOf(prize.pointsValue));
        final android.widget.EditText etStock = new android.widget.EditText(this);
        etStock.setHint("库存 (-1=无限)");
        etStock.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etStock.setText(String.valueOf(prize.stock));
        final android.widget.Spinner spType = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> typeAdapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"🎯 积分奖品", "🎁 礼物奖品"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);
        spType.setSelection("points".equals(prize.prizeType) ? 0 : 1);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.addView(etName);
        layout.addView(etIcon);
        layout.addView(spType);
        layout.addView(etPoints);
        layout.addView(etProb);
        layout.addView(etStock);
        
        new AlertDialog.Builder(this)
            .setTitle("✏️ 编辑奖品: " + prize.icon + " " + prize.name)
            .setView(layout)
            .setPositiveButton("💾 保存", (d, w) -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show(); return; }
                int prob = etProb.getText().toString().trim().isEmpty() ? prize.probability : Integer.parseInt(etProb.getText().toString().trim());
                if (prob < 0 || prob > 100) { Toast.makeText(this, "概率需在0-100之间", Toast.LENGTH_SHORT).show(); return; }
                // 概率总和校验（排除自身）
                int currentTotal = 0;
                for (LotteryPrize p : db.lotteryDao().getEnabledPrizes()) {
                    if (p.id != prize.id) currentTotal += p.probability;
                }
                if (currentTotal + prob > 100) {
                    Toast.makeText(this, "❌ 概率总和将超过100% (其他奖品已占" + currentTotal + "%)", Toast.LENGTH_LONG).show();
                    return;
                }
                prize.name = name;
                prize.icon = etIcon.getText().toString().trim().isEmpty() ? "🎁" : etIcon.getText().toString().trim();
                prize.prizeType = spType.getSelectedItemPosition() == 0 ? "points" : "gift";
                String ptsStr = etPoints.getText().toString().trim();
                if (!ptsStr.isEmpty()) prize.pointsValue = Integer.parseInt(ptsStr);
                prize.probability = prob;
                String stockStr = etStock.getText().toString().trim();
                if (!stockStr.isEmpty()) prize.stock = Integer.parseInt(stockStr);
                db.lotteryDao().updatePrize(prize);
                Toast.makeText(this, "✅ 已保存", Toast.LENGTH_SHORT).show();
                showLotteryManageDialog();
            })
            .setNeutralButton(prize.enabled ? "🔴 禁用" : "🟢 启用", (d, w) -> {
                prize.enabled = !prize.enabled;
                db.lotteryDao().updatePrize(prize);
                Toast.makeText(this, prize.enabled ? "已启用" : "已禁用", Toast.LENGTH_SHORT).show();
                showLotteryManageDialog();
            })
            .setNegativeButton("🗑️ 删除", (d, w) -> {
                db.lotteryDao().deletePrize(prize);
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                showLotteryManageDialog();
            })
            .show();
    }
    /** 🏪 从商城商品添加为奖品 */
    private void showAddPrizeFromShopDialog() {
        List<ShopItem> items = db.shopItemDao().getAll();
        if (items.isEmpty()) {
            Toast.makeText(this, "商城暂无商品，请先上架商品", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ShopItem s = items.get(i);
            labels[i] = (s.iconUrl != null && !s.iconUrl.isEmpty() ? "🖼️" : "🏪") + " " + s.name + " (" + s.priceCoins + "分)";
        }
        new AlertDialog.Builder(this)
            .setTitle("🏪 选择商城商品作为奖品")
            .setItems(labels, (d, which) -> {
                ShopItem shop = items.get(which);
                final android.widget.EditText etProb = new android.widget.EditText(this);
                etProb.setHint("中奖概率 % 0-100");
                etProb.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                final android.widget.EditText etStock = new android.widget.EditText(this);
                etStock.setHint("奖品库存 (默认同步商品库存)");
                etStock.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(48, 24, 48, 0);
                layout.addView(etProb);
                layout.addView(etStock);
                new AlertDialog.Builder(this)
                    .setTitle("奖品: " + shop.name)
                    .setMessage("抽中后自动向家长发送礼物审批")
                    .setView(layout)
                    .setPositiveButton("添加", (d2, w2) -> {
                        String probStr = etProb.getText().toString().trim();
                        if (probStr.isEmpty()) { Toast.makeText(this, "请输入概率", Toast.LENGTH_SHORT).show(); return; }
                        int prob = Integer.parseInt(probStr);
                        if (prob < 0 || prob > 100) { Toast.makeText(this, "概率需在0-100之间", Toast.LENGTH_SHORT).show(); return; }
                        int currentTotal = 0;
                        for (LotteryPrize p : db.lotteryDao().getEnabledPrizes()) currentTotal += p.probability;
                        if (currentTotal + prob > 100) {
                            Toast.makeText(this, "❌ 概率总和将超过100% (当前" + currentTotal + "%)", Toast.LENGTH_LONG).show();
                            return;
                        }
                        LotteryPrize prize = new LotteryPrize();
                        prize.name = shop.name;
                        prize.icon = shop.iconUrl != null && !shop.iconUrl.isEmpty() ? "🎁" : "🎁";
                        prize.prizeType = "gift";
                        prize.pointsValue = shop.priceCoins;
                        prize.probability = prob;
                        String stockStr = etStock.getText().toString().trim();
                        prize.stock = stockStr.isEmpty() ? (shop.stock > 0 ? shop.stock : -1) : Integer.parseInt(stockStr);
                        prize.shopItemId = shop.id;
                        db.lotteryDao().insertPrize(prize);
                        Toast.makeText(this, "✅ 奖品已添加 (礼物类型，库存联动)", Toast.LENGTH_SHORT).show();
                        showLotteryManageDialog();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /** 🏆 学校奖励管理 */
    private void showSchoolRewardDialog() {
        soundHelper.playClickSound();
        List<SchoolReward> rewards = db.schoolRewardDao().getAll();
        int totalPoints = db.schoolRewardDao().getTotalPoints();
        
        String[] items = new String[rewards.size() + 2];
        for (int i = 0; i < rewards.size(); i++) {
            SchoolReward r = rewards.get(i);
            items[i] = r.badge + " " + r.name + " +" + r.points + "分 (" + r.date + ")";
        }
        items[rewards.size()] = "➕ 添加学校奖励";
        items[rewards.size() + 1] = "📊 累计: " + totalPoints + "分";
        
        new AlertDialog.Builder(this)
            .setTitle("🏆 学校奖励管理")
            .setItems(items, (d, which) -> {
                if (which < rewards.size()) {
                    showSchoolRewardEditDialog(rewards.get(which));
                } else if (which == rewards.size()) {
                    showAddSchoolRewardDialog();
                }
            })
            .setNegativeButton("← 返回", (d2, w2) -> showSettingsDialog())
            .show();
    }
    
    private void showAddSchoolRewardDialog() {
        final android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("奖励名称 (如: 数学优+)");
        final android.widget.EditText etPoints = new android.widget.EditText(this);
        etPoints.setHint("奖励积分 (默认5)");
        etPoints.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final android.widget.EditText etNote = new android.widget.EditText(this);
        etNote.setHint("备注 (可选)");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);
        layout.addView(etName);
        layout.addView(etPoints);
        layout.addView(etNote);
        
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        
        new AlertDialog.Builder(this)
            .setTitle("🏆 添加学校奖励 (" + today + ")")
            .setView(layout)
            .setPositiveButton("添加", (d, w) -> {
                String name = etName.getText().toString().trim();
                String ptsStr = etPoints.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(this, "请输入奖励名称", Toast.LENGTH_SHORT).show(); return; }
                SchoolReward reward = new SchoolReward();
                reward.name = name;
                reward.points = ptsStr.isEmpty() ? 5 : Integer.parseInt(ptsStr);
                reward.note = etNote.getText().toString().trim();
                reward.date = today;
                reward.deviceId = syncManager.getDeviceId();
                db.schoolRewardDao().insert(reward);
                
                // 同时发放积分
                Integer balance = db.coinTransactionDao().getBalance("sister");
                int newBalance = (balance != null ? balance : 0) + reward.points;
                com.sister.habits.data.models.CoinTransaction tx = new com.sister.habits.data.models.CoinTransaction(
                    "sister", reward.points, newBalance, "school_reward",
                    "🏆 " + reward.name, syncManager.getDeviceId());
                db.coinTransactionDao().insert(tx);
                syncManager.onDataChanged();
                
                Toast.makeText(this, "✅ 已添加！+ " + reward.points + "分", Toast.LENGTH_SHORT).show();
                showSchoolRewardDialog();
            })
            .setNegativeButton("取消", (d2, w2) -> showSchoolRewardDialog())
            .show();
    }
    
    private void showSchoolRewardEditDialog(SchoolReward reward) {
        new AlertDialog.Builder(this)
            .setTitle(reward.badge + " " + reward.name)
            .setMessage("日期: " + reward.date + "\n积分: +" + reward.points + "分\n备注: " + (reward.note.isEmpty() ? "无" : reward.note))
            .setPositiveButton("🗑️ 删除", (d, w) -> {
                db.schoolRewardDao().delete(reward);
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                showSchoolRewardDialog();
            })
            .setNegativeButton("← 返回", (d2, w2) -> showSchoolRewardDialog())
            .show();
    }
    /** 二级菜单：⚙️ 系统设置 */
    private void showSystemMenu() {
        String[] items = {
                "👤 孩子信息（昵称/头像/标题）",
                "🏠 默认启动模式",
                "💰 完整经济参数",
                "📅 假期与折扣（假期范围/周末开关）",
                "🚀 加速器管理（双倍积分日/打卡勋章/周月奖励）",
                "📋 任务模板库（22+预设任务）",
                "💰 积分审批（待审积分确认）",
                "🔑 绑定管理（家长/孩子Key）",
                "🔐 数据导出备份",
                "🔄 同步中心",
                "🔄 检查更新",
                "🔐 安全防护"
        };
        new AlertDialog.Builder(this)
                .setTitle("⚙️ 系统设置")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showProfileSettings(); break;
                        case 1: showHubSettings(); break;
                        case 2: showEconomySettings(); break;
                        case 3: showGateManageDialog(); break;
                        case 4: showAcceleratorSettings(); break;
                        case 5: showTaskTemplates(); break;
                        case 6: showEarningApprovals(); break;
                        case 7: showBindKeyDialog(); break;
                        case 8: showBackupRestoreDialog(); break;
                        case 9: showSyncCenterDialog(); break;
                        case 10: checkForUpdate(); break;
                        case 11: showPinManageDialog(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
                .show();
    }

    /** 🔑 绑定管理 */
    private void showBindKeyDialog() {
        String parentKey = BindKeyManager.generateParentKey(this);
        java.util.Set<String> children = BindKeyManager.getBoundChildren(this);
        StringBuilder sb = new StringBuilder();
        sb.append("👤 你的家长Key：\n").append(parentKey).append("\n\n");
        sb.append("📱 已绑定的孩子：\n");
        if (children.isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            int i = 1;
            for (String ck : children) {
                sb.append(i).append(". ").append(ck).append("\n");
                i++;
            }
        }
        sb.append("\n💡 提示：在孩子端查看Child Key，在此输入即可绑定。");
        
        android.widget.EditText etInput = new android.widget.EditText(this);
        etInput.setHint("输入孩子的Child Key (HABIT-C-XXXX)");
        etInput.setTextColor(0xFF000000);
        
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(48, 24, 48, 24);
        android.widget.TextView tvInfo = new android.widget.TextView(this);
        tvInfo.setText(sb.toString());
        tvInfo.setTextSize(14);
        tvInfo.setTextColor(0xFF333333);
        ll.addView(tvInfo);
        ll.addView(etInput);
        
        new AlertDialog.Builder(this)
            .setTitle("🔑 绑定管理")
            .setView(ll)
            .setPositiveButton("➕ 绑定孩子", (d, w) -> {
                String input = etInput.getText().toString().trim();
                if (BindKeyManager.isValidChildKey(input)) {
                    if (BindKeyManager.bindChild(ParentActivity.this, input)) {
                        Toast.makeText(this, "✅ 绑定成功！孩子Key: " + input + "\n正在全同步数据...", Toast.LENGTH_LONG).show();
                        // 绑定后自动全同步（Hub→局域网→云端），新设备立即拉取家庭数据
                        syncManager.triggerFullSyncAsync();
                        showBindKeyDialog(); // 保持对话框不关闭，方便连续操作
                        return;
                    } else {
                        Toast.makeText(this, "⚠️ 该孩子已绑定过", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "❌ Key格式错误（应为 HABIT-C-XXXXXXXX）", Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("🗑 解绑全部", (d, w) -> {
                new AlertDialog.Builder(this)
                    .setTitle("确认解绑")
                    .setMessage("确定要解绑所有已绑定的孩子吗？")
                    .setPositiveButton("确定", (d2, w2) -> {
                        for (String ck : new java.util.HashSet<>(BindKeyManager.getBoundChildren(ParentActivity.this))) {
                            BindKeyManager.unbindChild(ParentActivity.this, ck);
                        }
                        Toast.makeText(this, "已解绑全部孩子", Toast.LENGTH_SHORT).show();
                        showBindKeyDialog(); // 保持对话框不关闭
                    })
                    .setNegativeButton("取消", null)
                    .show();
            })
            .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
            .show();
    }

    /** ⚙️ 检查更新 */
    private void checkForUpdate() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://api.github.com/repos/Cuiyi-Srama/HabitTracker/releases/latest");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "HabitTracker/1.5.0");
                conn.setInstanceFollowRedirects(true);
                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String json = s.hasNext() ? s.next() : "";
                is.close();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> release = (java.util.Map<String, Object>) gson.fromJson(json, java.util.Map.class);
                String latestTag = (String) release.get("tag_name");
                String currentVer = "v1.5.0";
                java.util.List<Object> assets = (java.util.List<Object>) release.get("assets");
                String apkUrl = null;
                if (assets != null) {
                    for (Object a : assets) {
                        java.util.Map<String, Object> asset = (java.util.Map<String, Object>) a;
                        if (((String)asset.get("name")).endsWith(".apk")) {
                            apkUrl = (String) asset.get("browser_download_url");
                            break;
                        }
                    }
                }
                final String fTag = latestTag;
                final String fApkUrl = apkUrl;
                if (latestTag == null) {
                    runOnUiThread(() -> Toast.makeText(this, "❌ 检查失败", Toast.LENGTH_SHORT).show());
                    return;
                }
                boolean isNewer = !latestTag.equals(currentVer);
                runOnUiThread(() -> {
                    if (isNewer && fApkUrl != null) {
                        new AlertDialog.Builder(this)
                                .setTitle("🔄 发现新版本")
                                .setMessage("当前: " + currentVer + "\n最新: " + fTag + "\n\n是否下载更新？")
                                .setPositiveButton("📥 下载更新", (d, w) -> downloadUpdate(fApkUrl))
                                .setNegativeButton("☮ 稍后", null)
                                .show();
                    } else {
                        Toast.makeText(this, "✅ 已是最新版本 (" + currentVer + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "❌ 检查失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void downloadUpdate(String apkUrl) {
        Toast.makeText(this, "📥 下载中...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(apkUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                int totalSize = conn.getContentLength();
                java.io.InputStream is = conn.getInputStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream("/storage/emulated/0/Download/HabitTracker_update.apk");
                byte[] buf = new byte[8192];
                int len;
                long downloaded = 0;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                    downloaded += len;
                    final long d = downloaded;
                    final int pct = totalSize > 0 ? (int)(downloaded*100/totalSize) : -1;
                    runOnUiThread(() -> {
                        if (pct >= 0)
                            Toast.makeText(this, "📥 "+pct+"% ("+(d/1024)+"KB)", Toast.LENGTH_SHORT).show();
                    });
                }
                fos.close(); is.close();
                runOnUiThread(() -> {
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    i.setDataAndType(android.net.Uri.fromFile(new java.io.File("/storage/emulated/0/Download/HabitTracker_update.apk")),
                            "application/vnd.android.package-archive");
                    i.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "❌ 下载失败: "+e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
private void showProfileSettings() {
        View view = getLayoutInflater().inflate(R.layout.dialog_profile_settings, null);
        android.widget.EditText etNickname = view.findViewById(R.id.et_nickname);
        android.widget.EditText etAppTitle = view.findViewById(R.id.et_app_title);
        Button btnPickAvatar = view.findViewById(R.id.btn_pick_avatar);
        android.widget.TextView tvBirthday = view.findViewById(R.id.tv_birthday);
        etNickname.setText(profile.getNickname());
        etAppTitle.setText(profile.getAppTitle());
        // 生日显示
        String bday = profile.getBirthday();
        tvBirthday.setText(bday.isEmpty() ? "🎂 点击设置孩子生日" : "🎂 生日: " + bday);
        tvBirthday.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            // 如果已有生日，用已有的日期
            if (!bday.isEmpty()) {
                try {
                    String[] parts = bday.split("-");
                    cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])-1, Integer.parseInt(parts[2]));
                } catch (Exception e) {}
            }
            new android.app.DatePickerDialog(this, (datePicker, year, month, day) -> {
                String picked = year + "-" + (month+1) + "-" + day;
                tvBirthday.setText("🎂 生日: " + picked);
                profile.setBirthday(picked);
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        btnPickAvatar.setOnClickListener(v -> launchShopImagePicker());
        new AlertDialog.Builder(this)
                .setTitle("👤 孩子信息")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    String nn = etNickname.getText().toString().trim();
                    if (!nn.isEmpty()) profile.setNickname(nn);
                    String at = etAppTitle.getText().toString().trim();
                    if (!at.isEmpty()) profile.setAppTitle(at);
                    Toast.makeText(this, "孩子信息已更新 ✅", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

                        /** 🚀 加速器管理：双倍积分日、加速器开关 */

    /** 管理所有已发布的任务（编辑/删除） */
    private void showManageTasksDialog() {
        java.util.List<com.sister.habits.data.models.Task> all = db.taskDao().getAll();
        if (all == null || all.isEmpty()) {
            Toast.makeText(this, "暂无已发布任务", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            com.sister.habits.data.models.Task t = all.get(i);
            names[i] = ("active".equals(t.status) ? "🟢" : "confirmed".equals(t.status) ? "✅" : "⏳") + " " + t.title + " (" + t.rewardCoins + "分)";
        }
        new AlertDialog.Builder(this)
                .setTitle("📝 管理已发布任务 (" + all.size() + "个)")
                .setItems(names, (d, which) -> {
                    com.sister.habits.data.models.Task task = all.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle(task.title)
                            .setMessage("状态: " + task.status + " | 积分: " + task.rewardCoins + " | 类型: " + task.recurrenceType)
                            .setPositiveButton("✏️ 编辑", (d2, w2) -> showEditTaskDialog(task))
                            .setNeutralButton("🗑️ 删除", (d2, w2) -> {
                                new AlertDialog.Builder(this)
                                        .setTitle("确认删除")
                                        .setMessage("确定删除任务「" + task.title + "」吗？此操作不可恢复。")
                                        .setPositiveButton("确认删除", (d3, w3) -> {
                                            db.taskDao().delete(task);
                                            Toast.makeText(this, "已删除: " + task.title, Toast.LENGTH_SHORT).show();
                                            showManageTasksDialog();
                                        })
                                        .setNegativeButton("取消", null).show();
                            })
                            .setNegativeButton("← 返回", null).show();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showTaskMenu()).show();
    }
    /** 编辑已有任务 */
    private void showEditTaskDialog(com.sister.habits.data.models.Task task) {
        android.widget.EditText etTitle = new android.widget.EditText(this);
        etTitle.setText(task.title);
        etTitle.setHint("任务名称");
        android.widget.EditText etReward = new android.widget.EditText(this);
        etReward.setText(String.valueOf(task.rewardCoins));
        etReward.setHint("奖励积分");
        etReward.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        String[] types = {"一次性", "每日", "每周", "每月", "常驻", "限时"};
        String[] typeCodes = {"once", "daily", "weekly", "monthly", "permanent", "timed"};
        final int[] sel = {java.util.Arrays.asList(typeCodes).indexOf(task.recurrenceType)};
        if (sel[0] < 0) sel[0] = 0;
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(32,16,32,16);
        ll.addView(etTitle);
        ll.addView(etReward);
        new AlertDialog.Builder(this)
                .setTitle("编辑任务")
                .setView(ll)
                .setSingleChoiceItems(types, sel[0], (d2, w2) -> sel[0] = w2)
                .setPositiveButton("保存", (d2, w2) -> {
                    String title = etTitle.getText().toString().trim();
                    String rewardStr = etReward.getText().toString().trim();
                    if (title.isEmpty() || rewardStr.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    task.title = title;
                    task.rewardCoins = Integer.parseInt(rewardStr);
                    task.recurrenceType = typeCodes[sel[0]];
                    db.taskDao().update(task);
                    Toast.makeText(this, "✅ 已更新: " + title, Toast.LENGTH_SHORT).show();
                    showManageTasksDialog();
                })
                .setNegativeButton("取消", null).show();
    }
    private void showTaskTemplates() {
        String[] templates = {
            "H01-整理床铺 (3分)", "H02-玩具归位 (3分)", "H03-擦拭餐桌 (5分)",
            "H04-倒垃圾 (5分)", "H05-扫地一间 (10分)", "H06-拖地一间 (15分)",
            "H07-洗碗一餐 (10分)", "H08-晾收衣服 (8分)", "H09-叠衣服 (8分)",
            "H15-大扫除30min (30分)", "S01-背单词10个 (8分)", "S02-课外阅读20min (5分)",
            "S03-练琴练字20min (8分)", "S04-额外练习一页 (5分)", "S05-背古诗古文 (5分)",
            "S06-写日记小作文 (10分)", "S09-错题5道整理 (5分)", "C01-主动帮小事 (5分)",
            "C03-连续三天不挑食 (5分)", "H17-帮做一顿饭 (15分)",
            "C04-赞美家人 (3分)", "C05-招待客人 (5分)"
        };
        final boolean[] checked = new boolean[templates.length];
        new AlertDialog.Builder(this)
                .setTitle("任务模板库(22个)-多选后一键添加")
                .setMultiChoiceItems(templates, checked, (d, which, isChecked) -> {})
                .setPositiveButton("✅ 批量添加选中项", (d, which) -> {
                    int added = 0;
                    for (int i = 0; i < templates.length; i++) {
                        if (!checked[i]) continue;
                        String t = templates[i];
                        String[] p = t.split("-", 2);
                        String code = p[0];
                        String desc = p.length > 1 ? p[1] : "";
                        int reward = 5;
                        if (desc.contains("(") && desc.contains("分")) {
                            try {
                                reward = Integer.parseInt(desc.substring(desc.indexOf("(")+1, desc.indexOf("分")));
                            } catch (Exception e) {}
                        }
                        String title = desc.contains("(") ? desc.substring(0, desc.indexOf("(")).trim() : desc;
                        com.sister.habits.data.models.Task task = new com.sister.habits.data.models.Task();
                        task.id = code + "_" + System.currentTimeMillis() + "_" + i;
                        task.title = title;
                        task.description = code + " " + title;
                        task.rewardCoins = reward;
                        task.recurrenceType = "daily";
                        task.status = "active";
                        task.createdAt = System.currentTimeMillis();
                        db.taskDao().insert(task);
                        added++;
                    }
                    Toast.makeText(this, "✅ 已添加 " + added + " 个任务", Toast.LENGTH_SHORT).show();
                    showManageTasksDialog();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu()).show();
    }


    private void showEarningApprovals() {
        java.util.List<com.sister.habits.data.models.CoinEarning> pendings = db.coinEarningDao().getPending();
        if (pendings == null || pendings.isEmpty()) {
            Toast.makeText(this, "无待审积分", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[pendings.size()];
        for (int i = 0; i < pendings.size(); i++) {
            com.sister.habits.data.models.CoinEarning e = pendings.get(i);
            String desc = e.description != null ? e.description : "未知";
            String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date(e.requestedAt));
            items[i] = "+" + e.amount + "分 " + desc + "  (" + time + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle("" + pendings.size() + "项待审积分")
                .setItems(items, (d, which) -> {
                    com.sister.habits.data.models.CoinEarning e = pendings.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle("审批: " + e.description)
                            .setMessage("金额: +" + e.amount + "分\n来源: " + e.sourceType)
                            .setPositiveButton("确认", (d2, w2) -> {
                                e.status = "confirmed";
                                e.confirmedAt = System.currentTimeMillis();
                                db.coinEarningDao().update(e);
                                Integer balance = db.coinTransactionDao().getBalance("sister");
                                int newBal = (balance != null ? balance : 0) + e.amount;
                                com.sister.habits.data.models.CoinTransaction ct = new com.sister.habits.data.models.CoinTransaction(
                                        "sister", e.amount, newBal,
                                        e.sourceType, e.description,
                                        com.sister.habits.sync.SyncManager.getInstance(ParentActivity.this).getDeviceId());
                                db.coinTransactionDao().insert(ct);
                                Toast.makeText(this, "已确认 +" + e.amount + "分!", Toast.LENGTH_SHORT).show();
                                refreshStats();
                            })
                            .setNegativeButton("拒绝", (d2, w2) -> {
                                e.status = "rejected";
                                e.rejectedAt = System.currentTimeMillis();
                                db.coinEarningDao().update(e);
                                Toast.makeText(this, "已拒绝该积分申请", Toast.LENGTH_SHORT).show();
                            })
                            .setNeutralButton("返回", null)
                            .show();
                })
                .setNegativeButton("返回上级", (d, w) -> showSystemMenu()).show();
    }

    private void showAcceleratorSettings() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        EconomyConfig finalConfig = config;
        View view = getLayoutInflater().inflate(R.layout.dialog_accelerator_settings, null);

        android.widget.Switch swDoublePoints = view.findViewById(R.id.sw_double_points);
        android.widget.TextView tvDoubleDate = view.findViewById(R.id.tv_double_date);
        android.widget.EditText etStreak7 = view.findViewById(R.id.et_accel_streak7);
        android.widget.EditText etWeek = view.findViewById(R.id.et_accel_week);
        android.widget.EditText etMonth = view.findViewById(R.id.et_accel_month);
        android.widget.EditText etBirthday = view.findViewById(R.id.et_accel_birthday);
        android.widget.EditText etHoliday = view.findViewById(R.id.et_accel_holiday);
        android.widget.EditText etLimitWeekday = view.findViewById(R.id.et_limit_weekday);
        android.widget.EditText etLimitWeekend = view.findViewById(R.id.et_limit_weekend);
        android.widget.TextView tvSummary = view.findViewById(R.id.tv_accel_summary);

        swDoublePoints.setChecked(finalConfig.doublePointsEnabled);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
        tvDoubleDate.setText("📅 启用日期: " + (finalConfig.doublePointDate != null ? finalConfig.doublePointDate : today));
        etStreak7.setText(String.valueOf(finalConfig.boostStreak7));
        etWeek.setText(String.valueOf(finalConfig.boostWeek));
        etMonth.setText(String.valueOf(finalConfig.boostMonth));
        etBirthday.setText(String.valueOf(finalConfig.boostBirthday));
        etHoliday.setText(String.valueOf(finalConfig.boostHoliday));
        etLimitWeekday.setText(String.valueOf(finalConfig.softLimitWeekday));
        etLimitWeekend.setText(String.valueOf(finalConfig.softLimitWeekend));

        // 双倍积分日开关：点日期可修改
        tvDoubleDate.setOnClickListener(v -> {
            String dateStr = tvDoubleDate.getText().toString().replace("📅 启用日期: ", "").trim();
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA);
                java.util.Date d = sdf.parse(dateStr);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(d);
                new android.app.DatePickerDialog(this, (view2, year, month, day) -> {
                    String picked = year + "-" + (month+1) + "-" + day;
                    tvDoubleDate.setText("📅 启用日期: " + picked);
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
            } catch (Exception e) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                new android.app.DatePickerDialog(this, (view2, year, month, day) -> {
                    String picked = year + "-" + (month+1) + "-" + day;
                    tvDoubleDate.setText("📅 启用日期: " + picked);
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
            }
        });

        // 显示当前加速器摘要
        String boostSummary = com.sister.habits.sync.AcceleratorService.getTodayBoostSummary(this);
        tvSummary.setText(!boostSummary.isEmpty() ? boostSummary : "今日暂无加速器触发");

        new AlertDialog.Builder(this)
                .setTitle("🚀 加速器管理")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    boolean doubleOn = swDoublePoints.isChecked();
                    String doubleDate = tvDoubleDate.getText().toString().replace("📅 启用日期: ", "").trim();
                    finalConfig.doublePointsEnabled = doubleOn;
                    finalConfig.doublePointDate = doubleOn ? doubleDate : null;
                    finalConfig.boostStreak7 = parseInt(etStreak7, 15);
                    finalConfig.boostWeek = parseInt(etWeek, 30);
                    finalConfig.boostMonth = parseInt(etMonth, 80);
                    finalConfig.boostBirthday = parseInt(etBirthday, 100);
                    finalConfig.boostHoliday = parseInt(etHoliday, 50);
                    finalConfig.softLimitWeekday = parseInt(etLimitWeekday, 60);
                    finalConfig.softLimitWeekend = parseInt(etLimitWeekend, 100);
                    finalConfig.updatedAt = System.currentTimeMillis();  // LWW时间戳（v3.0.62）
                    db.economyConfigDao().setConfig(finalConfig);
                    Toast.makeText(this, "加速器设置已保存 ✅", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("🚀 立即检查加速器", (d, w) -> {
                    com.sister.habits.sync.AcceleratorService.checkAndApply(this);
                    Toast.makeText(this, "加速器检查完成，查看积分审批确认结果", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu()).show();
    }

    private void showEconomySettings() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        View view = getLayoutInflater().inflate(R.layout.dialog_economy_settings, null);
        EconomyConfig finalConfig = config;
        android.widget.EditText etCheckinBase = view.findViewById(R.id.et_checkin_base);
        android.widget.EditText etStreak3 = view.findViewById(R.id.et_streak3);
        android.widget.EditText etStreak7 = view.findViewById(R.id.et_streak7);
        android.widget.EditText etStreak14 = view.findViewById(R.id.et_streak14);
        android.widget.EditText etStreak30 = view.findViewById(R.id.et_streak30);
        android.widget.EditText etWordLearn = view.findViewById(R.id.et_word_learn);
        android.widget.EditText etReviewPass = view.findViewById(R.id.et_review_pass);
        android.widget.EditText etMaxDailyCoins = view.findViewById(R.id.et_max_daily_coins);
        android.widget.EditText etMaxWords = view.findViewById(R.id.et_max_words);
        android.widget.EditText etMaxReview = view.findViewById(R.id.et_max_review);
        etCheckinBase.setText(String.valueOf(finalConfig.checkInBaseReward));
        etStreak3.setText(String.valueOf(finalConfig.streak3Bonus));
        etStreak7.setText(String.valueOf(finalConfig.streak7Bonus));
        etStreak14.setText(String.valueOf(finalConfig.streak14Bonus));
        etStreak30.setText(String.valueOf(finalConfig.streak30Bonus));
        etWordLearn.setText(String.valueOf(finalConfig.wordLearnReward));
        etReviewPass.setText(String.valueOf(finalConfig.reviewPassReward));
        etMaxDailyCoins.setText(String.valueOf(finalConfig.maxDailyCoins));
        etMaxWords.setText(String.valueOf(finalConfig.maxDailyWords));
        etMaxReview.setText(String.valueOf(finalConfig.maxDailyReview));



        android.widget.EditText etSoftWeekday = view.findViewById(R.id.et_soft_limit_weekday);
        android.widget.EditText etSoftWeekend = view.findViewById(R.id.et_soft_limit_weekend);



        etSoftWeekday.setText(String.valueOf(finalConfig.softLimitWeekday));
        etSoftWeekend.setText(String.valueOf(finalConfig.softLimitWeekend));

        new AlertDialog.Builder(this)
                .setTitle("💰 经济参数")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    finalConfig.checkInBaseReward = parseInt(etCheckinBase, 10);
                    finalConfig.streak3Bonus = parseInt(etStreak3, 5);
                    finalConfig.streak7Bonus = parseInt(etStreak7, 15);
                    finalConfig.streak14Bonus = parseInt(etStreak14, 30);
                    finalConfig.streak30Bonus = parseInt(etStreak30, 100);
                    finalConfig.wordLearnReward = parseInt(etWordLearn, 2);
                    finalConfig.reviewPassReward = parseInt(etReviewPass, 2);
                    finalConfig.maxDailyCoins = parseInt(etMaxDailyCoins, 500);
                    finalConfig.maxDailyWords = parseInt(etMaxWords, 10);
                    finalConfig.maxDailyReview = parseInt(etMaxReview, 30);


                    finalConfig.softLimitWeekday = parseInt(etSoftWeekday, 60);
                    finalConfig.softLimitWeekend = parseInt(etSoftWeekend, 100);
                    finalConfig.updatedAt = System.currentTimeMillis();  // LWW时间戳（v3.0.62）
                    db.economyConfigDao().setConfig(finalConfig);
                    Toast.makeText(this, "参数已更新 ✅", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu())
                .show();
    }

    private void showHubSettings() {
        SharedPreferences prefs = getSharedPreferences("parent_prefs", MODE_PRIVATE);
        String currentMode = prefs.getString("default_mode", "child");
        String[] modes = {"👧 默认进入孩子模式", "👨 默认进入家长模式", "❓ 每次询问"};
        int checked = "child".equals(currentMode) ? 0 : "parent".equals(currentMode) ? 1 : 2;
        // v3.0.65：Hub中枢相关配置已移至「🔄同步中心」，本对话框只保留启动模式
        new AlertDialog.Builder(this)
                .setTitle("🏠 默认启动模式")
                .setSingleChoiceItems(modes, checked, (d, w) -> {
                    String mode = w == 0 ? "child" : w == 1 ? "parent" : "ask";
                    prefs.edit().putString("default_mode", mode).apply();
                })
                .setPositiveButton("✅ 确定", null)
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu()).show();
    }

    private int parseInt(android.widget.EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString()); } catch (Exception e) { return def; }
    }

    /**
     * 词库管理对话框：年级选择 + 每日单词量 + JSON导入
     */
    private void showWordbankDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_wordbank, null);
        SharedPreferences prefs = getSharedPreferences("wordbank_prefs", MODE_PRIVATE);

        // 恢复年级选择状态（小学/初中/高中）
        ((CheckBox) view.findViewById(R.id.cb_grade_primary)).setChecked(prefs.getBoolean("grade_primary", true));
        ((CheckBox) view.findViewById(R.id.cb_grade_junior)).setChecked(prefs.getBoolean("grade_junior", true));
        ((CheckBox) view.findViewById(R.id.cb_grade_senior)).setChecked(prefs.getBoolean("grade_senior", false));

        // 恢复每日单词量
        EconomyConfig config = db.economyConfigDao().getConfig();
        android.widget.EditText etDailyWords = view.findViewById(R.id.et_daily_words);
        etDailyWords.setText(String.valueOf(config != null ? config.maxDailyWords : 10));

        // JSON导入按钮——启动文件选择器
        Button btnImport = view.findViewById(R.id.btn_import_wordbank);
        btnImport.setOnClickListener(v -> {
            soundHelper.playClickSound();
            wordbankImportLauncher.launch(new String[]{"application/json", "*/*"});
        });

        // ===== 外部词库源加载 =====
        LinearLayout externalLayout = view.findViewById(R.id.layout_external_sources);
        try {
            android.content.res.AssetManager am = getAssets();
            java.io.InputStream is = am.open("wordbank_sources.json");
            // ★ Fix 2: 使用ByteArrayOutputStream确保读取完整文件
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = is.read(tmp)) != -1) {
                baos.write(tmp, 0, n);
            }
            is.close();
            String json = baos.toString("UTF-8");
            Type sourceType = new TypeToken<List<ExternalSource>>(){}.getType();
            List<ExternalSource> sources = new Gson().fromJson(json, sourceType);

            for (ExternalSource source : sources) {
                // 卡片式布局
                android.widget.LinearLayout card = new android.widget.LinearLayout(this);
                card.setOrientation(android.widget.LinearLayout.VERTICAL);
                card.setPadding(12, 12, 12, 12);
                android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, 8);
                card.setLayoutParams(cardParams);
                card.setBackgroundResource(R.drawable.card_background);

                // 名称+标签
                android.widget.LinearLayout headerRow = new android.widget.LinearLayout(this);
                headerRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                headerRow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

                TextView tvName = new TextView(this);
                tvName.setText(source.name);
                tvName.setTextSize(14);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                tvName.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView tvTag = new TextView(this);
                tvTag.setText(source.gradeLabel);
                tvTag.setTextSize(12);
                tvTag.setTextColor(0xFFFFFFFF);
                tvTag.setPadding(8, 3, 8, 3);
                tvTag.setBackgroundColor(0xFF6B35);
                android.widget.LinearLayout.LayoutParams tagLp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                tagLp.setMargins(0, 0, 4, 0);
                tvTag.setLayoutParams(tagLp);
                android.graphics.drawable.GradientDrawable tagBg = new android.graphics.drawable.GradientDrawable();
                tagBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                tagBg.setCornerRadius(20);
                tagBg.setColor(0xFF6B35);
                tvTag.setBackground(tagBg);

                headerRow.addView(tvName);
                headerRow.addView(tvTag);
                card.addView(headerRow);

                // 描述
                TextView tvDesc = new TextView(this);
                tvDesc.setText(source.description);
                tvDesc.setTextSize(12);
                tvDesc.setTextColor(0xFF666666);
                tvDesc.setPadding(0, 4, 0, 8);
                card.addView(tvDesc);

                // 下载按钮 — MATCH_PARENT全宽，自适应高度
                Button btnDownload = new Button(this);
                btnDownload.setText("📥 下载并预览");
                btnDownload.setTextSize(14);
                btnDownload.setAllCaps(false);
                btnDownload.setTextColor(0xFFFFFFFF);
                btnDownload.setBackgroundColor(0xFF1976D2);
                android.graphics.drawable.GradientDrawable btnDlBg = new android.graphics.drawable.GradientDrawable();
                btnDlBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                btnDlBg.setCornerRadius(8);
                btnDlBg.setColor(0xFF1976D2);
                btnDownload.setBackground(btnDlBg);
                int dp12dl = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
                int dp48dl = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
                btnDownload.setPadding(dp12dl, 0, dp12dl, 0);
                btnDownload.setMinHeight(dp48dl);
                btnDownload.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                btnDownload.setOnClickListener(v -> downloadAndPreview(source));
                card.addView(btnDownload);

                externalLayout.addView(card);
            }
        } catch (Exception e) {
            android.util.Log.e("ParentActivity", "加载外部词库源失败", e);
            TextView tvError = new TextView(this);
            tvError.setText("外部词库加载失败: " + e.getMessage());
            tvError.setTextSize(12);
            tvError.setTextColor(0xFFFF4444);
            tvError.setPadding(0, 8, 0, 0);
            externalLayout.addView(tvError);
        }

        // ===== 已下载词库列表（切换/删除功能） =====
        LinearLayout installedLayout = view.findViewById(R.id.layout_installed_banks);
        String activeBankId = getSharedPreferences("wordbank_prefs", MODE_PRIVATE).getString("active_bank_id", "builtin");
        java.util.List<com.sister.habits.data.models.WordBank> installedBanks = db.wordBankDao().getAll();
        final AlertDialog[] dialogHolder = new AlertDialog[1];

        if (installedBanks.isEmpty()) {
            TextView tvNoBank = new TextView(this);
            tvNoBank.setText("暂无已下载词库");
            tvNoBank.setTextSize(12);
            tvNoBank.setTextColor(0xFF888888);
            tvNoBank.setPadding(8, 8, 8, 8);
            installedLayout.addView(tvNoBank);
        } else {
            for (com.sister.habits.data.models.WordBank bank : installedBanks) {
                boolean isActive = bank.id.equals(activeBankId);
                boolean isBuiltin = "builtin".equals(bank.id);
                final String bankId = bank.id;
                final String bankName = bank.name;

                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

                Button btnBank = new Button(this);
                String prefix = isActive ? "✅ " : "  ";
                btnBank.setText(prefix + bankName + " (" + bank.wordCount + "词)");
                btnBank.setTextSize(13);
                btnBank.setAllCaps(false);
                btnBank.setBackgroundColor(isActive ? 0xFFE8F5E9 : 0xFFF5F5F5);
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        isBuiltin ? android.widget.LinearLayout.LayoutParams.MATCH_PARENT : 0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                lp.setMargins(0, 3, isBuiltin ? 0 : 4, 3);
                btnBank.setLayoutParams(lp);
                btnBank.setOnClickListener(v -> {
                    if (bankId.equals(activeBankId)) {
                        Toast.makeText(this, "当前已在使用此词库", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    getSharedPreferences("wordbank_prefs", MODE_PRIVATE)
                            .edit().putString("active_bank_id", bankId).apply();
                    db.wordBankDao().deactivateAll();
                    db.wordBankDao().setActive(bankId);
                    Toast.makeText(this, "✅ 已切换到: " + bankName, Toast.LENGTH_LONG).show();
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    showWordbankDialog();
                });
                row.addView(btnBank);

                if (!isBuiltin) {
                    Button btnDelete = new Button(this);
                    btnDelete.setText("🗑");
                    btnDelete.setTextSize(13);
                    btnDelete.setAllCaps(false);
                    btnDelete.setBackgroundColor(0xFFFFEBEE);
                    btnDelete.setTextColor(0xFFD32F2F);
                    int dp48 = (int)(48 * getResources().getDisplayMetrics().density + 0.5f);
                    android.widget.LinearLayout.LayoutParams dlp = new android.widget.LinearLayout.LayoutParams(dp48,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    dlp.setMargins(0, 3, 0, 3);
                    btnDelete.setLayoutParams(dlp);
                    btnDelete.setOnClickListener(v -> {
                        new AlertDialog.Builder(ParentActivity.this)
                                .setTitle("删除词库")
                                .setMessage("确定要删除「" + bankName + "」吗？\n\n将同时删除该词库下的所有词汇数据，不可恢复。")
                                .setPositiveButton("确认删除", (dd, ww) -> {
                                    db.vocabularyDao().deleteByBankId(bankId);
                                    db.wordBankDao().deleteById(bankId);
                                    if (bankId.equals(activeBankId)) {
                                        getSharedPreferences("wordbank_prefs", MODE_PRIVATE)
                                                .edit().putString("active_bank_id", "builtin").apply();
                                        db.wordBankDao().setActive("builtin");
                                    }
                                    Toast.makeText(ParentActivity.this, "🗑 已删除: " + bankName, Toast.LENGTH_SHORT).show();
                                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                                    showWordbankDialog();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    });
                    row.addView(btnDelete);
                }
                installedLayout.addView(row);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("📚 词库管理")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    boolean gPrimary = ((CheckBox) view.findViewById(R.id.cb_grade_primary)).isChecked();
                    boolean gJunior = ((CheckBox) view.findViewById(R.id.cb_grade_junior)).isChecked();
                    boolean gSenior = ((CheckBox) view.findViewById(R.id.cb_grade_senior)).isChecked();
                    prefs.edit()
                            .putBoolean("grade_primary", gPrimary)
                            .putBoolean("grade_junior", gJunior)
                            .putBoolean("grade_senior", gSenior)
                            .putInt("grade_version", prefs.getInt("grade_version", 0) + 1)
                            .apply();
                    try {
                        int dailyWords = Integer.parseInt(etDailyWords.getText().toString());
                        if (dailyWords > 0) {
                            db.economyConfigDao().updateMaxDailyWords(dailyWords);
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(this, "词库配置已保存 ✅\n重启App后生效", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("\u2190 返回上级", (d, w) -> showLearningMenu())
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    
    /** 商城管理对话框 — 编辑/下架商品 */


    /** 紧凑小按钮（减少默认高度内边距） */


    /** 确认删除商品（单项或批量） */
    /** 确认删除商品（单项或批量） */

    /** 编辑商品 */


    // 审批适配器（点击=勾选，长按=单项审批）
    private static class ApprovalAdapter extends RecyclerView.Adapter<ApprovalAdapter.ViewHolder> {
        private final List<Redemption> items;
        private final java.util.Set<String> selected;
        private final OnApprovalListener listener;
        interface OnApprovalListener { void onApprove(Redemption item, boolean approved); }
        ApprovalAdapter(List<Redemption> items, java.util.Set<String> selected, OnApprovalListener listener) {
            this.items = items;
            this.selected = selected;
            this.listener = listener;
        }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
            return new ViewHolder(v);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Redemption item = items.get(position);
            holder.textView.setText("🪙 " + item.itemName + "  (" + item.coinsCost + "金币)");
            holder.itemView.setActivated(selected.contains(item.id));
            holder.itemView.setOnClickListener(v -> {
                if (selected.contains(item.id)) selected.remove(item.id);
                else selected.add(item.id);
                holder.itemView.setActivated(selected.contains(item.id));
            });
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("审批兑换申请")
                        .setMessage("兑换: " + item.itemName + "\n消耗: " + item.coinsCost + " 金币\n申请时间: " +
                                new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(item.requestedAt)))
                        .setPositiveButton("✅ 确认", (d, w) -> listener.onApprove(item, true))
                        .setNegativeButton("❌ 拒绝", (d, w) -> listener.onApprove(item, false))
                        .setNeutralButton("稍后", null)
                        .show();
                return true;
            });
        }
        @Override
        public int getItemCount() { return items.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textView;
            ViewHolder(View v) { super(v); textView = v.findViewById(android.R.id.text1); }
        }
    }

    // ==================== 数据导出备份 ====================
    private void showBackupRestoreDialog() {
        String backupInfo = "📁 默认: " + com.sister.habits.utils.BackupExportHelper.getDefaultBackupDir() +
                "\n📄 命名: HabitTracker_backup_设备码_日期" + com.sister.habits.utils.BackupExportHelper.getBackupExt() +
                "\n💡 导出/导入可自定义位置";
        // 自定义View：避免setMessage+setItems组合在小屏上items被挤压隐藏
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 8, 48, 8);
        android.widget.TextView tvInfo = new android.widget.TextView(this);
        tvInfo.setText(backupInfo);
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(0xFF666666);
        tvInfo.setPadding(0, 0, 0, 16);
        layout.addView(tvInfo);
        String[] btnLabels = {
            "📤 导出加密备份（可选择保存位置）",
            "📥 从备份文件恢复（选择文件）",
            "📂 查看已有备份文件"
        };
        final Runnable[] actions = new Runnable[] {
            this::doExportBackup,
            this::doImportBackup,
            this::listBackupFiles
        };
        for (int i = 0; i < btnLabels.length; i++) {
            final int idx = i;
            android.widget.Button btn = new android.widget.Button(this);
            btn.setText(btnLabels[i]);
            btn.setTextSize(14);
            btn.setAllCaps(false);
            btn.setTextColor(0xFF333333);
            btn.setBackgroundColor(0xFFF5F5F5);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 12);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> actions[idx].run());
            layout.addView(btn);
        }
        new AlertDialog.Builder(this)
                .setTitle("🔐 数据备份与恢复")
                .setView(layout)
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu())
                .show();
    }
    private void doExportBackup() {
        android.widget.EditText etPwd = new android.widget.EditText(this);
        etPwd.setHint("设置备份密码（用于加密保护）");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("📤 导出加密备份")
                .setMessage("将导出全部数据（加密）\n\n默认文件名: " + new com.sister.habits.utils.BackupExportHelper(this).generateDefaultFileName() +
                        "\n导出时可自由选择保存位置和文件名")
                .setView(etPwd)
                .setPositiveButton("下一步：选择位置", (d, w) -> {
                    String pwd = etPwd.getText().toString();
                    if (pwd.length() < 4) { Toast.makeText(this, "密码至少4位", Toast.LENGTH_SHORT).show(); return; }
                    pendingBackupPassword = pwd;
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    intent.putExtra(android.content.Intent.EXTRA_TITLE,
                            new com.sister.habits.utils.BackupExportHelper(this).generateDefaultFileName());
                    try {
                        createBackupLauncher.launch(intent);
                    } catch (Exception e) {
                        // 无launcher时退回默认目录
                        try {
                            com.sister.habits.utils.BackupExportHelper helper = new com.sister.habits.utils.BackupExportHelper(this);
                            java.io.File f = helper.exportBackup(pwd);
                            Toast.makeText(this, "✅ 备份成功: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        } catch (Exception e2) {
                            Toast.makeText(this, "❌ 备份失败: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doImportBackup() {
        // 优先列出默认目录（Download）的备份文件，用户可直接选择恢复
        // 避免部分设备系统文件选择器（DocumentsUI）无法导航目录的问题
        java.io.File[] backups = com.sister.habits.utils.BackupExportHelper.findBackupFiles(this);
        if (backups != null && backups.length > 0) {
            String[] names = new String[backups.length + 1];
            for (int i = 0; i < backups.length; i++) {
                names[i] = "📄 " + backups[i].getName() + " (" + (backups[i].length() / 1024) + " KB)";
            }
            names[backups.length] = "📂 选择其他位置（系统文件选择器）";
            final java.io.File[] finalBackups = backups;
            // 自定义布局：TextView目录 + 实体Button列表，规避 vivo setMessage+setItems 列表不渲染
            android.widget.LinearLayout bkLayout = new android.widget.LinearLayout(this);
            bkLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
            bkLayout.setPadding(40, 20, 40, 20);
            android.widget.TextView tvDir = new android.widget.TextView(this);
            tvDir.setText("📁 默认位置: " + com.sister.habits.utils.BackupExportHelper.getDefaultBackupDir());
            tvDir.setTextSize(13);
            tvDir.setTextColor(0xFF666666);
            tvDir.setPadding(0, 0, 12, 0);
            bkLayout.addView(tvDir);
            for (int i = 0; i < names.length; i++) {
                final int which = i;
                android.widget.Button btn = new android.widget.Button(this);
                btn.setText(names[i]);
                btn.setAllCaps(false);
                btn.setOnClickListener(v -> {
                    if (which == finalBackups.length) {
                        launchSystemFilePicker();
                        return;
                    }
                    restoreFromBackupFile(finalBackups[which]);
                });
                android.widget.LinearLayout.LayoutParams bkLp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                bkLp.setMargins(0, 0, 0, 8);
                btn.setLayoutParams(bkLp);
                bkLayout.addView(btn);
            }
            new AlertDialog.Builder(this)
                    .setTitle("📥 选择备份文件恢复")
                    .setView(bkLayout)
                    .setNegativeButton("📂 选择其他位置", (d, w) -> launchSystemFilePicker())
                    .show();
        } else {
            launchSystemFilePicker();
        }
    }
    /** 系统文件选择器（SAF） */
    private void launchSystemFilePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            openBackupLauncher.launch(intent);
        } catch (Exception e) {
            // 兼容无 launcher 注册的情况
            Toast.makeText(this, "❌ 无法打开文件选择器", Toast.LENGTH_LONG).show();
        }
    }
    /** 从本地备份文件恢复（含密码确认） */
    private void restoreFromBackupFile(final java.io.File selected) {
        android.widget.EditText etPwd = new android.widget.EditText(this);
        etPwd.setHint("输入备份密码");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("恢复: " + selected.getName())
                .setMessage("⚠️ 恢复将覆盖当前所有数据！确定继续？")
                .setView(etPwd)
                .setPositiveButton("恢复", (d2, w2) -> {
                    String pwd = etPwd.getText().toString().trim();
                    // 自动备份文件（auto_开头）：密码留空时自动使用内置密码
                    if (pwd.isEmpty() && selected.getName().startsWith("auto_")) {
                        pwd = com.sister.habits.utils.BackupExportHelper.AUTO_PWD;
                    }
                    final String finalPwd = pwd;
                    new Thread(() -> {
                        try {
                            com.sister.habits.utils.BackupExportHelper helper = new com.sister.habits.utils.BackupExportHelper(this);
                            helper.importBackup(selected, finalPwd);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "✅ 恢复成功！请重启App", Toast.LENGTH_LONG).show();
                                refreshAll();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "❌ 恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private void showRestorePasswordDialog(android.net.Uri uri) {
        String name = "";
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idxName = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idxName >= 0) name = c.getString(idxName);
                c.close();
            }
        } catch (Exception ignored) {}
        android.widget.EditText etPwd = new android.widget.EditText(this);
        etPwd.setHint("输入备份密码");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("恢复: " + (name.isEmpty() ? uri.getLastPathSegment() : name))
                .setMessage("⚠️ 恢复将覆盖当前所有数据！确定继续？")
                .setView(etPwd)
                .setPositiveButton("恢复", (d2, w2) -> {
                    String pwd = etPwd.getText().toString();
                    new Thread(() -> {
                        try {
                            java.io.InputStream is = getContentResolver().openInputStream(uri);
                            byte[] encrypted = readAllBytes(is);
                            com.sister.habits.utils.BackupExportHelper helper = new com.sister.habits.utils.BackupExportHelper(this);
                            helper.importBackupBytes(encrypted, pwd);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "✅ 恢复成功！请重启App", Toast.LENGTH_LONG).show();
                                refreshAll();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "❌ 恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private byte[] readAllBytes(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        is.close();
        return baos.toByteArray();
    }
        
    

        private void showBackupListDialog(java.io.File[] backups) {
        String[] names = new String[backups.length];
        for (int i = 0; i < backups.length; i++) {
            names[i] = backups[i].getName();
        }
        final java.io.File[] finalBackups = backups;
        new AlertDialog.Builder(this)
                .setTitle("📥 选择备份文件恢复")
                .setItems(names, (d, which) -> {
                    java.io.File selected = finalBackups[which];
                    android.widget.EditText etPwd = new android.widget.EditText(this);
                    etPwd.setHint("输入备份密码");
                    etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    new AlertDialog.Builder(this)
                            .setTitle("恢复: " + selected.getName())
                            .setMessage("⚠️ 恢复将覆盖当前所有数据！确定继续？")
                            .setView(etPwd)
                            .setPositiveButton("恢复", (d2, w2) -> {
                                String pwd = etPwd.getText().toString();
                                new Thread(() -> {
                                    try {
                                        com.sister.habits.utils.BackupExportHelper helper = new com.sister.habits.utils.BackupExportHelper(this);
                                        helper.importBackup(selected, pwd);
                                        runOnUiThread(() -> {
                                            Toast.makeText(this, "✅ 恢复成功！请重启App", Toast.LENGTH_LONG).show();
                                            refreshAll();
                                        });
                                    } catch (Exception e) {
                                        runOnUiThread(() -> Toast.makeText(this, "❌ 恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                    }
                                }).start();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void listBackupFiles() {
        // Android 11+ 用 MediaStore 扫描（无需存储权限）
        java.io.File[] backups = com.sister.habits.utils.BackupExportHelper.findBackupFiles(this);
        if (backups == null || backups.length == 0) {
            Toast.makeText(this, "📂 Download 目录下暂无 .habitbak 备份文件", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder("📂 找到 " + backups.length + " 个备份文件:\n\n");
        for (java.io.File f : backups) {
            sb.append("• ").append(f.getName()).append("\n  (").append(f.length() / 1024).append(" KB)\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("📂 已有备份")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

        // ==================== 同步中心（v3.0.68 重构：单入口一屏三区） ====================
    private void showSyncCenterDialog() {
        final com.sister.habits.sync.HubSync hub = syncManager.getHubSync();
        final com.sister.habits.sync.RemoteSync remote = syncManager.getRemoteSync();
        String deviceKey = com.sister.habits.utils.DeviceIdentity.getDeviceKey(this);
        String shortKey = deviceKey != null && deviceKey.length() >= 19 ? deviceKey.substring(0, 19) : deviceKey;
        String nickname = profile.getNickname();
        String model = android.os.Build.MODEL;

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 8, 32, 8);

        // ===== 区1：状态总览（只读） =====
        layout.addView(sectionTitle("状态总览"));
        String serverUrl = hub.getServerUrl();
        String serverLine = (serverUrl != null && !serverUrl.isEmpty()) ? serverUrl : "未配置";
        TextView tvOverview = new TextView(this);
        tvOverview.setText("同步模式: " + syncManager.getSyncModeText() +
                "\n中心服务器: " + serverLine +
                "\n上次同步: " + hub.getLastSyncInfo());
        tvOverview.setTextSize(13);
        tvOverview.setTextColor(0xFF333333);
        tvOverview.setPadding(12, 10, 12, 10);
        android.graphics.drawable.GradientDrawable ovBg = new android.graphics.drawable.GradientDrawable();
        ovBg.setCornerRadius(8);
        ovBg.setColor(0xFFF5F5F5);
        tvOverview.setBackground(ovBg);
        android.widget.LinearLayout.LayoutParams ovLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        ovLp.setMargins(0, 0, 0, 2);
        tvOverview.setLayoutParams(ovLp);
        layout.addView(tvOverview);
        TextView tvDevice = new TextView(this);
        tvDevice.setText("本机: " + nickname + " (" + model + ")" + (shortKey != null ? "  ID " + shortKey : ""));
        tvDevice.setTextSize(11);
        tvDevice.setTextColor(0xFF999999);
        tvDevice.setPadding(4, 0, 4, 10);
        layout.addView(tvDevice);

        // ===== 区2：家人入网（核心操作） =====
        layout.addView(sectionTitle("家人入网"));
        final Button btnFamilyQr = new Button(this);
        btnFamilyQr.setText("生成配置二维码（家人扫码自动入网）");
        btnFamilyQr.setTextSize(14);
        btnFamilyQr.setAllCaps(false);
        btnFamilyQr.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable qrBg = new android.graphics.drawable.GradientDrawable();
        qrBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        qrBg.setCornerRadius(8);
        qrBg.setColor(0xFF43A047);
        btnFamilyQr.setBackground(qrBg);
        int dp24 = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
        int dp48 = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
        btnFamilyQr.setPadding(dp24, 0, dp24, 0);
        btnFamilyQr.setMinHeight(dp48);
        btnFamilyQr.setOnClickListener(v -> showFamilyQrDialog(hub));
        layout.addView(btnFamilyQr);
        // 次操作行：展示本机配对码 | 扫码配对
        android.widget.LinearLayout rowOps = new android.widget.LinearLayout(this);
        rowOps.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = 8;
        rowOps.setLayoutParams(rowLp);
        Button btnMyQr = makeSyncButton("展示本机配对码", 0xFF1976D2);
        Button btnScanQr = makeSyncButton("扫码配对", 0xFF1976D2);
        btnMyQr.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnScanQr.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnMyQr.setOnClickListener(v -> showMyQrDialog(nickname, model));
        btnScanQr.setOnClickListener(v -> launchQrScan());
        rowOps.addView(btnMyQr);
        rowOps.addView(btnScanQr);
        layout.addView(rowOps);

        // ===== 区3：同步配置 =====
        layout.addView(sectionTitle("同步配置"));
        final android.widget.RadioGroup rgMode = new android.widget.RadioGroup(this);
        final android.widget.RadioButton rbP2p = new android.widget.RadioButton(this);
        rbP2p.setText("仅局域网（家庭内直连）");
        final android.widget.RadioButton rbServer = new android.widget.RadioButton(this);
        rbServer.setText("仅中心服务器（跨网络）");
        final android.widget.RadioButton rbAuto = new android.widget.RadioButton(this);
        rbAuto.setText("自动（推荐，服务器→局域网）");
        int curMode = syncManager.getSyncMode();
        if (curMode == com.sister.habits.sync.SyncManager.MODE_SERVER_ONLY) rbServer.setChecked(true);
        else if (curMode == com.sister.habits.sync.SyncManager.MODE_AUTO) rbAuto.setChecked(true);
        else rbP2p.setChecked(true);
        rgMode.addView(rbP2p);
        rgMode.addView(rbServer);
        rgMode.addView(rbAuto);
        layout.addView(rgMode);
        final TextView tvServerStatus = new TextView(this);
        tvServerStatus.setText(hub.getServerStatusText());
        tvServerStatus.setTextSize(12);
        tvServerStatus.setTextColor(0xFF1976D2);
        tvServerStatus.setPadding(4, 4, 4, 2);
        layout.addView(tvServerStatus);
        final android.widget.EditText etServerUrl = new android.widget.EditText(this);
        etServerUrl.setHint("服务器地址，如 http://100.65.13.111:23458/habit/cuiyi");
        etServerUrl.setSingleLine(true);
        etServerUrl.setTextSize(13);
        String existingUrl = hub.getServerUrl();
        if (existingUrl != null) etServerUrl.setText(existingUrl);
        layout.addView(etServerUrl);
        final android.widget.EditText etServerToken = new android.widget.EditText(this);
        etServerToken.setHint("家庭Token（二维码自动携带，留空则不鉴权）");
        etServerToken.setSingleLine(true);
        etServerToken.setTextSize(13);
        etServerToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etServerToken);

        // ▸ 高级选项（默认折叠）
        final boolean[] advancedOpen = {false};
        final Button btnAdvanced = new Button(this);
        btnAdvanced.setText("▸ 高级选项（局域网直连 / WebDAV）");
        btnAdvanced.setTextSize(13);
        btnAdvanced.setAllCaps(false);
        btnAdvanced.setTextColor(0xFF666666);
        btnAdvanced.setBackgroundColor(0x00000000);
        btnAdvanced.setPadding(4, 12, 4, 4);
        final android.widget.LinearLayout advLayout = new android.widget.LinearLayout(this);
        advLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        advLayout.setVisibility(android.view.View.GONE);
        btnAdvanced.setOnClickListener(v -> {
            advancedOpen[0] = !advancedOpen[0];
            advLayout.setVisibility(advancedOpen[0] ? android.view.View.VISIBLE : android.view.View.GONE);
            btnAdvanced.setText(advancedOpen[0] ? "▾ 高级选项（点击收起）" : "▸ 高级选项（局域网直连 / WebDAV）");
        });
        layout.addView(btnAdvanced);
        final android.widget.CheckBox cbHubMode = new android.widget.CheckBox(this);
        cbHubMode.setText("本设备作为局域网直连中枢");
        cbHubMode.setTextSize(13);
        cbHubMode.setChecked(syncManager.isHubModeEnabled());
        advLayout.addView(cbHubMode);
        final android.widget.EditText etHubIp = new android.widget.EditText(this);
        etHubIp.setHint("指定局域网直连 IP（可选）");
        etHubIp.setSingleLine(true);
        etHubIp.setTextSize(13);
        advLayout.addView(etHubIp);
        advLayout.addView(hintText("WebDAV 备用通道（非必填）"));
        final android.widget.EditText etUrl = new android.widget.EditText(this);
        etUrl.setHint("WebDAV地址，如 https://dav.jianguoyun.com/dav/");
        etUrl.setSingleLine(true);
        etUrl.setTextSize(13);
        advLayout.addView(etUrl);
        final android.widget.EditText etUser = new android.widget.EditText(this);
        etUser.setHint("账号（坚果云/Nextcloud用户名）");
        etUser.setSingleLine(true);
        etUser.setTextSize(13);
        advLayout.addView(etUser);
        final android.widget.EditText etPass = new android.widget.EditText(this);
        etPass.setHint("应用密码（非登录密码）");
        etPass.setSingleLine(true);
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setTextSize(13);
        advLayout.addView(etPass);
        final android.widget.EditText etSyncPass = new android.widget.EditText(this);
        etSyncPass.setHint("数据加密密码（默认0903）");
        etSyncPass.setSingleLine(true);
        etSyncPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etSyncPass.setTextSize(13);
        advLayout.addView(etSyncPass);
        android.widget.LinearLayout rowTools = new android.widget.LinearLayout(this);
        rowTools.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams rowToolsLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        rowToolsLp.topMargin = 8;
        rowTools.setLayoutParams(rowToolsLp);
        Button btnScanNet = makeSyncButton("扫描局域网设备", 0xFF78909C);
        Button btnClearCache = makeSyncButton("清除发现缓存", 0xFF78909C);
        btnScanNet.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnClearCache.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnScanNet.setOnClickListener(v -> {
            tvServerStatus.setText("正在扫描局域网...");
            tvServerStatus.setTextColor(0xFF1976D2);
            hub.clearDiscoveredHubs();
            hub.scanNetwork(150, new com.sister.habits.sync.SyncCallback() {
                @Override
                public void onStatusUpdate(String status) {
                    runOnUiThread(() -> tvServerStatus.setText(status));
                }
                @Override
                public void onHubFound(String ip, String deviceId) {
                    runOnUiThread(() -> tvServerStatus.setText("发现设备: " + ip));
                }
                @Override
                public void onSyncComplete(boolean success, String message) {
                    runOnUiThread(() -> tvServerStatus.setText((success ? "完成: " : "失败: ") + message));
                }
                @Override
                public void onScanProgress(int scanned, int total) {
                    runOnUiThread(() -> tvServerStatus.setText("扫描中 " + scanned + "/" + total));
                }
            });
        });
        btnClearCache.setOnClickListener(v -> {
            hub.clearDiscoveredHubs();
            tvServerStatus.setText("已清除发现缓存");
            tvServerStatus.setTextColor(0xFF666666);
        });
        rowTools.addView(btnScanNet);
        rowTools.addView(btnClearCache);
        advLayout.addView(rowTools);
        Button btnClearAll = new Button(this);
        btnClearAll.setText("清除服务器与WebDAV配置");
        btnClearAll.setTextSize(12);
        btnClearAll.setAllCaps(false);
        btnClearAll.setTextColor(0xFFE53935);
        btnClearAll.setBackgroundColor(0x00000000);
        btnClearAll.setOnClickListener(v -> {
            hub.clearServerConfig();
            remote.clearConfig();
            Toast.makeText(this, "已清除服务器与WebDAV配置", Toast.LENGTH_SHORT).show();
        });
        advLayout.addView(btnClearAll);
        layout.addView(advLayout);

        // ===== 立即同步 =====
        final Button btnSyncNow = new Button(this);
        btnSyncNow.setText("立即同步");
        btnSyncNow.setTextSize(14);
        btnSyncNow.setAllCaps(false);
        btnSyncNow.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable syncBg = new android.graphics.drawable.GradientDrawable();
        syncBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        syncBg.setCornerRadius(8);
        syncBg.setColor(0xFF1976D2);
        btnSyncNow.setBackground(syncBg);
        btnSyncNow.setPadding(dp24, 0, dp24, 0);
        btnSyncNow.setMinHeight(dp48);
        android.widget.LinearLayout.LayoutParams syncLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        syncLp.topMargin = 12;
        btnSyncNow.setLayoutParams(syncLp);
        btnSyncNow.setOnClickListener(v -> {
            Toast.makeText(this, "同步中...", Toast.LENGTH_SHORT).show();
            syncManager.triggerFullSync();
            Toast.makeText(this, "已触发全同步（服务器/局域网/WebDAV）", Toast.LENGTH_LONG).show();
        });
        layout.addView(btnSyncNow);

        scrollView.addView(layout);
        new AlertDialog.Builder(this)
                .setTitle("🔄 同步中心")
                .setView(scrollView)
                .setPositiveButton("保存", (d, w) -> {
                    if (rbServer.isChecked()) syncManager.setSyncMode(com.sister.habits.sync.SyncManager.MODE_SERVER_ONLY);
                    else if (rbAuto.isChecked()) syncManager.setSyncMode(com.sister.habits.sync.SyncManager.MODE_AUTO);
                    else syncManager.setSyncMode(com.sister.habits.sync.SyncManager.MODE_P2P_ONLY);
                    String urlSaved = etServerUrl.getText().toString().trim();
                    if (!urlSaved.isEmpty()) {
                        hub.setServerConfig(urlSaved, etServerToken.getText().toString().trim());
                    }
                    String webdavUrl = etUrl.getText().toString().trim();
                    if (!webdavUrl.isEmpty()) {
                        remote.setConfig(webdavUrl, etUser.getText().toString(), etPass.getText().toString(),
                                etSyncPass.getText().toString().isEmpty() ? "0903" : etSyncPass.getText().toString());
                    }
                    syncManager.setHubModeEnabled(cbHubMode.isChecked());
                    String hubIp = etHubIp.getText().toString().trim();
                    if (!hubIp.isEmpty()) {
                        hub.setManualHubIp(hubIp);
                    }
                    Toast.makeText(this, "已保存，模式: " + syncManager.getSyncModeText(), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 同步中心统一样式按钮 */
    private Button makeSyncButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8);
        bg.setColor(color);
        btn.setBackground(bg);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        int dp44 = (int) (44 * getResources().getDisplayMetrics().density + 0.5f);
        btn.setPadding(dp16, 0, dp16, 0);
        btn.setMinHeight(dp44);
        return btn;
    }

    /** 生成配置二维码（家人扫码自动入网） */
    private void showFamilyQrDialog(final com.sister.habits.sync.HubSync hub) {
        String qrContent = com.sister.habits.utils.QRCodeHelper.buildSyncConfigQrContent(this);
        if (qrContent == null) {
            Toast.makeText(this, "请先保存服务器地址，再生成二维码", Toast.LENGTH_SHORT).show();
            return;
        }
        android.graphics.Bitmap qrBitmap = com.sister.habits.utils.QRCodeHelper.generateQrBitmap(qrContent);
        if (qrBitmap != null) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageBitmap(qrBitmap);
            iv.setPadding(32, 32, 32, 32);
            new AlertDialog.Builder(this)
                    .setTitle("同步配置二维码")
                    .setMessage("家人设备扫码后自动配置（无需手动输入）\n\n服务器: " + hub.getServerUrl())
                    .setView(iv)
                    .setPositiveButton("关闭", null)
                    .show();
        } else {
            Toast.makeText(this, "二维码生成失败", Toast.LENGTH_SHORT).show();
        }
    }

    /** 展示本机配对码 */
    private void showMyQrDialog(String nickname, String model) {
        String qrContent = com.sister.habits.utils.QRCodeHelper.buildDeviceQrContent(this);
        android.graphics.Bitmap qrBitmap = com.sister.habits.utils.QRCodeHelper.generateQrBitmap(qrContent);
        if (qrBitmap != null) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageBitmap(qrBitmap);
            iv.setPadding(32, 32, 32, 32);
            new AlertDialog.Builder(this)
                    .setTitle("本机配对码")
                    .setMessage("让对方扫描此码进行配对\n昵称: " + nickname + " | 设备: " + model)
                    .setView(iv)
                    .setPositiveButton("关闭", null)
                    .show();
        } else {
            Toast.makeText(this, "二维码生成失败", Toast.LENGTH_SHORT).show();
        }
    }

    /** 启动扫码配对 */
    private void launchQrScan() {
        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
        options.setPrompt("扫描对方设备的配对码");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        qrScanLauncher.launch(options);
    }

    /** 同步中心分区标题（统一风格，不带花哨emoji） */
    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText("— " + text + " —");
        tv.setTextSize(13);
        tv.setTextColor(0xFF1976D2);
        tv.setPadding(4, 12, 4, 4);
        return tv;
    }
    /** 灰色提示文字 */
    private TextView hintText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(0xFF666666);
        tv.setPadding(4, 0, 4, 4);
        return tv;
    }

    private void showPinManageDialog() {
        final boolean sysOn = PinHelper.isSystemLockEnabled(this);
        final boolean pinOn = PinHelper.isAppPinEnabled(this);
        final boolean pinSet = PinHelper.isPinSet(this);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        android.widget.CheckBox cbSysLock = new android.widget.CheckBox(this);
        cbSysLock.setText("🔒 系统锁屏验证");
        cbSysLock.setChecked(sysOn);
        cbSysLock.setTextSize(16);
        cbSysLock.setPadding(0, 8, 0, 8);
        layout.addView(cbSysLock);

        android.widget.CheckBox cbAppPin = new android.widget.CheckBox(this);
        cbAppPin.setText("🔑 应用PIN码验证");
        cbAppPin.setChecked(pinOn);
        cbAppPin.setTextSize(16);
        cbAppPin.setPadding(0, 8, 0, 8);
        layout.addView(cbAppPin);

        android.widget.Button btnSetPin = new android.widget.Button(this);
        btnSetPin.setText(pinSet ? "✏️ 修改PIN码" : "🔢 设置PIN码");
        btnSetPin.setAllCaps(false);
        btnSetPin.setTextSize(14);
        android.widget.LinearLayout.LayoutParams bLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, 12, 0, 0);
        btnSetPin.setLayoutParams(bLp);
        layout.addView(btnSetPin);

        android.widget.Button btnReset = new android.widget.Button(this);
        btnReset.setText("🔄 重置为默认（仅系统锁屏）");
        btnReset.setAllCaps(false);
        btnReset.setTextSize(14);
        android.widget.LinearLayout.LayoutParams rLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        rLp.setMargins(0, 8, 0, 0);
        btnReset.setLayoutParams(rLp);
        layout.addView(btnReset);

        new AlertDialog.Builder(this)
                .setTitle("🔐 安全防护管理")
                .setView(layout)
                .setPositiveButton("💾 保存", (d, w) -> {
                    boolean newSys = cbSysLock.isChecked();
                    boolean newPin = cbAppPin.isChecked();
                    if (!newSys && !newPin) {
                        Toast.makeText(this, "至少保留一种验证方式", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    PinHelper.setSystemLockEnabled(this, newSys);
                    PinHelper.setAppPinEnabled(this, newPin);
                    Toast.makeText(this, "✅ 安全设置已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回", (d2, w2) -> showSystemMenu())
                .show();

        btnSetPin.setOnClickListener(v -> {
            showAuthVerifyDialog(() -> showPinSetupDialog());
        });

        btnReset.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("确认重置")
                    .setMessage("将关闭所有安全防护，只保留系统锁屏。确定？")
                    .setPositiveButton("确定", (dd, ww) -> {
                        PinHelper.disableAll(this);
                        Toast.makeText(this, "已重置为系统锁屏验证", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }    /** 更新假期范围显示 */
    private void updateHolidayRangesText(android.widget.TextView tv, java.util.List<String[]> list) {
        if (list.isEmpty()) {
            tv.setText("（暂无假期）");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                String[] r = list.get(i);
                sb.append(i+1).append(". ").append(r[0]).append(" ~ ").append(r[1]).append("\n");
            }
            tv.setText(sb.toString().trim());
        }
    }
}
