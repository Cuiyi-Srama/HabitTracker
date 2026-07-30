package com.sister.habits.child;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.sister.habits.utils.NotificationHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城Fragment - 支持常驻商品(购物车模式)和限量商品(直接兑换)
 */
public class ShopFragment extends Fragment {
    private AppDatabase db;
    private SyncManager syncManager;
    private RecyclerView recyclerView;
    private LinearLayout cartBar;
    private TextView tvCartInfo;
    private Button btnCartSubmit;
    private Button btnAll, btnNormal, btnLimited, btnWish;
    
    private String currentTab = "all"; // all/normal/limited/wishlist
    private Map<String, Integer> cart = new HashMap<>(); // itemId -> quantity

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shop, container, false);
        db = AppDatabase.getInstance(requireContext());
        syncManager = SyncManager.getInstance(requireContext());
        
        btnAll = view.findViewById(R.id.btn_shop_all);
        btnNormal = view.findViewById(R.id.btn_shop_normal);
        btnLimited = view.findViewById(R.id.btn_shop_limited);
        btnWish = view.findViewById(R.id.btn_shop_wishlist);
        cartBar = view.findViewById(R.id.cart_bar);
        tvCartInfo = view.findViewById(R.id.tv_cart_info);
        btnCartSubmit = view.findViewById(R.id.btn_cart_submit);
        
        btnAll.setOnClickListener(v -> switchTab("all"));
        btnNormal.setOnClickListener(v -> switchTab("normal"));
        btnLimited.setOnClickListener(v -> switchTab("limited"));
        btnWish.setOnClickListener(v -> switchTab("wishlist"));
        
        btnCartSubmit.setOnClickListener(v -> submitCart());
        
        recyclerView = view.findViewById(R.id.recycler_shop);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        switchTab("all");
        return view;
    }
    
    private void switchTab(String tab) {
        currentTab = tab;
        // Reset button colors
        btnAll.setBackgroundColor(0xFFE0E0E0); btnAll.setTextColor(0xFF333333);
        btnNormal.setBackgroundColor(0xFFE0E0E0); btnNormal.setTextColor(0xFF333333);
        btnLimited.setBackgroundColor(0xFFE0E0E0); btnLimited.setTextColor(0xFF333333);
        btnWish.setBackgroundColor(0xFFE0E0E0); btnWish.setTextColor(0xFF333333);
        
        switch (tab) {
            case "all":
                btnAll.setBackgroundColor(0xFFFF9800); btnAll.setTextColor(0xFFFFFFFF);
                loadShopItems();
                cartBar.setVisibility(View.GONE);
                break;
            case "normal":
                btnNormal.setBackgroundColor(0xFFFF9800); btnNormal.setTextColor(0xFFFFFFFF);
                loadByType("normal");
                cartBar.setVisibility(View.VISIBLE);
                updateCartBar();
                break;
            case "limited":
                btnLimited.setBackgroundColor(0xFFFF9800); btnLimited.setTextColor(0xFFFFFFFF);
                loadByType("limited");
                cartBar.setVisibility(View.GONE);
                break;
            case "wishlist":
                btnWish.setBackgroundColor(0xFFFF9800); btnWish.setTextColor(0xFFFFFFFF);
                loadWishlistItems();
                cartBar.setVisibility(View.GONE);
                break;
        }
    }
    
    private void loadByType(String itemType) {
        List<ShopItem> items = db.shopItemDao().getActiveByType(itemType);
        boolean isNormal = "normal".equals(itemType);
        recyclerView.setAdapter(new ShopAdapter(items, 
            isNormal ? this::addToCart : this::requestRedemption,
            isNormal ? this::removeFromCart : null,
            cart));
    }
    
    private void loadShopItems() {
        List<ShopItem> items = db.shopItemDao().getActive();
        recyclerView.setAdapter(new ShopAdapter(items, this::onItemClick, null, cart));
    }
    
    private void loadWishlistItems() {
        List<com.sister.habits.data.models.WishlistItem> wishes = db.wishlistDao().getAll();
        List<ShopItem> items = new ArrayList<>();
        for (com.sister.habits.data.models.WishlistItem w : wishes) {
            ShopItem si = db.shopItemDao().getById(w.shopItemId);
            if (si != null && si.active) items.add(si);
        }
        recyclerView.setAdapter(new ShopAdapter(items, this::onItemClick, null, cart));
    }
    
    /** 全部Tab下，根据商品类型分发点击 */
    private void onItemClick(ShopItem item) {
        if ("normal".equals(item.itemType)) {
            showQuantityDialog(item);
        } else {
            requestRedemption(item);
        }
    }
    
    /** 常驻商品：弹出数量选择对话框 */
    private void showQuantityDialog(ShopItem item) {
        String[] qtyOptions = {"1个", "2个", "3个", "4个", "5个"};
        new android.app.AlertDialog.Builder(getContext())
            .setTitle(item.name)
            .setItems(qtyOptions, (d, which) -> {
                addToCart(item, which + 1);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /** 添加到购物车 */
    private void addToCart(ShopItem item) {
        showQuantityDialog(item);
    }
    
    private void addToCart(ShopItem item, int qty) {
        String key = item.id;
        int current = cart.containsKey(key) ? cart.get(key) : 0;
        cart.put(key, current + qty);
        updateCartBar();
        Toast.makeText(getContext(), "➕ " + item.name + " x" + qty + " 已加入购物车", Toast.LENGTH_SHORT).show();
    }
    
    /** 从购物车移除 */
    private void removeFromCart(ShopItem item) {
        String key = item.id;
        int current = cart.containsKey(key) ? cart.get(key) : 0;
        if (current <= 1) {
            cart.remove(key);
        } else {
            cart.put(key, current - 1);
        }
        updateCartBar();
        // 刷新当前列表
        if ("normal".equals(currentTab)) {
            loadByType("normal");
        }
    }
    
    /** 更新购物车栏显示 */
    private void updateCartBar() {
        int totalQty = 0;
        int totalCost = 0;
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            ShopItem item = db.shopItemDao().getById(e.getKey());
            if (item != null) {
                totalQty += e.getValue();
                totalCost += item.priceCoins * e.getValue();
            }
        }
        if (totalQty == 0) {
            tvCartInfo.setText("🛒 购物车为空");
            btnCartSubmit.setEnabled(false);
        } else {
            tvCartInfo.setText("🛒 " + totalQty + "件商品 | 合计: " + totalCost + "分");
            btnCartSubmit.setEnabled(true);
        }
    }
    
    /** 批量提交购物车 */
    private void submitCart() {
        if (cart.isEmpty()) {
            Toast.makeText(getContext(), "购物车为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Integer balance = db.coinTransactionDao().getBalance("sister");
        int currentBalance = balance != null ? balance : 0;
        
        // 计算总价
        int totalCost = 0;
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            ShopItem item = db.shopItemDao().getById(e.getKey());
            if (item != null) {
                int cost = item.priceCoins * e.getValue();
                totalCost += cost;
                summary.append(item.name).append(" x").append(e.getValue()).append(" = ").append(cost).append("分\n");
            }
        }
        
        if (currentBalance < totalCost) {
            Toast.makeText(getContext(), "金币不够！需要 " + totalCost + " 分，当前 " + currentBalance + " 分", Toast.LENGTH_LONG).show();
            return;
        }
        
        // 确认对话框
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("确认兑换")
            .setMessage(summary.toString() + "\n合计: " + totalCost + " 分\n余额: " + (currentBalance - totalCost) + " 分")
            .setPositiveButton("确认提交", (d, w) -> {
                int newBalance = currentBalance;
                for (Map.Entry<String, Integer> e : cart.entrySet()) {
                    ShopItem item = db.shopItemDao().getById(e.getKey());
                    if (item != null) {
                        int cost = item.priceCoins * e.getValue();
                        newBalance -= cost;
                        Redemption redemption = new Redemption();
                        redemption.shopItemId = item.id;
                        redemption.itemName = item.name + " x" + e.getValue();
                        redemption.coinsCost = cost;
                        redemption.coinsBalanceBefore = currentBalance;
                        redemption.coinsBalanceAfter = newBalance;
                        redemption.deviceId = syncManager.getDeviceId();
                        db.redemptionDao().insert(redemption);
                    }
                }
                // 一次性扣款
                com.sister.habits.data.models.CoinTransaction ct =
                    new com.sister.habits.data.models.CoinTransaction(
                        "sister", -totalCost, currentBalance - totalCost,
                        "shop_spend", "批量兑换: " + cart.size() + "种商品",
                        syncManager.getDeviceId());
                db.coinTransactionDao().insert(ct);
                syncManager.onDataChanged();
                NotificationHelper.createChannel(requireContext());
                NotificationHelper.notifyRedemption(requireContext(), "批量兑换(" + cart.size() + "件)", 0);
                
                cart.clear();
                updateCartBar();
                loadByType("normal");
                Toast.makeText(getContext(), "✅ 已提交！等家长审批～", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /** 限量商品：直接提交兑换（保持原逻辑） */
    private void requestRedemption(ShopItem item) {
        Integer balance = db.coinTransactionDao().getBalance("sister");
        int currentBalance = balance != null ? balance : 0;
        if (currentBalance < item.priceCoins) {
            Toast.makeText(getContext(), "金币不够哦！还差 " + (item.priceCoins - currentBalance) + " 个 🪙",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int newBalance = currentBalance - item.priceCoins;
        Redemption redemption = new Redemption();
        redemption.shopItemId = item.id;
        redemption.itemName = item.name;
        redemption.coinsCost = item.priceCoins;
        redemption.coinsBalanceBefore = currentBalance;
        redemption.coinsBalanceAfter = newBalance;
        redemption.deviceId = syncManager.getDeviceId();
        db.redemptionDao().insert(redemption);
        
        com.sister.habits.data.models.CoinTransaction ct =
                new com.sister.habits.data.models.CoinTransaction(
                        "sister", -item.priceCoins, newBalance,
                        "shop_spend", "兑换: " + item.name,
                        syncManager.getDeviceId());
        db.coinTransactionDao().insert(ct);
        syncManager.onDataChanged();
        NotificationHelper.createChannel(requireContext());
        NotificationHelper.notifyRedemption(requireContext(), item.name, redemption.id);
        Toast.makeText(getContext(), "✅ 兑换申请已提交！等家长审批～", Toast.LENGTH_LONG).show();
        
        if ("limited".equals(currentTab)) loadByType("limited");
        else if ("all".equals(currentTab)) loadShopItems();
    }
    
    // ===== Adapter =====
    class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {
        private final List<ShopItem> items;
        private final OnItemClickListener listener;
        private final OnItemClickListener removeListener;
        private final Map<String, Integer> cartRef;
        
        interface OnItemClickListener { void onClick(ShopItem item); }
        
        ShopAdapter(List<ShopItem> items, OnItemClickListener listener, 
                    OnItemClickListener removeListener, Map<String, Integer> cartRef) {
            this.items = items;
            this.listener = listener;
            this.removeListener = removeListener;
            this.cartRef = cartRef;
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
            boolean isNormal = "normal".equals(item.itemType);
            
            // 常驻商品显示类型标签和购物车数量
            if (isNormal) {
                holder.tvName.setText(item.name + " 🔄");
                int qty = cartRef != null && cartRef.containsKey(item.id) ? cartRef.get(item.id) : 0;
                if (qty > 0) {
                    holder.tvPrice.setText("🪙 " + item.priceCoins + " | 🛒x" + qty);
                }
            } else {
                holder.tvName.setText(item.name + " 🔥");
            }
            
            if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(item.iconUrl)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_launcher)
                                .error(R.drawable.ic_launcher)
                                .transform(new RoundedCorners(12)))
                        .into(holder.ivIcon);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_launcher);
            }

            holder.itemView.setOnClickListener(v -> listener.onClick(item));
            
            // 愿望清单按钮
            boolean isWishlisted = db.wishlistDao().getByShopItemId(item.id) != null;
            holder.btnWishlist.setBackgroundColor(isWishlisted ? 0xFFFFD700 : 0xFFE0E0E0);
            holder.btnWishlist.setOnClickListener(v2 -> {
                if (isWishlisted) {
                    db.wishlistDao().deleteByShopItemId(item.id);
                    holder.btnWishlist.setBackgroundColor(0xFFE0E0E0);
                    Toast.makeText(v2.getContext(), "已移除心愿 ⭐", Toast.LENGTH_SHORT).show();
                } else {
                    db.wishlistDao().insert(com.sister.habits.data.models.WishlistItem.create(item.id));
                    holder.btnWishlist.setBackgroundColor(0xFFFFD700);
                    Toast.makeText(v2.getContext(), "已加入心愿 ⭐", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            View btnWishlist;
            TextView tvName, tvDesc, tvPrice;
            ViewHolder(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.iv_shop_icon);
                btnWishlist = v.findViewById(R.id.btn_wishlist);
                tvName = v.findViewById(R.id.tv_shop_name);
                tvDesc = v.findViewById(R.id.tv_shop_desc);
                tvPrice = v.findViewById(R.id.tv_shop_price);
            }
        }
    }
}
