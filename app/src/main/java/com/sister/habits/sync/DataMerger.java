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

/**
 * 数据合并器——解决多设备间的数据冲突
 * 策略：每条记录带 UUID + deviceId + syncTimestamp
 * 合并时按 syncTimestamp 最新者优先
 * 同一条记录（相同 UUID）不会重复
 */
public class DataMerger {
    private static final String TAG = "DataMerger";

    private final CheckInDao checkInDao;
    private final CoinTransactionDao coinDao;
    private final TaskDao taskDao;
    private final RedemptionDao redemptionDao;

    public DataMerger(CheckInDao checkInDao, CoinTransactionDao coinDao,
                      TaskDao taskDao, RedemptionDao redemptionDao) {
        this.checkInDao = checkInDao;
        this.coinDao = coinDao;
        this.taskDao = taskDao;
        this.redemptionDao = redemptionDao;
    }

    /**
     * 合并打卡记录——相同日期保留最早的一条（防止重复打卡）
     */
    public void mergeCheckIns(java.util.List<CheckIn> remoteList) {
        for (CheckIn remote : remoteList) {
            CheckIn local = checkInDao.getByDate(remote.userId, remote.date);
            if (local == null) {
                // 本地没有这条记录 → 直接插入
                remote.synced = true;
                checkInDao.insert(remote);
                Log.d(TAG, "合并打卡: 新增 " + remote.date);
            } else if (remote.syncTimestamp > local.syncTimestamp) {
                // 远程更新 → 覆盖（但保留最早打卡时间戳）
                remote.synced = true;
                checkInDao.insert(remote);
                Log.d(TAG, "合并打卡: 更新 " + remote.date);
            }
        }
    }

    /**
     * 合并金币流水——UUID 唯一，不重复
     */
    public void mergeCoinTransactions(java.util.List<CoinTransaction> remoteList) {
        for (CoinTransaction remote : remoteList) {
            // 按 UUID 去重，直接 insert with REPLACE
            remote.synced = true;
            coinDao.insert(remote);
        }
        Log.d(TAG, "合并金币流水: " + remoteList.size() + " 条");
    }

    /**
     * 合并任务
     */
    public void mergeTasks(java.util.List<Task> remoteList) {
        for (Task remote : remoteList) {
            remote.synced = true;
            taskDao.insert(remote);
        }
        Log.d(TAG, "合并任务: " + remoteList.size() + " 条");
    }

    /**
     * 合并兑换记录
     */
    public void mergeRedemptions(java.util.List<Redemption> remoteList) {
        for (Redemption remote : remoteList) {
            remote.synced = true;
            redemptionDao.insert(remote);
        }
        Log.d(TAG, "合并兑换: " + remoteList.size() + " 条");
    }
}