package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手机通用列表页：通过 "mode" 区分 搜索 / 历史 / 收藏。
 * UI 参考目标：顶部搜索栏 + 左侧站点标签栏 + 右侧两列海报卡片网格。
 * 搜索结果链路完全沿用 TV：EventBus TYPE_SEARCH_RESULT + 线程池并发 + token 过滤。
 */
public class MobileListActivity extends MobileBaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_QUERY = "query";
    public static final String MODE_SEARCH = "search";
    public static final String MODE_HISTORY = "history";
    public static final String MODE_COLLECT = "collect";

    private String mode = MODE_SEARCH;
    private SourceViewModel viewModel;
    private EditText etQuery;
    private TextView tvStatus, tvFuzzy;
    private RecyclerView recyclerView;
    private VodAdapter adapter;
    private LinearLayout sourceBar;
    private LinearLayout sourceList;
    private TextView tvFilter;

    /** 站点 key -> 显示名（用于卡片角标与左侧标签）。 */
    private final Map<String, String> sourceNames = new HashMap<String, String>();
    private String filterSourceKey = null;     // null = 全部站点
    private boolean fuzzy = true;              // 模糊匹配开关
    private int spanCount = 2;                 // 结果网格列数（2/3 切换）

    /** 与 TV SearchActivity 一致：每个站点一个线程，避免在主线程执行爬虫网络请求。 */
    private ExecutorService searchExecutor;
    private String currentToken = "";
    private final AtomicInteger pendingSearches = new AtomicInteger(0);
    private final Set<String> seenKeys = new HashSet<String>();
    private int rawResultCount = 0;            // 各站点返回总数（去重前），用于 "搜索(x/y)"

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getIntent().getExtras();
        if (b != null && !TextUtils.isEmpty(b.getString(EXTRA_MODE))) {
            mode = b.getString(EXTRA_MODE);
        }
        viewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        EventBus.getDefault().register(this);
        buildUI();

        if (MODE_SEARCH.equals(mode)) {
            String query = b == null ? "" : b.getString(EXTRA_QUERY, "");
            if (TextUtils.isEmpty(query)) query = lastSearchWord();
            if (!TextUtils.isEmpty(query)) {
                etQuery.setText(query);
                etQuery.setSelection(query.length());
                doSearch();   // 从详情页「快搜」进入时自动搜索
            } else {
                updateStatus("输入片名，多个站点同时查找");
            }
            buildSourceTabs();
        } else if (MODE_HISTORY.equals(mode)) {
            loadHistory();
        } else if (MODE_COLLECT.equals(mode)) {
            loadCollect();
        }
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------
    private void buildUI() {
        LinearLayout root = root();
        root.addView(buildSearchHeader());

        tvStatus = text("", 12, C_TEXT_2, false);
        tvStatus.setPadding(pad, dp(2), pad, dp(6));
        root.addView(tvStatus);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);

        // 左侧：站点标签栏（搜索模式显示）
        sourceBar = new LinearLayout(this);
        sourceBar.setOrientation(LinearLayout.VERTICAL);
        sourceBar.setBackgroundColor(Color.parseColor("#0C1625"));
        sourceBar.setVisibility(MODE_SEARCH.equals(mode) ? View.VISIBLE : View.GONE);
        ScrollView sourceScroll = new ScrollView(this);
        sourceList = new LinearLayout(this);
        sourceList.setOrientation(LinearLayout.VERTICAL);
        sourceList.setPadding(dp(8), dp(4), dp(8), dp(8));
        sourceScroll.addView(sourceList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sourceBar.addView(sourceScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        body.addView(sourceBar, new LinearLayout.LayoutParams(dp(112),
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 右侧：结果网格
        recyclerView = new RecyclerView(this);
        adapter = new VodAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));
        recyclerView.setAdapter(adapter);
        recyclerView.setPadding(dp(6), dp(2), dp(8), dp(10));
        recyclerView.setClipToPadding(false);
        body.addView(recyclerView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bottomNav(MODE_HISTORY.equals(mode) || MODE_COLLECT.equals(mode) ? 2 : -1));
        setContentView(root);
    }

    /** 顶部：返回 + 圆角输入框 + 模糊 / 布局 / 筛选 / 搜索。 */
    private LinearLayout buildSearchHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(10), dp(10), dp(8));

        TextView back = text("‹", 26, C_TEXT, false);
        back.setPadding(0, 0, dp(6), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back);

        etQuery = new EditText(this);
        etQuery.setHint("输入片名搜索");
        etQuery.setSingleLine(true);
        etQuery.setTextColor(Color.parseColor(C_TEXT));
        etQuery.setHintTextColor(Color.parseColor(C_TEXT_2));
        etQuery.setTextSize(14);
        etQuery.setBackground(roundBg("#1B2C42", dp(20)));
        etQuery.setPadding(dp(14), dp(8), dp(12), dp(8));
        etQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                doSearch();
                return true;
            }
        });
        bar.addView(etQuery, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (MODE_SEARCH.equals(mode)) {
            tvFuzzy = text("模糊", 12, Color.parseColor(C_ACCENT), true);
            tvFuzzy.setPadding(dp(8), dp(4), dp(6), dp(4));
            tvFuzzy.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    fuzzy = !fuzzy;
                    tvFuzzy.setTextColor(Color.parseColor(fuzzy ? C_ACCENT : C_TEXT_2));
                    toast(fuzzy ? "已开启模糊匹配" : "已关闭模糊匹配（精确匹配）");
                    adapter.applyFilter();
                }
            });
            bar.addView(tvFuzzy);

            final TextView layoutBtn = text("▦", 20, Color.parseColor(C_TEXT_2), false);
            layoutBtn.setPadding(dp(6), 0, dp(6), 0);
            layoutBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    spanCount = spanCount == 2 ? 3 : 2;
                    recyclerView.setLayoutManager(new GridLayoutManager(
                            MobileListActivity.this, spanCount));
                    recyclerView.setAdapter(adapter);
                    layoutBtn.setText(spanCount == 2 ? "▦" : "▤");
                }
            });
            bar.addView(layoutBtn);

            tvFilter = text("☰", 19, Color.parseColor(C_TEXT_2), false);
            tvFilter.setPadding(dp(6), 0, dp(6), 0);
            tvFilter.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    sourceBar.setVisibility(sourceBar.getVisibility() == View.VISIBLE
                            ? View.GONE : View.VISIBLE);
                }
            });
            bar.addView(tvFilter);
        }

        TextView searchBtn = text("⌕", 26, Color.parseColor(C_ACCENT), true);
        searchBtn.setPadding(dp(8), 0, 0, 0);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSearch(); }
        });
        bar.addView(searchBtn);
        return bar;
    }

    /** 左侧站点标签：全部 + 参与搜索的各站点，点击筛选结果。 */
    private void buildSourceTabs() {
        sourceList.removeAllViews();
        List<SourceBean> sources = null;
        try { sources = ApiConfig.get().getSearchSourceBeanList(); } catch (Throwable ignored) {}
        sourceNames.clear();
        if (sources != null) {
            for (SourceBean s : sources) {
                if (s == null || TextUtils.isEmpty(s.getKey())) continue;
                sourceNames.put(s.getKey(), TextUtils.isEmpty(s.getName()) ? s.getKey() : s.getName());
            }
        }
        sourceList.addView(makeSourceTab("全部显示", null, filterSourceKey == null));
        if (sources != null) {
            for (SourceBean s : sources) {
                if (s == null || TextUtils.isEmpty(s.getKey())) continue;
                String name = TextUtils.isEmpty(s.getName()) ? s.getKey() : s.getName();
                sourceList.addView(makeSourceTab(name, s.getKey(),
                        s.getKey().equals(filterSourceKey)));
            }
        }
        adapter.applyFilter();
    }

    private TextView makeSourceTab(final String label, final String key, boolean selected) {
        TextView t = text(label, 12,
                Color.parseColor(selected ? "#FFFFFF" : C_TEXT_2), selected);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(9), dp(9), dp(9), dp(9));
        t.setBackground(roundBg(selected ? C_ACCENT : C_CARD, dp(7)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                filterSourceKey = key;
                buildSourceTabs();
            }
        });
        t.setLayoutParams(lp);
        return t;
    }

    private void updateStatus(String s) { if (tvStatus != null) tvStatus.setText(s); }

    // ------------------------------------------------------------------
    // 搜索事件（与 TV SearchActivity 完全一致）
    // ------------------------------------------------------------------
    @Override protected void onDestroy() {
        try { EventBus.getDefault().unregister(this); } catch (Throwable ignored) {}
        try { com.lzy.okgo.OkGo.getInstance().cancelTag("search"); } catch (Throwable ignored) {}
        if (searchExecutor != null) {
            try { searchExecutor.shutdownNow(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSearchEvent(RefreshEvent event) {
        if (event == null || event.type != RefreshEvent.TYPE_SEARCH_RESULT) return;
        if (!MODE_SEARCH.equals(mode)) return;
        AbsXml xml = null;
        if (event.obj instanceof AbsXml) xml = (AbsXml) event.obj;
        if (xml != null && !TextUtils.isEmpty(xml.searchToken)
                && !xml.searchToken.equals(currentToken)) return;
        int left = pendingSearches.get() > 0 ? pendingSearches.decrementAndGet() : 0;
        if (xml != null && xml.movie != null && xml.movie.videoList != null) {
            rawResultCount += xml.movie.videoList.size();
            List<Movie.Video> fresh = new ArrayList<Movie.Video>();
            for (Movie.Video v : xml.movie.videoList) {
                if (v == null || TextUtils.isEmpty(v.id)) continue;
                String key = (TextUtils.isEmpty(v.sourceKey) ? "" : v.sourceKey) + "|" + v.id;
                if (seenKeys.add(key)) fresh.add(v);
            }
            if (!fresh.isEmpty()) {
                adapter.addData(fresh);
                updateStatus("搜索(" + adapter.getShownCount() + "/" + rawResultCount + ")");
                return;
            }
        }
        if (adapter.getAllCount() > 0) {
            updateStatus("搜索(" + adapter.getShownCount() + "/" + rawResultCount + ")");
        } else if (left <= 0) {
            updateStatus("未找到相关影片，可换个关键词或切换站点");
        }
    }

    private void doSearch() {
        final String wd = etQuery.getText().toString().trim();
        if (TextUtils.isEmpty(wd)) { toast("请输入片名"); return; }
        saveSearchWord(wd);
        adapter.clear();
        seenKeys.clear();
        rawResultCount = 0;
        filterSourceKey = null;
        updateStatus("正在加载数据源…");
        try { com.lzy.okgo.OkGo.getInstance().cancelTag("search"); } catch (Throwable ignored) {}
        if (searchExecutor != null) {
            try { searchExecutor.shutdownNow(); } catch (Throwable ignored) {}
        }
        searchExecutor = Executors.newFixedThreadPool(6);

        MobileDataInitializer.ensureReady(false, this, new MobileDataInitializer.Callback() {
            @Override public void onReady() {
                List<SourceBean> sources = ApiConfig.get().getSearchSourceBeanList();
                if (sources == null || sources.isEmpty()) {
                    updateStatus("没有可用于搜索的站点");
                    return;
                }
                currentToken = wd + "_" + System.currentTimeMillis();
                seenKeys.clear();
                pendingSearches.set(sources.size());
                buildSourceTabs();
                updateStatus("正在搜索「" + wd + "」…");
                for (final SourceBean bean : sources) {
                    searchExecutor.execute(new Runnable() {
                        @Override public void run() {
                            try {
                                viewModel.getSearch(bean.getKey(), wd, currentToken);
                            } catch (final Throwable t) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        int left = pendingSearches.get() > 0
                                                ? pendingSearches.decrementAndGet() : 0;
                                        if (left <= 0 && adapter.getAllCount() == 0) {
                                            updateStatus("搜索失败：" + (t.getMessage() == null
                                                    ? "数据源异常" : t.getMessage()));
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (adapter.getAllCount() == 0
                                && !TextUtils.isEmpty(etQuery.getText().toString().trim())) {
                            updateStatus("未找到相关影片或站点超时，可切换站点后重试");
                        }
                    }
                }, 15000);
            }
            @Override public void onError(String msg) { updateStatus("加载失败：" + msg); }
            @Override public void onNotice(String msg) { updateStatus("提示：" + msg); }
        });
    }

    private void loadHistory() {
        List<VodInfo> records = RoomDataManger.getAllVodRecord(100);
        List<ListItem> items = new ArrayList<ListItem>();
        for (VodInfo v : records) {
            items.add(new ListItem(v.id, v.sourceKey, v.name, v.pic,
                    TextUtils.isEmpty(v.playNote) ? "" : "上次看到 " + v.playNote,
                    sourceNames.get(v.sourceKey)));
        }
        adapter.setItems(items);
        updateStatus(items.isEmpty() ? "暂无观看历史" : ("共 " + items.size() + " 条"));
    }

    private void loadCollect() {
        List<VodCollect> collects = RoomDataManger.getAllVodCollect();
        List<ListItem> items = new ArrayList<ListItem>();
        for (VodCollect v : collects) {
            items.add(new ListItem(v.vodId, v.sourceKey, v.name, v.pic, "",
                    sourceNames.get(v.sourceKey)));
        }
        adapter.setItems(items);
        updateStatus(items.isEmpty() ? "暂无收藏" : ("共 " + items.size() + " 部"));
    }

    private String lastSearchWord() {
        try {
            ArrayList<String> h = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
            return h.isEmpty() ? "" : h.get(0);
        } catch (Throwable t) { return ""; }
    }

    private void saveSearchWord(String wd) {
        try {
            ArrayList<String> h = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
            h.remove(wd);
            h.add(0, wd);
            if (h.size() > 20) h = new ArrayList<String>(h.subList(0, 20));
            Hawk.put(HawkConfig.SEARCH_HISTORY, h);
        } catch (Throwable t) {}
    }

    // ------------------------------------------------------------------
    // 数据 + 卡片
    // ------------------------------------------------------------------
    private static class ListItem {
        String id, sourceKey, name, pic, note, sourceName;
        ListItem(String id, String sourceKey, String name, String pic,
                 String note, String sourceName) {
            this.id = id; this.sourceKey = sourceKey; this.name = name;
            this.pic = pic; this.note = note; this.sourceName = sourceName;
        }
    }

    private class VodAdapter extends RecyclerView.Adapter<VodAdapter.VH> {
        private final List<ListItem> all = new ArrayList<ListItem>();
        private final List<ListItem> shown = new ArrayList<ListItem>();

        void setItems(List<ListItem> list) {
            all.clear();
            if (list != null) all.addAll(list);
            applyFilter();
        }
        void clear() { all.clear(); shown.clear(); notifyDataSetChanged(); }
        void addData(List<Movie.Video> videos) {
            for (Movie.Video v : videos) {
                String key = TextUtils.isEmpty(v.sourceKey)
                        ? (ApiConfig.get().getHomeSourceBean() == null ? ""
                           : ApiConfig.get().getHomeSourceBean().getKey()) : v.sourceKey;
                all.add(new ListItem(v.id, key, v.name, v.pic, v.note, sourceNames.get(key)));
            }
            applyFilter();
        }
        int getAllCount() { return all.size(); }
        int getShownCount() { return shown.size(); }

        /** 按站点标签 + 模糊/精确开关重新过滤显示列表。 */
        void applyFilter() {
            shown.clear();
            String wd = MODE_SEARCH.equals(mode) && etQuery != null
                    ? etQuery.getText().toString().trim() : "";
            for (ListItem it : all) {
                if (filterSourceKey != null && !filterSourceKey.equals(it.sourceKey)) continue;
                if (!fuzzy && !TextUtils.isEmpty(wd) && it.name != null
                        && !it.name.contains(wd)) continue;
                shown.add(it);
            }
            notifyDataSetChanged();
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(C_CARD, dp(8)));
            card.setPadding(dp(4), dp(4), dp(4), dp(7));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = dp(4);
            cp.setMargins(m, m, m, m);
            card.setLayoutParams(cp);

            FrameLayout coverBox = new FrameLayout(parent.getContext());
            ImageView cover = new ImageView(parent.getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(Color.parseColor("#22344A"));
            cover.setBackground(ph);
            coverBox.addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

            TextView badge = new TextView(parent.getContext());
            badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
            badge.setTextColor(Color.parseColor("#FFFFFF"));
            badge.setBackgroundColor(Color.parseColor("#99000000"));
            badge.setPadding(dp(6), dp(2), dp(6), dp(3));
            badge.setSingleLine(true);
            badge.setEllipsize(android.text.TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.gravity = Gravity.TOP | Gravity.LEFT;
            bl.leftMargin = dp(3);
            bl.topMargin = dp(3);
            coverBox.addView(badge, bl);
            card.addView(coverBox, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

            TextView name = text2(parent.getContext(), "", 12, C_TEXT, false);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = dp(5);
            card.addView(name, nl);
            TextView note = text2(parent.getContext(), "", 10, C_TEXT_2, false);
            note.setSingleLine(true);
            card.addView(note);
            return new VH(card, cover, badge, name, note);
        }

        @Override public void onBindViewHolder(VH holder, int position) {
            final ListItem item = shown.get(position);
            holder.name.setText(TextUtils.isEmpty(item.name) ? "(无片名)" : item.name);
            holder.note.setText(TextUtils.isEmpty(item.note) ? "" : item.note);
            if (!TextUtils.isEmpty(item.sourceName)) {
                holder.badge.setText(item.sourceName);
                holder.badge.setVisibility(View.VISIBLE);
            } else {
                holder.badge.setVisibility(View.GONE);
            }
            if (!TextUtils.isEmpty(item.pic)) ImgUtil.load(item.pic, holder.cover, 4);
            else holder.cover.setImageDrawable(null);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Intent intent = new Intent(view.getContext(), MobileDetailActivity.class);
                    intent.putExtra(MobilePlayActivity.EXTRA_ID, item.id == null ? "" : item.id);
                    intent.putExtra(MobilePlayActivity.EXTRA_SOURCE_KEY,
                            item.sourceKey == null ? "" : item.sourceKey);
                    intent.putExtra(MobilePlayActivity.EXTRA_NAME, item.name == null ? "" : item.name);
                    intent.putExtra("picture", item.pic == null ? "" : item.pic);
                    view.getContext().startActivity(intent);
                }
            });
        }
        @Override public int getItemCount() { return shown.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView cover; TextView badge, name, note;
            VH(View v, ImageView cover, TextView badge, TextView name, TextView note) {
                super(v); this.cover = cover; this.badge = badge;
                this.name = name; this.note = note;
            }
        }
    }

    private TextView text2(Context c, String s, int sizeSp, String color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return tv;
    }
}
