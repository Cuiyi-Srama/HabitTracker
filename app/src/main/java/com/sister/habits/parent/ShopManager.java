package com.sister.habits.parent;

import android.app.AlertDialog;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.ShopItem;
import com.sister.habits.utils.MenuHelper;
import com.sister.habits.utils.SoundHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 商城模块管理器（从 ParentActivity 拆分）
 * 负责：商城管理菜单 / 商品增删改查
 */
public class ShopManager {
    private final ParentActivity activity;
    private android.app.AlertDialog manageDialog;
    private LinearLayout manageContainer;
    private boolean manageMulti;
    private android.widget.TextView manageTvTitle;
    private final AppDatabase db;
    private final SoundHelper soundHelper;
    private String selectedShopImagePath;
    private boolean shopMultiSelect = false;
    private final Set<String> shopSelectedIds = new HashSet<>();
    private View currentShopDialogView;

    public ShopManager(ParentActivity activity, AppDatabase db, SoundHelper soundHelper) {
        this.activity = activity;
        this.db = db;
        this.soundHelper = soundHelper;
    }

    public void onImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(activity.getContentResolver().openInputStream(uri), null, opts);
            int maxDim = 1280;
            int scale = 1;
            while (opts.outWidth / scale > maxDim || opts.outHeight / scale > maxDim) {
                scale *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;
            java.io.InputStream is2 = activity.getContentResolver().openInputStream(uri);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is2, null, opts);
            is2.close();
            if (bmp == null) throw new Exception("无法读取图片");
            String fileName = "shop_" + System.currentTimeMillis() + ".jpg";
            File outFile = new File(activity.getFilesDir(), "shop_images/" + fileName);
            outFile.getParentFile().mkdirs();
            java.io.OutputStream os = new java.io.FileOutputStream(outFile);
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, os);
            os.close();
            bmp.recycle();
            selectedShopImagePath = outFile.getAbsolutePath();
            if (currentShopDialogView != null) {
                ImageView preview = currentShopDialogView.findViewById(R.id.iv_image_preview);
                TextView tvName = currentShopDialogView.findViewById(R.id.tv_image_name);
                if (preview != null && tvName != null) {
                    preview.setVisibility(View.VISIBLE);
                    tvName.setVisibility(View.VISIBLE);
                    tvName.setText("已选择: " + fileName);
                    Glide.with(activity).load(outFile).into(preview);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ShopManager", "图片选择失败", e);
            Toast.makeText(activity, "❌ 图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    public void showShopMenu() {
        int shopCount = db.shopItemDao().getAll().size();
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        String[] items = {
                "➕ 上架新商品",
                "✏️ 管理已有商品（" + shopCount + "件）",
                "✅ 兑换审批（" + pendingCount + "项待处理）"
        };
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("🏪 商城管理")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showAddShopItemDialog(); break;
                        case 1: showManageShopDialog(); break;
                        case 2: activity.loadPendingApprovals(); Toast.makeText(activity, "已刷新审批列表", Toast.LENGTH_SHORT).show(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> activity.showSettingsDialog())
                .show();
    }
    public void showAddShopItemDialog() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_shop_item, null);
        android.widget.EditText etName = view.findViewById(R.id.et_item_name);
        android.widget.EditText etDesc = view.findViewById(R.id.et_item_desc);
        android.widget.EditText etPrice = view.findViewById(R.id.et_item_price);
        android.widget.EditText etCategory = view.findViewById(R.id.et_item_category);
        Button btnPickImage = view.findViewById(R.id.btn_pick_image);
        ImageView ivPreview = view.findViewById(R.id.iv_image_preview);
        TextView tvImageName = view.findViewById(R.id.tv_image_name);

        // 重置之前选中的图片
        selectedShopImagePath = null;

        // 从相册选图按钮
        btnPickImage.setOnClickListener(v -> {
            soundHelper.playClickSound();
            currentShopDialogView = view;  // 保存引用，图片选择后更新预览
            activity.launchShopImagePicker();
        });

        new AlertDialog.Builder(activity)
                .setTitle("添加上架商品")
                .setView(view)
                .setPositiveButton("上架", (d, w) -> {
                    ShopItem item = new ShopItem();
                    item.name = etName.getText().toString();
                    item.description = etDesc.getText().toString();
                    try { item.priceCoins = Integer.parseInt(etPrice.getText().toString()); }
                    catch (Exception e) { item.priceCoins = 50; }
                    item.category = etCategory.getText().toString();
                    android.widget.RadioGroup rgType = view.findViewById(R.id.rg_item_type);
                    item.itemType = (rgType.getCheckedRadioButtonId() == R.id.rb_limited) ? "limited" : "normal";
                    try { item.stock = Integer.parseInt(((android.widget.EditText) view.findViewById(R.id.et_item_stock)).getText().toString()); }
                    catch (Exception e) { item.stock = -1; }
                    item.iconUrl = selectedShopImagePath != null ? selectedShopImagePath : "";
                    item.updatedAt = System.currentTimeMillis();  // LWW时间戳（v3.0.62）
                    db.shopItemDao().insert(item);
                    selectedShopImagePath = null;
                    currentShopDialogView = null;
                    Toast.makeText(activity, "商品已上架 🏪", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    public void showManageShopDialog() {
        soundHelper.playClickSound();
        java.util.List<ShopItem> allItems = db.shopItemDao().getAll();
        if (allItems.isEmpty()) {
            Toast.makeText(activity, "暂无商品，请先上架", Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean multi = shopMultiSelect;
        final float den = activity.getResources().getDisplayMetrics().density;
        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding((int)(8*den), (int)(8*den), (int)(8*den), (int)(8*den));
        scrollView.addView(container);
        // 标题行：标题 + 批量删除按钮
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, 0, 0, (int)(6*den));
        TextView tvTitle = new TextView(activity);
        tvTitle.setText("🏪 管理已有商品（" + allItems.size() + "件）" + (multi ? " — 勾选后批量删除" : ""));
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tvTitleP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(tvTitleP);
        titleRow.addView(tvTitle);
        Button btnBatch = makeCompactButton(multi ? "✅ 完成" : "🗑 批量删除");
        btnBatch.setOnClickListener(v -> {
            shopMultiSelect = !shopMultiSelect;
            if (!shopMultiSelect) shopSelectedIds.clear();
            showManageShopDialog();
        });
        titleRow.addView(btnBatch);
        container.addView(titleRow);
        // 搜索框
        android.widget.EditText etSearch = new android.widget.EditText(activity);
        etSearch.setHint("🔍 搜索商品名称...");
        etSearch.setTextSize(13);
        etSearch.setSingleLine(true);
        etSearch.setPadding((int)(8*den), 0, (int)(8*den), 0);
        LinearLayout.LayoutParams etP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etP.setMargins(0, 0, 0, (int)(6*den));
        etSearch.setLayoutParams(etP);
        container.addView(etSearch);
        // 列表构建（可搜索重建）
        buildShopRows(allItems, container, multi, tvTitle);
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = s.toString().trim();
                java.util.List<ShopItem> filtered = new java.util.ArrayList<>();
                if (q.isEmpty()) { filtered = allItems; }
                else { for (ShopItem it : allItems) { if (it.name != null && it.name.contains(q)) filtered.add(it); } }
                while (container.getChildCount() > 2) container.removeViewAt(container.getChildCount() - 1);
                buildShopRows(filtered, container, multi, tvTitle);
            }
            public void afterTextChanged(android.text.Editable s) {}
        });
        manageContainer = container;
        manageMulti = multi;
        manageTvTitle = tvTitle;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("🏪 商城管理")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getWindow().setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * 0.80f), android.view.WindowManager.LayoutParams.WRAP_CONTENT));
        manageDialog = dialog;
        dialog.setOnDismissListener(d -> { if (manageDialog == dialog) manageDialog = null; });
        dialog.show();
    }
    private void buildShopRows(java.util.List<ShopItem> items, LinearLayout container, boolean multi, TextView tvTitle) {
        final Button[] delSelRef = new Button[1];
        float den = activity.getResources().getDisplayMetrics().density;
        java.util.List<ShopItem> allItems = db.shopItemDao().getAll();
        for (ShopItem item : items) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int)(6*den), (int)(4*den), (int)(6*den), (int)(4*den));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, (int)(1*den), 0, (int)(1*den));
            row.setLayoutParams(rowParams);
            row.setBackgroundColor(item.active ? 0xFFF5F5F5 : 0xFFFFF0F0);
            // 缩略图（36dp）
            ImageView ivThumb = new ImageView(activity);
            int thumbPx = (int) (36 * den);
            LinearLayout.LayoutParams ivP = new LinearLayout.LayoutParams(thumbPx, thumbPx);
            ivP.setMargins(0, 0, (int)(8*den), 0);
            ivThumb.setLayoutParams(ivP);
            ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivThumb.setBackgroundColor(0xFFDDDDDD);
            if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
                try { Glide.with(activity).load(new java.io.File(item.iconUrl)).into(ivThumb); }
                catch (Exception ignored) {}
            }
            row.addView(ivThumb);
            // 多选勾选框
            CheckBox cb = new CheckBox(activity);
            cb.setVisibility(multi ? View.VISIBLE : View.GONE);
            cb.setChecked(shopSelectedIds.contains(item.id));
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) shopSelectedIds.add(item.id); else shopSelectedIds.remove(item.id);
                tvTitle.setText("🏪 管理已有商品（" + allItems.size() + "件）" + (multi ? " — 已选" + shopSelectedIds.size() + "件" : ""));
                if (delSelRef[0] != null) delSelRef[0].setText("🗑 删除选中（" + shopSelectedIds.size() + "）");
            });
            row.addView(cb);
            // 文本两行：名称 / 价格+状态
            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            // weight方案：dialog宽度在onShow时已确定(80%屏宽)，weight正常分配，永不溢出
            LinearLayout.LayoutParams textColP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            textCol.setLayoutParams(textColP);
            textCol.setMinimumWidth((int)(60 * den));
            TextView tvName = new TextView(activity);
            tvName.setText(item.name);
            tvName.setTextSize(14);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setMaxLines(1);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(tvName);
            TextView tvInfo = new TextView(activity);
            String status = item.active ? "" : "  [已下架]";
            tvInfo.setText("🪙 " + item.priceCoins + status);
            tvInfo.setTextSize(12);
            tvInfo.setTextColor(0xFF666666);
            textCol.addView(tvInfo);
            row.addView(textCol);
            if (multi) {
                row.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));
            } else {
                Button btnEdit = makeCompactButton("✏️");
                btnEdit.setOnClickListener(v -> showEditShopItemDialog(item));
                row.addView(btnEdit);
                Button btnDel = makeCompactButton("🗑");
                btnDel.setOnClickListener(v -> confirmDeleteShopItems(java.util.Collections.singletonList(item)));
                row.addView(btnDel);
                Button btnToggle = makeCompactButton(item.active ? "⬇" : "⬆");
                btnToggle.setOnClickListener(v -> {
                    item.active = !item.active;
                    item.updatedAt = System.currentTimeMillis();  // LWW时间戳（v3.0.62）
                    db.shopItemDao().update(item);
                    Toast.makeText(activity, (item.active ? "✅ 已上架: " : "⬇ 已下架: ") + item.name, Toast.LENGTH_SHORT).show();
                    // 原地刷新列表，不重开对话框（否则返回时要关两次）
                    if (manageDialog != null && manageDialog.isShowing() && manageContainer != null) {
                        java.util.List<ShopItem> refreshed = db.shopItemDao().getAll();
                        while (manageContainer.getChildCount() > 2) manageContainer.removeViewAt(manageContainer.getChildCount() - 1);
                        buildShopRows(refreshed, manageContainer, manageMulti, manageTvTitle);
                    } else {
                        showManageShopDialog();
                    }
                });
                row.addView(btnToggle);
            }
            container.addView(row);
        }
        if (multi) {
            Button btnDelSel = new Button(activity);
            btnDelSel.setText("🗑 删除选中（" + shopSelectedIds.size() + "）");
            btnDelSel.setTextSize(14);
            btnDelSel.setPadding(0, (int)(12*den), 0, (int)(12*den));
            delSelRef[0] = btnDelSel;
            btnDelSel.setOnClickListener(v -> {
                if (shopSelectedIds.isEmpty()) { Toast.makeText(activity, "请先勾选要删除的商品", Toast.LENGTH_SHORT).show(); return; }
                java.util.List<ShopItem> toDelete = new java.util.ArrayList<>();
                for (ShopItem it : allItems) if (shopSelectedIds.contains(it.id)) toDelete.add(it);
                confirmDeleteShopItems(toDelete);
            });
            container.addView(btnDelSel);
        }
    }
    public void showEditShopItemDialog(ShopItem item) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_shop_item, null);
        android.widget.EditText etName = view.findViewById(R.id.et_item_name);
        android.widget.EditText etDesc = view.findViewById(R.id.et_item_desc);
        android.widget.EditText etPrice = view.findViewById(R.id.et_item_price);
        android.widget.EditText etCategory = view.findViewById(R.id.et_item_category);
        Button btnPickImage = view.findViewById(R.id.btn_pick_image);
        ImageView ivPreview = view.findViewById(R.id.iv_image_preview);
        TextView tvImageName = view.findViewById(R.id.tv_image_name);

        etName.setText(item.name);
        etDesc.setText(item.description);
        etPrice.setText(String.valueOf(item.priceCoins));
        etCategory.setText(item.category);
        android.widget.RadioGroup rgType = view.findViewById(R.id.rg_item_type);
        if ("limited".equals(item.itemType)) rgType.check(R.id.rb_limited); else rgType.check(R.id.rb_normal);
        android.widget.EditText etStock = view.findViewById(R.id.et_item_stock);
        etStock.setText(String.valueOf(item.stock));
        selectedShopImagePath = item.iconUrl;

        if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
            ivPreview.setVisibility(View.VISIBLE);
            tvImageName.setVisibility(View.VISIBLE);
            tvImageName.setText("当前图片: " + item.iconUrl.substring(Math.max(0, item.iconUrl.length() - 30)));
            Glide.with(activity).load(new java.io.File(item.iconUrl)).into(ivPreview);
        }

        btnPickImage.setOnClickListener(v -> {
            currentShopDialogView = view;
            activity.launchShopImagePicker();
        });

        new AlertDialog.Builder(activity)
                .setTitle("✏️ 编辑商品")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    item.name = etName.getText().toString();
                    item.description = etDesc.getText().toString();
                    try { item.priceCoins = Integer.parseInt(etPrice.getText().toString()); }
                    catch (Exception e) { item.priceCoins = 50; }
                    item.category = etCategory.getText().toString();
                    android.widget.RadioGroup rgType2 = view.findViewById(R.id.rg_item_type);
                    item.itemType = (rgType2.getCheckedRadioButtonId() == R.id.rb_limited) ? "limited" : "normal";
                    try { item.stock = Integer.parseInt(((android.widget.EditText) view.findViewById(R.id.et_item_stock)).getText().toString()); }
                    catch (Exception e) { item.stock = -1; }
                    if (selectedShopImagePath != null) item.iconUrl = selectedShopImagePath;
                    item.updatedAt = System.currentTimeMillis();  // LWW时间戳（v3.0.62）
                    db.shopItemDao().update(item);
                    currentShopDialogView = null;
                    selectedShopImagePath = null;
                    Toast.makeText(activity, "✅ 商品已更新", Toast.LENGTH_SHORT).show();
                    if (manageDialog != null && manageDialog.isShowing() && manageContainer != null) {
                        java.util.List<ShopItem> refreshed = db.shopItemDao().getAll();
                        while (manageContainer.getChildCount() > 2) manageContainer.removeViewAt(manageContainer.getChildCount() - 1);
                        buildShopRows(refreshed, manageContainer, manageMulti, manageTvTitle);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private Button makeCompactButton(String text) {
        Button b = new Button(activity);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        float den = activity.getResources().getDisplayMetrics().density;
        int p = (int) (4 * den);
        b.setPadding(p, 2, p, 2);
        b.setLayoutParams(new LinearLayout.LayoutParams((int)(30 * den), (int)(30 * den)));
        return b;
    }
    private void confirmDeleteShopItems(java.util.List<ShopItem> items) {
        if (items.isEmpty()) return;
        String names = "";
        for (int i = 0; i < items.size() && i < 3; i++) names += "\n· " + items.get(i).name;
        if (items.size() > 3) names += "\n· 等" + items.size() + "件商品";
        new AlertDialog.Builder(activity)
                .setTitle("🗑 删除商品")
                .setMessage("确定删除 " + items.size() + " 件商品？" + names + "\n\n此操作不可恢复！")
                .setPositiveButton("删除", (d, w) -> {
                    int ok = 0;
                    for (ShopItem it : items) {
                        try {
                            if (it.iconUrl != null && it.iconUrl.contains("shop_images")) {
                                java.io.File f = new java.io.File(it.iconUrl);
                                if (f.exists()) f.delete();
                            }
                            db.shopItemDao().delete(it);
                            ok++;
                        } catch (Exception ignored) {}
                    }
                    shopSelectedIds.clear();
                    shopMultiSelect = false;
                    Toast.makeText(activity, "🗑 已删除 " + ok + " 件商品", Toast.LENGTH_SHORT).show();
                    // 原地刷新列表，不重开对话框（否则返回时要关两次）
                    if (manageDialog != null && manageDialog.isShowing() && manageContainer != null) {
                        java.util.List<ShopItem> refreshed = db.shopItemDao().getAll();
                        while (manageContainer.getChildCount() > 2) manageContainer.removeViewAt(manageContainer.getChildCount() - 1);
                        buildShopRows(refreshed, manageContainer, false, manageTvTitle);
                    } else {
                        showManageShopDialog();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
