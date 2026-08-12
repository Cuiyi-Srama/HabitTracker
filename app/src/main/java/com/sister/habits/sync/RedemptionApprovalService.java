package com.sister.habits.sync;

import com.sister.habits.data.dao.CoinTransactionDao;
import com.sister.habits.data.dao.RedemptionDao;
import com.sister.habits.data.models.CoinTransaction;
import com.sister.habits.data.models.Redemption;

import java.util.List;

/**
 * 兑换审批服务 —— v3.0.61 防双花核心
 *
 * 架构背景：孩子端提交兑换申请时【不再立即扣款】，扣款时点迁移到家长审批通过时。
 * 审批通过时以【本机流水 SUM(amount)】作为权威余额校验，余额不足则自动拒绝，
 * 从机制上杜绝多设备离线双花（各自本地扣款 → 同步后流水合并重复扣）。
 *
 * 所有方法均提供 DAO 注入版本（可单元测试，不依赖 Context），
 * 与 EarningService 保持同一测试模式。
 */
public class RedemptionApprovalService {

    /** 默认用户（Redemption 实体无 userId 字段，统一使用 sister） */
    public static final String DEFAULT_USER = "sister";

    /** 审批结果 */
    public enum ApproveResult {
        APPROVED,          // 已批准并扣款
        REJECTED_INSUFFICIENT  // 余额不足，自动拒绝
    }

    // ==================== Context 版本（生产环境调用） ====================
    public static ApproveResult approve(android.content.Context ctx, Redemption redemption, String deviceId) {
        com.sister.habits.data.AppDatabase db = com.sister.habits.data.AppDatabase.getInstance(ctx);
        return approve(db.coinTransactionDao(), db.redemptionDao(), redemption, deviceId);
    }

    public static void reject(android.content.Context ctx, Redemption redemption) {
        com.sister.habits.data.AppDatabase db = com.sister.habits.data.AppDatabase.getInstance(ctx);
        reject(db.redemptionDao(), redemption);
    }

    // ==================== DAO 注入版本（可单测） ====================

    /**
     * 审批通过：校验权威余额，充足则扣款并确认，不足则自动拒绝。
     *
     * @param coinDao   流水 DAO（权威余额 = SUM(amount)）
     * @param redDao    兑换 DAO
     * @param redemption 待审批的兑换申请
     * @param deviceId  本设备 ID（记录扣款流水来源）
     * @return APPROVED 或 REJECTED_INSUFFICIENT
     */
    public static ApproveResult approve(CoinTransactionDao coinDao, RedemptionDao redDao,
                                        Redemption redemption, String deviceId) {
        if (redemption == null) return ApproveResult.REJECTED_INSUFFICIENT;

        Integer balance = coinDao.getBalance(DEFAULT_USER);
        int current = balance != null ? balance : 0;

        if (current < redemption.coinsCost) {
            // 余额不足 → 自动拒绝，不产生任何流水（不引入负积分）
            redDao.process(redemption.id, "rejected", System.currentTimeMillis(),
                    "余额不足，自动拒绝（需 " + redemption.coinsCost + " 分，当前 " + current + " 分）");
            return ApproveResult.REJECTED_INSUFFICIENT;
        }

        // 余额充足 → 扣款 + 确认
        int newBalance = current - redemption.coinsCost;
        CoinTransaction ct = new CoinTransaction(
                DEFAULT_USER,
                -redemption.coinsCost, newBalance,
                "shop_spend", "兑换: " + redemption.itemName,
                deviceId);
        coinDao.insert(ct);
        redDao.process(redemption.id, "confirmed", System.currentTimeMillis(),
                "已确认 ✅");

        // 同步字段：新流水待同步
        return ApproveResult.APPROVED;
    }

    /**
     * 审批拒绝：直接标记拒绝（v3.0.61 起孩子端提交时不扣款，故无需退款）。
     */
    public static void reject(RedemptionDao redDao, Redemption redemption) {
        if (redemption == null) return;
        redDao.process(redemption.id, "rejected", System.currentTimeMillis(), "已拒绝 ❌");
    }

    /**
     * 一次性迁移（v3.0.61 首次启动调用）：
     * 旧版本（≤v3.0.60）孩子端提交兑换时【已立即扣款】，本次升级将扣款时点改为审批通过。
     * 为保证新旧语义一致，对所有仍处于 pending 的旧兑换申请执行退款（+coinsCost 流水），
     * 之后由审批流程按新规则扣款。
     *
     * 副作用：顺带修复旧版多设备双花造成的负余额（退款流水使 SUM 恢复真实值）。
     *
     * @return 退款笔数（0 = 无需迁移或已迁移）
     */
    public static int migratePendingRefunds(CoinTransactionDao coinDao, RedemptionDao redDao, String deviceId) {
        List<Redemption> pendings = redDao.getByStatus("pending");
        if (pendings.isEmpty()) return 0;

        int refunded = 0;
        for (Redemption r : pendings) {
            Integer balance = coinDao.getBalance(DEFAULT_USER);
            int current = balance != null ? balance : 0;
            CoinTransaction ct = new CoinTransaction(
                    DEFAULT_USER,
                    r.coinsCost, current + r.coinsCost,
                    "parent_adjust", "兑换申请转审批制退回: " + r.itemName,
                    deviceId);
            coinDao.insert(ct);
            refunded++;
        }
        return refunded;
    }
}