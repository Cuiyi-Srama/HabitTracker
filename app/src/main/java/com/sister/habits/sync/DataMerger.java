package com.sister.habits.sync;

import android.util.Log;

import com.sister.habits.data.dao.CheckInDao;
import com.sister.habits.data.dao.CoinTransactionDao;
import com.sister.habits.data.dao.RedemptionDao;
import com.sister.habits.data.dao.TaskDao;
import com.sister.habits.data.models.CheckIn;
import com.sister.habits.data.models.CoinTransaction;
import com.sister.habits.data.models.Redemption;
import com.sister.habits.data.models.Task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * 数据合并器——幂等合并（Idempotent Merge）
 * 策略：以记录自带 UUID 主键(id) 作为全局唯一键去重，数学上保证同一条记录永不重复入库。
 * 打卡同日冲突（不同设备同一天打卡）→ 保留 timestamp 更早的一条（先打卡者胜）。
 * 流水按 id 去重，各自保留。
 * 不上传删除操作，只上传新增
 */
public class DataMerger {
    private static final String TAG = "DataMerger";

    private final CheckInDao checkInDao;
    private final CoinTransactionDao coinDao;
    private final TaskDao taskDao;
    private final RedemptionDao redemptionDao;
    private final com.sister.habits.data.AppDatabase appDb;

    public DataMerger(com.sister.habits.data.AppDatabase appDb,
                      CheckInDao checkInDao, CoinTransactionDao coinDao,
                      TaskDao taskDao, RedemptionDao redemptionDao) {
        this.appDb = appDb;
        this.checkInDao = checkInDao;
        this.coinDao = coinDao;
        this.taskDao = taskDao;
        this.redemptionDao = redemptionDao;
    }

    /**
    /**
     * 合并打卡记录——幂等模式
     * 以 UUID 主键(id) 去重：同一条记录永不重复入库。
     * 同日冲突（不同设备同一天打卡，id 不同）→ 保留 timestamp 更早的一条（先打卡者胜），
     * 远端更早则 REPLACE 覆盖本地，本地更早则跳过远端。
     */
    public void mergeCheckIns(List<CheckIn> remoteList) {
        List<CheckIn> localList = checkInDao.getByUser("sister");
        Set<String> localIds = new HashSet<>();
        Map<String, CheckIn> localByDate = new HashMap<>();
        for (CheckIn c : localList) {
            localIds.add(c.id);
            localByDate.put(c.userId + "|" + c.date, c);
        }
        int added = 0;
        for (CheckIn remote : remoteList) {
            // 幂等：同 id 已存在 → 跳过
            if (localIds.contains(remote.id)) {
                Log.d(TAG, "⏭️ 跳过重复打卡(id): " + remote.date + " " + remote.id);
                continue;
            }
            String key = remote.userId + "|" + remote.date;
            CheckIn existing = localByDate.get(key);
            if (existing != null) {
                // 同日冲突：保留先打卡者（timestamp 更早）
                if (remote.timestamp < existing.timestamp) {
                    remote.synced = true;
                    checkInDao.insert(remote); // REPLACE：unique[userId,date] 覆盖本地更晚记录
                    localIds.add(remote.id);
                    localByDate.put(key, remote);
                    added++;
                    Log.d(TAG, "📥 同日冲突远端更早，覆盖本地: " + remote.date + " 来自 " + remote.deviceId);
                } else {
                    Log.d(TAG, "⏭️ 同日冲突保留本地更早: " + remote.date + " 来自 " + remote.deviceId);
                }
            } else {
                remote.synced = true;
                checkInDao.insert(remote);
                localIds.add(remote.id);
                localByDate.put(key, remote);
                added++;
                Log.d(TAG, "📥 新增打卡: " + remote.date + " 来自 " + remote.deviceId);
            }
        }
        Log.d(TAG, "合并打卡完成: 新增 " + added + "/" + remoteList.size());
    }
    /**
     * 合并金币流水——幂等模式
     * 以 UUID 主键(id) 去重：同一条记录永不重复入库（id 由产生端生成，全局唯一）。
     * 所有流水各自保留，不覆盖。
     */
    public void mergeCoinTransactions(List<CoinTransaction> remoteList) {
        List<CoinTransaction> localList = coinDao.getByUser("sister");
        Set<String> localIds = new HashSet<>();
        for (CoinTransaction c : localList) {
            localIds.add(c.id);
        }
        int added = 0;
        for (CoinTransaction remote : remoteList) {
            // 幂等：同 id 已存在 → 跳过
            if (!localIds.contains(remote.id)) {
                remote.synced = true;
                coinDao.insert(remote);
                localIds.add(remote.id);
                added++;
                Log.d(TAG, "📥 新增流水: " + remote.type + " " + remote.amount + " 来自 " + remote.deviceId);
            } else {
                Log.d(TAG, "⏭️ 跳过重复流水(id): " + remote.type + " " + remote.amount + " " + remote.id);
            }
        }
        Log.d(TAG, "合并流水完成: 新增 " + added + "/" + remoteList.size());
    }
    /**
     * 合并任务——叠加模式
     * 以 UUID 去重，各自保留
     */
    public void mergeTasks(List<Task> remoteList) {
        List<Task> localList = taskDao.getAll();
        Set<String> localIds = new HashSet<>();
        for (Task t : localList) {
            localIds.add(t.id);
        }

        int added = 0;
        for (Task remote : remoteList) {
            if (!localIds.contains(remote.id)) {
                remote.synced = true;
                taskDao.insert(remote);
                localIds.add(remote.id);
                added++;
                Log.d(TAG, "📥 新增任务: " + remote.title + " 来自 " + remote.deviceId);
            }
        }
        Log.d(TAG, "合并任务完成: 新增 " + added + "/" + remoteList.size());
    }

    /**
     * 合并兑换记录——叠加模式
     * 以 UUID 去重，各自保留
     */
    public void mergeRedemptions(List<Redemption> remoteList) {
        List<Redemption> localList = redemptionDao.getAll();
        Set<String> localIds = new HashSet<>();
        for (Redemption r : localList) {
            localIds.add(r.id);
        }

        int added = 0;
        for (Redemption remote : remoteList) {
            if (!localIds.contains(remote.id)) {
                remote.synced = true;
                redemptionDao.insert(remote);
                localIds.add(remote.id);
                added++;
                Log.d(TAG, "📥 新增兑换: " + remote.itemName + " 来自 " + remote.deviceId);
            }
        }
        Log.d(TAG, "合并兑换完成: 新增 " + added + "/" + remoteList.size());
    }

    // ==================== 全量数据合并（新增类型） ====================
    public void mergeVocabularies(List<com.sister.habits.data.models.Vocabulary> remoteList) {
        com.sister.habits.data.dao.VocabularyDao dao = appDb.vocabularyDao();
        int added = 0;
        for (com.sister.habits.data.models.Vocabulary v : remoteList) {
            try {
                com.sister.habits.data.models.Vocabulary existing = dao.getById(v.id);
                if (existing == null) {
                    dao.insert(v);
                    added++;
                }
            } catch (Exception e) {
                Log.d(TAG, "跳过重复单词");
            }
        }
        Log.d(TAG, "合并单词完成: 新增 " + added + "/" + remoteList.size());
    }

    /**
     * 合并复习进度——以 wordId 唯一索引 REPLACE 合并
     * 同一单词：以复习进度较深的记录为准（stage 大者胜）
     */
    public void mergeWordReviews(java.util.List<com.sister.habits.data.models.WordReview> remoteList) {
        if (remoteList == null || remoteList.isEmpty()) return;
        com.sister.habits.data.dao.WordReviewDao dao = appDb.wordReviewDao();
        int added = 0;
        for (com.sister.habits.data.models.WordReview r : remoteList) {
            try {
                com.sister.habits.data.models.WordReview local = dao.getByWordId(r.wordId, r.bankId);
                if (local == null) {
                    dao.insert(r); // REPLACE：wordId 唯一，安全
                    added++;
                } else if (r.stage > local.stage) {
                    // 远端进度更深 → 更新本地（保留更深的复习阶段）
                    local.stage = r.stage;
                    local.nextReviewAt = r.nextReviewAt;
                    local.lastReviewedAt = r.lastReviewedAt;
                    local.correctCount = r.correctCount;
                    local.wrongCount = r.wrongCount;
                    dao.update(local);
                    added++;
                }
            } catch (Exception e) {
                Log.d(TAG, "跳过复习记录: " + e.getMessage());
            }
        }
        Log.d(TAG, "合并复习进度完成: 更新 " + added + "/" + remoteList.size());
    }


    /**
     * 商品图标跨设备同步：base64解码到本机 getFilesDir()/shop_images/
     * 两台设备包名相同 -> filesDir 绝对路径天然一致 -> iconUrl 无需改写
     */
    public void mergeShopImages(android.content.Context context, java.util.Map<String, String> images) {
        if (images == null || images.isEmpty()) return;
        java.io.File dir = new java.io.File(context.getFilesDir(), "shop_images");
        if (!dir.exists()) dir.mkdirs();
        int saved = 0;
        for (java.util.Map.Entry<String, String> e : images.entrySet()) {
            try {
                String remotePath = e.getKey();
                String fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);
                if (fileName.isEmpty()) continue;
                java.io.File out = new java.io.File(dir, fileName);
                if (out.exists()) continue; // 已有则跳过
                byte[] data = android.util.Base64.decode(e.getValue(), android.util.Base64.NO_WRAP);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                fos.write(data);
                fos.close();
                saved++;
            } catch (Exception ex) {
                Log.w(TAG, "图片保存失败: " + ex.getMessage());
            }
        }
        Log.d(TAG, "商品图片同步完成: 新增 " + saved + "/" + images.size());
    }

    public void mergeShopItems(List<com.sister.habits.data.models.ShopItem> remoteList) {
        com.sister.habits.data.dao.ShopItemDao dao = appDb.shopItemDao();
        int added = 0;
        for (com.sister.habits.data.models.ShopItem s : remoteList) {
            try {
                com.sister.habits.data.models.ShopItem existing = dao.getById(s.id);
                if (existing == null) { dao.insert(s); added++; }
            } catch (Exception e) { Log.d(TAG, "跳过重复商品"); }
        }
        Log.d(TAG, "合并商品完成: 新增 " + added);
    }

    public void mergeWishlistItems(List<com.sister.habits.data.models.WishlistItem> remoteList) {
        com.sister.habits.data.dao.WishlistDao dao = appDb.wishlistDao();
        int added = 0;
        for (com.sister.habits.data.models.WishlistItem w : remoteList) {
            try { dao.insert(w); added++; }
            catch (Exception e) { Log.d(TAG, "跳过重复心愿单"); }
        }
        Log.d(TAG, "合并心愿单完成: 新增 " + added);
    }

    public void mergeWordBanks(List<com.sister.habits.data.models.WordBank> remoteList) {
        com.sister.habits.data.dao.WordBankDao dao = appDb.wordBankDao();
        int added = 0;
        for (com.sister.habits.data.models.WordBank b : remoteList) {
            try {
                com.sister.habits.data.models.WordBank existing = dao.getById(b.id);
                if (existing == null) { dao.insert(b); added++; }
            } catch (Exception e) { Log.d(TAG, "跳过重复词库"); }
        }
        Log.d(TAG, "合并词库完成: 新增 " + added);
    }

    public void mergeEconomyConfig(com.sister.habits.data.models.EconomyConfig remote) {
        com.sister.habits.data.dao.EconomyConfigDao dao = appDb.economyConfigDao();
        try {
            com.sister.habits.data.models.EconomyConfig local = dao.getConfig();
            if (local == null) { dao.setConfig(remote); }
        } catch (Exception e) {
            Log.d(TAG, "合并经济配置失败");
        }
    }
    public void mergeCoinEarnings(List<com.sister.habits.data.models.CoinEarning> remoteList) {
        com.sister.habits.data.dao.CoinEarningDao dao = appDb.coinEarningDao();
        int added = 0;
        for (com.sister.habits.data.models.CoinEarning ce : remoteList) {
            try {
                dao.insert(ce);
                added++;
            } catch (Exception e) { Log.d(TAG, "跳过重复积分审批"); }
        }
        Log.d(TAG, "合并积分审批完成: 新增 " + added);
    }

    /** 合并作业关卡配置 */
    public void mergeGateConfig(com.sister.habits.data.models.GateConfig remote) {
        com.sister.habits.data.dao.GateConfigDao dao = appDb.gateConfigDao();
        try {
            com.sister.habits.data.models.GateConfig local = dao.getConfig();
            if (local == null) {
                remote.synced = true;
                dao.insert(remote);
            } else if (remote.updatedAt > local.updatedAt) {
                remote.id = 1; // 保证主键一致
                remote.synced = true;
                dao.update(remote);
            }
        } catch (Exception e) {
            android.util.Log.d("DataMerger", "合并GateConfig失败: " + e.getMessage());
        }
    }

    /** 合并每日作业记录 */
    public void mergeDailyGates(java.util.List<com.sister.habits.data.models.DailyGate> remoteList) {
        com.sister.habits.data.dao.DailyGateDao dao = appDb.dailyGateDao();
        int added = 0;
        for (com.sister.habits.data.models.DailyGate g : remoteList) {
            try {
                com.sister.habits.data.models.DailyGate existing = dao.getByDate(g.date);
                if (existing == null) {
                    g.synced = true;
                    dao.insert(g);
                    added++;
                } else if (g.reviewedAt > existing.reviewedAt) {
                    // 远程更新更新，覆盖本地
                    g.synced = true;
                    dao.update(g);
                }
            } catch (Exception e) {
                android.util.Log.d("DataMerger", "跳过重复DailyGate: " + g.date);
            }
        }
        android.util.Log.d("DataMerger", "合并DailyGate完成: 新增/更新 " + added + "/" + remoteList.size());
    }
}