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

    public DataMerger(CheckInDao checkInDao, CoinTransactionDao coinDao,
                      TaskDao taskDao, RedemptionDao redemptionDao) {
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
}