package com.sister.habits.child;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
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
                    .inflate(R.layout.item_shop, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ShopItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvDesc.setText(item.description);
            holder.tvPrice.setText("🪙 " + item.priceCoins);

            // 加载商品图片（如果有URL）
            if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(item.iconUrl)
                        .apply(new RequestOptions()
                                .placeholder(com.sister.habits.R.drawable.ic_launcher)
                                .error(com.sister.habits.R.drawable.ic_launcher)
                                .transform(new RoundedCorners(12)))
                        .into(holder.ivIcon);
            } else {
                holder.ivIcon.setImageResource(com.sister.habits.R.drawable.ic_launcher);
            }

            holder.itemView.setOnClickListener(v -> listener.onRedeem(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvDesc, tvPrice;
            ViewHolder(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.iv_shop_icon);
                tvName = v.findViewById(R.id.tv_shop_name);
                tvDesc = v.findViewById(R.id.tv_shop_desc);
                tvPrice = v.findViewById(R.id.tv_shop_price);
            }
        }
    }
}