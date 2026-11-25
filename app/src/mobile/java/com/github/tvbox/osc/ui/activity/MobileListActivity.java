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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手机通用列表页：通过 "mode" 区分 搜索 / 历史 / 收藏。
 * 复用 TV 的 SourceViewModel.getSearch（含爬虫）+ RoomDataManger。
 */
public class MobileListActivity extends MobileBaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SEARCH = "search";
    public static final String MODE_HISTORY = "history";
    public static final String MODE_COLLECT = "collect";

    private String mode = MODE_SEARCH;
    private SourceViewModel viewModel;
    private EditText etQuery;
    private TextView tvStatus;
    private RecyclerView recyclerView;
    private VodAdapter adapter;

    /** 与 TV SearchActivity 一致：每个站点一个线程，避免在主线程执行爬虫网络请求。 */
    private ExecutorService searchExecutor;
    private String currentToken = "";
    private final AtomicInteger pendingSearches = new AtomicInteger(0);
    private final Set<String> seenKeys = new HashSet<String>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getIntent().getExtras();
        if (b != null && !TextUtils.isEmpty(b.getString(EXTRA_MODE))) {
            mode = b.getString(EXTRA_MODE);
        }
        viewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        EventBus.getDefault().register(this);
        buildUI();
        observeData();

        if (MODE_SEARCH.equals(mode)) {
            String last = lastSearchWord();
            if (!TextUtils.isEmpty(last)) etQuery.setText(last);
            tvStatus.setText("输入片名搜索，多个站点同时查找");
        } else if (MODE_HISTORY.equals(mode)) {
            loadHistory();
        } else if (MODE_COLLECT.equals(mode)) {
            loadCollect();
        }
    }

    private void buildUI() {
        LinearLayout root = root();
        String title = MODE_SEARCH.equals(mode) ? "搜索"
                : MODE_HISTORY.equals(mode) ? "观看历史" : "我的收藏";
        root.addView(titleBar(title));

        if (MODE_SEARCH.equals(mode)) {
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(pad, dp(6), pad, dp(6));
            etQuery = new EditText(this);
            etQuery.setHint("输入影片名");
            etQuery.setSingleLine(true);
            etQuery.setTextColor(Color.parseColor(C_TEXT));
            etQuery.setHintTextColor(Color.parseColor(C_TEXT_2));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(C_CARD));
            bg.setCornerRadius(dp(6));
            etQuery.setBackground(bg);
            etQuery.setPadding(dp(10), dp(9), dp(10), dp(9));
            bar.addView(etQuery, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView btn = text("搜索", 15, Color.parseColor("#FFFFFF"), true);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(16), dp(10), dp(16), dp(10));
            btn.setBackground(roundBg(C_ACCENT, dp(6)));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { doSearch(); }
            });
            bar.addView(btn);
            root.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            etQuery = new EditText(this); // 占位，搜索模式外不可见
            etQuery.setVisibility(View.GONE);
            // 历史 / 收藏 两个 Tab（TV 端为两个独立页面，手机端合并入口）
            LinearLayout tabs = new LinearLayout(this);
            tabs.setOrientation(LinearLayout.HORIZONTAL);
            tabs.setPadding(pad, dp(6), pad, dp(2));
            TextView tabHistory = text("观看历史", 15,
                    Color.parseColor(MODE_HISTORY.equals(mode) ? C_ACCENT : C_TEXT_2),
                    MODE_HISTORY.equals(mode));
            tabHistory.setGravity(Gravity.CENTER);
            tabHistory.setPadding(0, dp(8), 0, dp(8));
            tabHistory.setBackground(roundBg(MODE_HISTORY.equals(mode) ? C_CARD_2 : C_CARD, dp(6)));
            tabHistory.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!MODE_HISTORY.equals(mode)) openMode(MODE_HISTORY);
                }
            });
            TextView tabCollect = text("我的收藏", 15,
                    Color.parseColor(MODE_COLLECT.equals(mode) ? C_ACCENT : C_TEXT_2),
                    MODE_COLLECT.equals(mode));
            tabCollect.setGravity(Gravity.CENTER);
            tabCollect.setPadding(0, dp(8), 0, dp(8));
            tabCollect.setBackground(roundBg(MODE_COLLECT.equals(mode) ? C_CARD_2 : C_CARD, dp(6)));
            tabCollect.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!MODE_COLLECT.equals(mode)) openMode(MODE_COLLECT);
                }
            });
            tabs.addView(tabHistory, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams cpl = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cpl.leftMargin = dp(8);
            tabs.addView(tabCollect, cpl);
            root.addView(tabs, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        tvStatus = text("", 12, C_TEXT_2, false);
        tvStatus.setPadding(pad, dp(2), pad, dp(6));
        root.addView(tvStatus);

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new VodAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav(mode.equals(MODE_HISTORY) || mode.equals(MODE_COLLECT) ? 2 : -1));
        setContentView(root);
    }

    private void openMode(String newMode) {
        Intent intent = new Intent(this, MobileListActivity.class);
        intent.putExtra(EXTRA_MODE, newMode);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override protected void onDestroy() {
        try { EventBus.getDefault().unregister(this); } catch (Throwable ignored) {}
        try { com.lzy.okgo.OkGo.getInstance().cancelTag("search"); } catch (Throwable ignored) {}
        if (searchExecutor != null) {
            try { searchExecutor.shutdownNow(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    /**
     * 占位：保留与 TV 一致的初始化顺序。
     * 真正的搜索结果不通过 searchResult LiveData 分发（TV 的 SourceViewModel 对该 LiveData
     * 从不 postValue），而是统一 EventBus.post(RefreshEvent.TYPE_SEARCH_RESULT)。
     */
    private void observeData() {}

    /** 与 TV SearchActivity.refresh(RefreshEvent) 完全相同的接收方式。 */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSearchEvent(RefreshEvent event) {
        if (event == null || event.type != RefreshEvent.TYPE_SEARCH_RESULT) return;
        if (!MODE_SEARCH.equals(mode)) return;
        AbsXml xml = null;
        if (event.obj instanceof AbsXml) xml = (AbsXml) event.obj;
        // 过滤上一轮搜索迟到的结果
        if (xml != null && !TextUtils.isEmpty(xml.searchToken)
                && !xml.searchToken.equals(currentToken)) return;
        int left = pendingSearches.get() > 0 ? pendingSearches.decrementAndGet() : 0;
        if (xml != null && xml.movie != null && xml.movie.videoList != null) {
            List<Movie.Video> fresh = new ArrayList<>();
            for (Movie.Video v : xml.movie.videoList) {
                if (v == null || TextUtils.isEmpty(v.id)) continue;
                String key = (TextUtils.isEmpty(v.sourceKey) ? "" : v.sourceKey) + "|" + v.id;
                if (seenKeys.add(key)) fresh.add(v);
            }
            if (!fresh.isEmpty()) {
                adapter.addData(fresh);
                tvStatus.setText("找到 " + adapter.getItemCount() + " 部");
                return;
            }
        }
        if (adapter.getItemCount() > 0) {
            tvStatus.setText("找到 " + adapter.getItemCount() + " 部");
        } else if (left <= 0) {
            tvStatus.setText("未找到相关影片，可换个关键词或切换站点");
        }
    }

    private void doSearch() {
        final String wd = etQuery.getText().toString().trim();
        if (TextUtils.isEmpty(wd)) { toast("请输入片名"); return; }
        saveSearchWord(wd);
        adapter.clear();
        seenKeys.clear();
        tvStatus.setText("正在加载数据源…");
        try { com.lzy.okgo.OkGo.getInstance().cancelTag("search"); } catch (Throwable ignored) {}
        if (searchExecutor != null) {
            try { searchExecutor.shutdownNow(); } catch (Throwable ignored) {}
        }
        searchExecutor = Executors.newFixedThreadPool(6);

        // 搜索前先确保爬虫已加载（与 TV 一致）
        MobileDataInitializer.ensureReady(false, this, new MobileDataInitializer.Callback() {
            @Override public void onReady() {
                List<SourceBean> sources = ApiConfig.get().getSearchSourceBeanList();
                if (sources == null || sources.isEmpty()) {
                    tvStatus.setText("没有可用于搜索的站点");
                    return;
                }
                currentToken = wd + "_" + System.currentTimeMillis();
                seenKeys.clear();
                pendingSearches.set(sources.size());
                tvStatus.setText("正在搜索「" + wd + "」…");
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
                                        if (left <= 0 && adapter.getItemCount() == 0) {
                                            tvStatus.setText("搜索失败：" + (t.getMessage() == null
                                                    ? "数据源异常" : t.getMessage()));
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
                // 兜底：站点无响应时给出明确提示，避免一直停在「正在搜索」
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (adapter.getItemCount() == 0
                                && !TextUtils.isEmpty(etQuery.getText().toString().trim())) {
                            tvStatus.setText("未找到相关影片或站点超时，可切换站点后重试");
                        }
                    }
                }, 15000);
            }
            @Override public void onError(String msg) { tvStatus.setText("加载失败：" + msg); }
            @Override public void onNotice(String msg) { tvStatus.setText("提示：" + msg); }
        });
    }

    private void loadHistory() {
        List<VodInfo> records = RoomDataManger.getAllVodRecord(100);
        List<ListItem> items = new ArrayList<>();
        for (VodInfo v : records) {
            items.add(new ListItem(v.id, v.sourceKey, v.name, v.pic,
                    TextUtils.isEmpty(v.playNote) ? "" : "上次看到 " + v.playNote));
        }
        adapter.setItems(items);
        tvStatus.setText(items.isEmpty() ? "暂无观看历史" : ("共 " + items.size() + " 条"));
    }

    private void loadCollect() {
        List<VodCollect> collects = RoomDataManger.getAllVodCollect();
        List<ListItem> items = new ArrayList<>();
        for (VodCollect v : collects) {
            items.add(new ListItem(v.vodId, v.sourceKey, v.name, v.pic, ""));
        }
        adapter.setItems(items);
        tvStatus.setText(items.isEmpty() ? "暂无收藏" : ("共 " + items.size() + " 部"));
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
            if (h.size() > 20) h = new ArrayList<>(h.subList(0, 20));
            Hawk.put(HawkConfig.SEARCH_HISTORY, h);
        } catch (Throwable t) {}
    }

    // ===== 统一列表项 =====
    private static class ListItem {
        String id, sourceKey, name, pic, note;
        ListItem(String id, String sourceKey, String name, String pic, String note) {
            this.id = id; this.sourceKey = sourceKey; this.name = name; this.pic = pic; this.note = note;
        }
    }

    private class VodAdapter extends RecyclerView.Adapter<VodAdapter.VH> {
        private List<ListItem> data = new ArrayList<>();

        void setItems(List<ListItem> list) {
            this.data = list == null ? new ArrayList<ListItem>() : list;
            notifyDataSetChanged();
        }
        void clear() { this.data.clear(); notifyDataSetChanged(); }
        void addData(List<Movie.Video> videos) {
            for (Movie.Video v : videos) {
                String key = TextUtils.isEmpty(v.sourceKey)
                        ? (ApiConfig.get().getHomeSourceBean() == null ? ""
                           : ApiConfig.get().getHomeSourceBean().getKey()) : v.sourceKey;
                data.add(new ListItem(v.id, key, v.name, v.pic, v.note));
            }
            notifyDataSetChanged();
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(C_CARD, dp(8)));
            card.setPadding(dp(5), dp(5), dp(5), dp(8));
            ImageView cover = new ImageView(parent.getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(Color.parseColor("#22344A"));
            cover.setBackground(ph);
            card.addView(cover, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));
            TextView name = text2(parent.getContext(), "", 12, C_TEXT, false);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nl.topMargin = dp(5);
            card.addView(name, nl);
            TextView note = text2(parent.getContext(), "", 10, C_TEXT_2, false);
            note.setSingleLine(true);
            card.addView(note);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            int m = dp(4);
            cl.setMargins(m, m, m, m);
            card.setLayoutParams(cl);
            return new VH(card, cover, name, note);
        }
        @Override public void onBindViewHolder(VH holder, int position) {
            final ListItem item = data.get(position);
            holder.name.setText(TextUtils.isEmpty(item.name) ? "(无片名)" : item.name);
            holder.note.setText(TextUtils.isEmpty(item.note) ? "" : item.note);
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
        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView cover; TextView name; TextView note;
            VH(View v, ImageView cover, TextView name, TextView note) {
                super(v); this.cover = cover; this.name = name; this.note = note;
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
