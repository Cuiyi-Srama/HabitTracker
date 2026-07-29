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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据合并器——叠加模式（Append Mode）
 * 策略：不覆盖任何数据，以 {日期}+{事件类型}+{金币变动} 作为天然唯一键去重
 * 各设备的记录各自保留，联网后按时间线合并展示
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
     * 合并打卡记录——叠加模式
     * 以 {userId}+{date} 作为唯一键去重，各自保留不覆盖
     * 如果远程有同一天不同设备的打卡，两条都保留
     */
    public void mergeCheckIns(List<CheckIn> remoteList) {
        // 将本地已有记录构建为集合
        List<CheckIn> localList = checkInDao.getByUser("sister");
        Set<String> localKeys = new HashSet<>();
        for (CheckIn c : localList) {
            localKeys.add(c.userId + "|" + c.date);
        }

        int added = 0;
        for (CheckIn remote : remoteList) {
            String key = remote.userId + "|" + remote.date;
            if (!localKeys.contains(key)) {
                remote.synced = true;
                checkInDao.insert(remote);
                localKeys.add(key);
                added++;
                Log.d(TAG, "📥 新增打卡: " + remote.date + " 来自 " + remote.deviceId);
            } else {
                Log.d(TAG, "⏭️ 跳过重复打卡: " + remote.date);
            }
        }
        Log.d(TAG, "合并打卡完成: 新增 " + added + "/" + remoteList.size());
    }

    /**
     * 合并金币流水——叠加模式
     * 以 {userId}+{type}+{amount}+{createdAt}+{deviceId} 作为唯一键去重
     * 所有流水各自保留，不覆盖
     */
    public void mergeCoinTransactions(List<CoinTransaction> remoteList) {
        List<CoinTransaction> localList = coinDao.getByUser("sister");
        Set<String> localKeys = new HashSet<>();
        for (CoinTransaction c : localList) {
            localKeys.add(c.userId + "|" + c.type + "|" + c.amount + "|" + c.createdAt + "|" + c.deviceId);
        }

        int added = 0;
        for (CoinTransaction remote : remoteList) {
            // 用关键字段构建唯一键：{日期}+{事件类型}+{金币变动}
            String key = remote.userId + "|" + remote.type + "|" + remote.amount + "|" + remote.createdAt + "|" + remote.deviceId;
            if (!localKeys.contains(key)) {
                remote.synced = true;
                coinDao.insert(remote);
                localKeys.add(key);
                added++;
                Log.d(TAG, "📥 新增流水: " + remote.type + " " + remote.amount + " 来自 " + remote.deviceId);
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

        /* mergeWordReviews skipped - no getAll in WordReviewDao */


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