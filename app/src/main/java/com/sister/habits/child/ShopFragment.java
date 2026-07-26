package com.sister.habits.child;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.Redemption;
import com.sister.habits.data.models.ShopItem;
import com.sister.habits.sync.SyncManager;

import java.util.List;

/**
 * 商城Fragment——孩子可以浏览商品并发起兑换申请
 */
public class ShopFragment extends Fragment {

    private AppDatabase db;
    private SyncManager syncManager;
    private RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shop, container, false);
        db = AppDatabase.getInstance(requireContext());
        syncManager = SyncManager.getInstance(requireContext());

        recyclerView = view.findViewById(R.id.recycler_shop);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadShopItems();
        return view;
    }

    private void loadShopItems() {
        List<ShopItem> items = db.shopItemDao().getActive();
        recyclerView.setAdapter(new ShopAdapter(items, this::requestRedemption));
    }

    private void requestRedemption(ShopItem item) {
        Integer balance = db.coinTransactionDao().getBalance("sister");
        int currentBalance = balance != null ? balance : 0;

        if (currentBalance < item.priceCoins) {
            Toast.makeText(getContext(), "金币不够哦！还差 " + (item.priceCoins - currentBalance) + " 个 🪙",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建兑换申请
        int newBalance = currentBalance - item.priceCoins;
        Redemption redemption = new Redemption();
        redemption.shopItemId = item.id;
        redemption.itemName = item.name;
        redemption.coinsCost = item.priceCoins;
        redemption.coinsBalanceBefore = currentBalance;
        redemption.coinsBalanceAfter = newBalance;
        redemption.deviceId = syncManager.getDeviceId();
        db.redemptionDao().insert(redemption);

        // 扣除金币（记账）
        com.sister.habits.data.models.CoinTransaction ct =
                new com.sister.habits.data.models.CoinTransaction(
                        "sister", -item.priceCoins, newBalance,
                        "shop_spend", "兑换: " + item.name,
                        syncManager.getDeviceId());
        db.coinTransactionDao().insert(ct);

        // 触发同步
        syncManager.onDataChanged();

        Toast.makeText(getContext(), "✅ 兑换申请已提交！等家长审批～", Toast.LENGTH_LONG).show();
        loadShopItems(); // 刷新
    }

    // 简单适配器
    private static class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {
        private final List<ShopItem> items;
        private final OnRedeemListener listener;

        interface OnRedeemListener { void onRedeem(ShopItem item); }

        ShopAdapter(List<ShopItem> items, OnRedeemListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ShopItem item = items.get(position);
            holder.text1.setText(item.name + "  🪙" + item.priceCoins);
            holder.text2.setText(item.description);
            holder.itemView.setOnClickListener(v -> listener.onRedeem(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) { super(v); text1 = v.findViewById(android.R.id.text1); text2 = v.findViewById(android.R.id.text2); }
        }
    }
}