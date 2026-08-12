package com.sister.habits.sync;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sister.habits.data.dao.CheckInDao;
import com.sister.habits.data.dao.CoinTransactionDao;
import com.sister.habits.data.dao.RedemptionDao;
import com.sister.habits.data.dao.TaskDao;
import com.sister.habits.data.models.CheckIn;
import com.sister.habits.data.models.CoinTransaction;
import com.sister.habits.data.models.Redemption;
import com.sister.habits.data.models.Task;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * DataMerger 单元测试 —— 覆盖叠加模式的去重与新增逻辑
 * mergeXxx 前四类只使用各自 DAO，appDb 传 null 即可（不触碰 appDb 相关方法）
 */
public class DataMergerTest {

    private DataMerger newMerger(CheckInDao c, CoinTransactionDao t, TaskDao tk, RedemptionDao r) {
        return new DataMerger(null, c, t, tk, r);
    }

    // ==================== CheckIn ====================
    @Test
    public void mergeCheckIns_同日冲突本地更早跳过_新日期插入() {
        CheckInDao dao = mock(CheckInDao.class);
        CheckIn local = new CheckIn("sister", "2026-08-01", 1, 5, "devA");
        local.timestamp = 1000L;
        when(dao.getByUser("sister")).thenReturn(Collections.singletonList(local));
        // 与本地同一天（不同设备、不同id、打卡更晚）→ 同日冲突保留本地更早 → 跳过
        CheckIn dup = new CheckIn("sister", "2026-08-01", 2, 5, "devB");
        dup.timestamp = 2000L;
        // 新日期 → 应插入
        CheckIn fresh = new CheckIn("sister", "2026-08-02", 1, 5, "devB");
        DataMerger merger = newMerger(dao, null, null, null);
        merger.mergeCheckIns(Arrays.asList(dup, fresh));
        verify(dao, times(1)).insert(fresh);
        verify(dao, never()).insert(dup);
        assertTrue("新记录应标记已同步", fresh.synced);
    }

    @Test
    public void mergeCheckIns_同id幂等_跳过() {
        CheckInDao dao = mock(CheckInDao.class);
        CheckIn local = new CheckIn("sister", "2026-08-01", 1, 5, "devA");
        when(dao.getByUser("sister")).thenReturn(Collections.singletonList(local));
        // 同 id（同一记录被重复推送）→ 幂等跳过
        CheckIn dup = new CheckIn("sister", "2026-08-01", 1, 5, "devA");
        dup.id = local.id;
        DataMerger merger = newMerger(dao, null, null, null);
        merger.mergeCheckIns(Collections.singletonList(dup));
        verify(dao, never()).insert(any(CheckIn.class));
    }

    @Test
    public void mergeCheckIns_同日冲突远端更早_覆盖本地() {
        CheckInDao dao = mock(CheckInDao.class);
        // 本地打卡较晚
        CheckIn local = new CheckIn("sister", "2026-08-01", 2, 5, "devA");
        local.timestamp = 2000L;
        when(dao.getByUser("sister")).thenReturn(Collections.singletonList(local));
        // 远端同一天但打卡更早（先打卡者胜）→ 应 REPLACE 覆盖本地
        CheckIn remote = new CheckIn("sister", "2026-08-01", 1, 5, "devB");
        remote.timestamp = 1000L;
        DataMerger merger = newMerger(dao, null, null, null);
        merger.mergeCheckIns(Collections.singletonList(remote));
        verify(dao, times(1)).insert(remote);
        assertTrue("远端更早的打卡应标记已同步", remote.synced);
    }

    @Test
    public void mergeCheckIns_远程列表为空_不插入() {
        CheckInDao dao = mock(CheckInDao.class);
        when(dao.getByUser("sister")).thenReturn(Collections.<CheckIn>emptyList());
        DataMerger merger = newMerger(dao, null, null, null);
        merger.mergeCheckIns(Collections.<CheckIn>emptyList());
        verify(dao, never()).insert(any(CheckIn.class));
    }
    // ==================== CoinTransaction ====================

    @Test
    public void mergeCoinTransactions_同id去重_新流水插入() {
        CoinTransactionDao dao = mock(CoinTransactionDao.class);
        CoinTransaction local = newCoinTx("task", 5, 1000L, "devA");
        when(dao.getByUser("sister")).thenReturn(Collections.singletonList(local));
        // 同 id（同一流水被重复推送）→ 应跳过
        CoinTransaction dup = newCoinTx("task", 5, 1000L, "devA");
        dup.id = local.id;
        // 新 id（即使五元组完全一样，也是不同记录）→ 应插入
        CoinTransaction fresh = newCoinTx("task", 10, 2000L, "devB");
        DataMerger merger = newMerger(null, dao, null, null);
        merger.mergeCoinTransactions(Arrays.asList(dup, fresh));
        verify(dao, times(1)).insert(fresh);
        verify(dao, never()).insert(dup);
        assertTrue("新流水应标记已同步", fresh.synced);
    }

    @Test
    public void mergeCoinTransactions_同秒同额不同设备_都保留() {
        CoinTransactionDao dao = mock(CoinTransactionDao.class);
        when(dao.getByUser("sister")).thenReturn(Collections.<CoinTransaction>emptyList());
        // 旧逻辑会误判重复：同秒同额同类型不同设备 → 现在按 id 各自保留
        CoinTransaction a = newCoinTx("task", 5, 1000L, "devA");
        CoinTransaction b = newCoinTx("task", 5, 1000L, "devB");
        DataMerger merger = newMerger(null, dao, null, null);
        merger.mergeCoinTransactions(Arrays.asList(a, b));
        verify(dao, times(2)).insert(any(CoinTransaction.class));
    }
    // ==================== WordReview ====================
    @Test
    public void mergeWordReviews_stage相同但远端今日已学_更新lastReviewedAt() {
        com.sister.habits.data.AppDatabase appDb = mock(com.sister.habits.data.AppDatabase.class);
        com.sister.habits.data.dao.WordReviewDao dao = mock(com.sister.habits.data.dao.WordReviewDao.class);
        when(appDb.wordReviewDao()).thenReturn(dao);
        long yesterday = 1000L, today = 2000L;
        // 本地：stage=1，最后学习=昨天（今日已学标记为空）
        com.sister.habits.data.models.WordReview local = new com.sister.habits.data.models.WordReview();
        local.wordId = "w1"; local.bankId = "b1";
        local.stage = 1;
        local.lastReviewedAt = yesterday;
        when(dao.getByWordId("w1", "b1")).thenReturn(local);
        // 远端：stage=1（相同，旧逻辑会跳过！），但今天学过 → 必须更新今日已学标记
        com.sister.habits.data.models.WordReview remote = new com.sister.habits.data.models.WordReview();
        remote.wordId = "w1"; remote.bankId = "b1";
        remote.stage = 1;
        remote.lastReviewedAt = today;
        DataMerger merger = new DataMerger(appDb, null, null, null, null);
        merger.mergeWordReviews(java.util.Collections.singletonList(remote));
        verify(dao, times(1)).update(local);
        assertTrue("lastReviewedAt 应更新为远端（今日已学标记不丢）", local.lastReviewedAt == today);
    }

    @Test
    public void mergeWordReviews_远端stage更深_更新进度() {
        com.sister.habits.data.AppDatabase appDb = mock(com.sister.habits.data.AppDatabase.class);
        com.sister.habits.data.dao.WordReviewDao dao = mock(com.sister.habits.data.dao.WordReviewDao.class);
        when(appDb.wordReviewDao()).thenReturn(dao);
        com.sister.habits.data.models.WordReview local = new com.sister.habits.data.models.WordReview();
        local.wordId = "w2"; local.bankId = "b1";
        local.stage = 1; local.lastReviewedAt = 500L;
        when(dao.getByWordId("w2", "b1")).thenReturn(local);
        com.sister.habits.data.models.WordReview remote = new com.sister.habits.data.models.WordReview();
        remote.wordId = "w2"; remote.bankId = "b1";
        remote.stage = 3; remote.lastReviewedAt = 600L;
        remote.nextReviewAt = 9000L; remote.correctCount = 3; remote.wrongCount = 1;
        DataMerger merger = new DataMerger(appDb, null, null, null, null);
        merger.mergeWordReviews(java.util.Collections.singletonList(remote));
        verify(dao, times(1)).update(local);
        assertTrue("stage 应取更深", local.stage == 3);
        assertTrue("lastReviewedAt 应取更晚", local.lastReviewedAt == 600L);
    }

    @Test
    public void mergeWordReviews_远端更旧_不更新() {
        com.sister.habits.data.AppDatabase appDb = mock(com.sister.habits.data.AppDatabase.class);
        com.sister.habits.data.dao.WordReviewDao dao = mock(com.sister.habits.data.dao.WordReviewDao.class);
        when(appDb.wordReviewDao()).thenReturn(dao);
        com.sister.habits.data.models.WordReview local = new com.sister.habits.data.models.WordReview();
        local.wordId = "w3"; local.bankId = "b1";
        local.stage = 2; local.lastReviewedAt = 2000L;
        when(dao.getByWordId("w3", "b1")).thenReturn(local);
        // 远端 stage 更浅且 lastReviewedAt 更旧 → 不应更新
        com.sister.habits.data.models.WordReview remote = new com.sister.habits.data.models.WordReview();
        remote.wordId = "w3"; remote.bankId = "b1";
        remote.stage = 1; remote.lastReviewedAt = 1000L;
        DataMerger merger = new DataMerger(appDb, null, null, null, null);
        merger.mergeWordReviews(java.util.Collections.singletonList(remote));
        verify(dao, never()).update(any(com.sister.habits.data.models.WordReview.class));
    }

    private CoinTransaction newCoinTx(String type, int amount, long createdAt, String deviceId) {
        CoinTransaction t = new CoinTransaction();
        t.userId = "sister";
        t.type = type;
        t.amount = amount;
        t.createdAt = createdAt;
        t.deviceId = deviceId;
        return t;
    }

    // ==================== Task ====================

    @Test
    public void mergeTasks_按UUID去重_新任务插入() {
        TaskDao dao = mock(TaskDao.class);
        Task local = newTask("task-1");
        when(dao.getAll()).thenReturn(Collections.singletonList(local));

        Task dup = newTask("task-1");
        Task fresh = newTask("task-2");

        DataMerger merger = newMerger(null, null, dao, null);
        merger.mergeTasks(Arrays.asList(dup, fresh));

        verify(dao, times(1)).insert(fresh);
        verify(dao, never()).insert(dup);
        assertTrue("新任务应标记已同步", fresh.synced);
    }

    private Task newTask(String id) {
        Task t = new Task();
        t.id = id;
        t.title = "任务" + id;
        t.deviceId = "devB";
        return t;
    }

    // ==================== Redemption ====================

    @Test
    public void mergeRedemptions_按UUID去重_新兑换插入() {
        RedemptionDao dao = mock(RedemptionDao.class);
        Redemption local = newRedemption("red-1");
        when(dao.getAll()).thenReturn(Collections.singletonList(local));

        Redemption dup = newRedemption("red-1");
        Redemption fresh = newRedemption("red-2");

        DataMerger merger = newMerger(null, null, null, dao);
        merger.mergeRedemptions(Arrays.asList(dup, fresh));

        verify(dao, times(1)).insert(fresh);
        verify(dao, never()).insert(dup);
        assertTrue("新兑换应标记已同步", fresh.synced);
    }

    private Redemption newRedemption(String id) {
        Redemption r = new Redemption();
        r.id = id;
        r.itemName = "商品" + id;
        r.deviceId = "devB";
        return r;
    }
}