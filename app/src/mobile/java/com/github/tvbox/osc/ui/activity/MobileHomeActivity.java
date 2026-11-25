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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机竖屏首页。数据链路与 TV HomeActivity 完全一致：
 *   loadConfig -> loadJar -> getSort(分类+推荐) -> getList(分类榜单分页)
 * UI 为深色竖屏触摸样式（参考目标图），URL 配置已移到设置页。
 */
public class MobileHomeActivity extends MobileBaseActivity {

    private TextView tvSourceName;
    private TextView tvStatus;
    private LinearLayout tabContainer;
    private RecyclerView recyclerView;
    private VodAdapter adapter;
    private SourceViewModel viewModel;

    private List<MovieSort.SortData> sortTabs = new ArrayList<>();
    private AbsSortXml homeSort;
    private int currentTab = -1;
    private int page = 1;
    private int maxPage = 1;
    private boolean loadingMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        buildUI();
        observeData();
        // 已配置过订阅地址则直接加载；否则引导去设置页
        String savedApi = Hawk.get(HawkConfig.API_URL, "");
        if (!TextUtils.isEmpty(savedApi)) {
            loadConfigAndData(false);
        } else {
            tvStatus.setText("请到「我的 → 设置 → 配置地址」填写订阅地址");
        }
    }

    private void buildUI() {
        LinearLayout root = root();

        // ===== 顶部栏：品牌 + 搜索 + 站点名 =====
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, dp(14), pad, dp(10));

        TextView brand = text("⚡ 洛奇TV", 18, C_TEXT, true);
        header.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView search = text("🔍", 20, C_TEXT, false);
        search.setPadding(dp(10), 0, dp(6), 0);
        search.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MobileHomeActivity.this, MobileListActivity.class);
                i.putExtra("mode", "search");
                startActivity(i);
            }
        });
        header.addView(search);

        tvSourceName = text("选择站点 ▾", 13, C_ACCENT, false);
        tvSourceName.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvSourceName.setBackground(roundBg(C_CARD_2, dp(6)));
        tvSourceName.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileHomeActivity.this, MobileSourceActivity.class));
            }
        });
        header.addView(tvSourceName);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== 分类 tab =====
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setPadding(dp(10), dp(2), dp(10), dp(6));
        tabScroll.addView(tabContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== 状态提示（加载中/错误）=====
        tvStatus = text("", 12, C_TEXT_2, false);
        tvStatus.setPadding(pad, dp(2), pad, dp(6));
        root.addView(tvStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== 影片网格（3列竖屏海报）=====
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new VodAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loadingMore) return;
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int last = lm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 4 && page < maxPage && currentTab >= 0) {
                    loadMore();
                }
            }
        });
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav(0));
        setContentView(root);
    }

    // ===== 数据加载：完整链路 =====
    private void loadConfigAndData(boolean forceReload) {
        tvStatus.setText("正在加载数据源…");
        MobileDataInitializer.ensureReady(forceReload, this, new MobileDataInitializer.Callback() {
            @Override public void onReady() {
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                if (home == null || TextUtils.isEmpty(home.getKey())) {
                    tvStatus.setText("数据源已加载，但没有可用站点");
                    return;
                }
                tvSourceName.setText(TextUtils.isEmpty(home.getName()) ? "站点" : home.getName());
                tvStatus.setText("正在获取分类与推荐…");
                viewModel.getSort(home.getKey());
            }
            @Override public void onError(String msg) {
                tvStatus.setText("加载失败：" + msg);
            }
            @Override public void onNotice(String msg) {
                tvStatus.setText("提示：" + msg);
            }
        });
    }

    private void observeData() {
        viewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override public void onChanged(AbsSortXml xml) {
                homeSort = xml;
                List<MovieSort.SortData> tabs = new ArrayList<>();
                if (xml != null && xml.classes != null && xml.classes.sortList != null) {
                    SourceBean home = ApiConfig.get().getHomeSourceBean();
                    String key = home == null ? null : home.getKey();
                    tabs = DefaultConfig.adjustSort(key, xml.classes.sortList, true);
                }
                sortTabs = tabs;
                buildTabs();
                if (sortTabs.isEmpty()) {
                    List<Movie.Video> rec = extractRecommend(xml);
                    adapter.setData(rec);
                    tvStatus.setText(rec.isEmpty() ? "该站点暂无数据" : ("推荐 " + rec.size() + " 部"));
                    currentTab = -1;
                } else {
                    selectTab(0);
                }
            }
        });

        viewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override public void onChanged(AbsXml xml) {
                loadingMore = false;
                List<Movie.Video> videos = new ArrayList<>();
                if (xml != null && xml.movie != null && xml.movie.videoList != null) {
                    videos = xml.movie.videoList;
                    maxPage = xml.movie.pagecount;
                }
                if (page <= 1) adapter.setData(videos);
                else adapter.addData(videos);
                tvStatus.setText("共 " + adapter.getItemCount() + " 部");
            }
        });
    }

    private List<Movie.Video> extractRecommend(AbsSortXml xml) {
        List<Movie.Video> rec = new ArrayList<>();
        if (xml == null) return rec;
        if (xml.videoList != null && !xml.videoList.isEmpty()) rec.addAll(xml.videoList);
        else if (xml.list != null && xml.list.videoList != null) rec.addAll(xml.list.videoList);
        return rec;
    }

    private void buildTabs() {
        tabContainer.removeAllViews();
        for (int i = 0; i < sortTabs.size(); i++) {
            final int idx = i;
            MovieSort.SortData sd = sortTabs.get(i);
            TextView tab = text(sd.name, 14, i == currentTab ? C_ACCENT : C_TEXT_2, i == currentTab);
            tab.setPadding(dp(14), dp(8), dp(14), dp(8));
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectTab(idx); }
            });
            tabContainer.addView(tab, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void highlightTab(int idx) {
        for (int i = 0; i < tabContainer.getChildCount(); i++) {
            View v = tabContainer.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                tv.setTextColor(Color.parseColor(i == idx ? C_ACCENT : C_TEXT_2));
                tv.setTypeface(i == idx ? android.graphics.Typeface.DEFAULT_BOLD
                        : android.graphics.Typeface.DEFAULT);
            }
        }
    }

    private void selectTab(int idx) {
        if (idx < 0 || idx >= sortTabs.size()) return;
        currentTab = idx;
        highlightTab(idx);
        page = 1;
        maxPage = 1;
        MovieSort.SortData sd = sortTabs.get(idx);
        if ("my0".equals(sd.id)) {
            List<Movie.Video> rec = extractRecommend(homeSort);
            if (!rec.isEmpty()) {
                adapter.setData(rec);
                tvStatus.setText("推荐 " + rec.size() + " 部");
            } else if (sortTabs.size() > 1) {
                selectTab(1);
            } else {
                adapter.setData(new ArrayList<Movie.Video>());
                tvStatus.setText("该站点暂无推荐数据");
            }
        } else {
            tvStatus.setText("正在加载「" + sd.name + "」…");
            viewModel.getList(sd, page);
        }
    }

    private void loadMore() {
        if (currentTab < 0 || currentTab >= sortTabs.size()) return;
        MovieSort.SortData sd = sortTabs.get(currentTab);
        if ("my0".equals(sd.id)) return;
        loadingMore = true;
        page++;
        tvStatus.setText("正在加载更多…");
        viewModel.getList(sd, page);
    }

    @Override protected void onResume() {
        super.onResume();
        // 从设置/站点切换返回后，若数据未就绪则重新加载
        if (!MobileDataInitializer.isReady() && !TextUtils.isEmpty(Hawk.get(HawkConfig.API_URL, ""))) {
            loadConfigAndData(true);
        }
    }

    // ===== 影片网格适配器（非 static，便于访问 dp()）=====
    private class VodAdapter extends RecyclerView.Adapter<VodAdapter.VH> {
        private List<Movie.Video> data = new ArrayList<>();

        void setData(List<Movie.Video> list) {
            this.data = list == null ? new ArrayList<Movie.Video>() : list;
            notifyDataSetChanged();
        }
        void addData(List<Movie.Video> list) {
            if (list == null) return;
            this.data.addAll(list);
            notifyDataSetChanged();
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(C_CARD, dp(8)));
            int p = dp(5);
            card.setPadding(p, p, p, dp(8));

            ImageView cover = new ImageView(parent.getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(Color.parseColor("#22344A"));
            cover.setBackground(ph);
            card.addView(cover, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));

            TextView name = text2(parent.getContext(), "", 12, C_TEXT, false);
            name.setSingleLine(true);
            name.setEllipsis(android.text.TextUtils.TruncateAt.END);
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
            final Movie.Video v = data.get(position);
            holder.name.setText(TextUtils.isEmpty(v.name) ? "(无片名)" : v.name);
            holder.note.setText(TextUtils.isEmpty(v.note) ? "" : v.note);
            if (!TextUtils.isEmpty(v.pic)) ImgUtil.load(v.pic, holder.cover, 4);
            else holder.cover.setImageDrawable(null);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Intent intent = new Intent(view.getContext(), MobileDetailActivity.class);
                    intent.putExtra(MobilePlayActivity.EXTRA_ID, v.id == null ? "" : v.id);
                    String key = TextUtils.isEmpty(v.sourceKey)
                            ? (ApiConfig.get().getHomeSourceBean() == null
                               ? "" : ApiConfig.get().getHomeSourceBean().getKey())
                            : v.sourceKey;
                    intent.putExtra(MobilePlayActivity.EXTRA_SOURCE_KEY, key);
                    intent.putExtra(MobilePlayActivity.EXTRA_NAME, v.name == null ? "" : v.name);
                    intent.putExtra("picture", v.pic == null ? "" : v.pic);
                    view.getContext().startActivity(intent);
                }
            });
        }
        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView cover; TextView name; TextView note;
            VH(View itemView, ImageView cover, TextView name, TextView note) {
                super(itemView);
                this.cover = cover; this.name = name; this.note = note;
            }
        }
    }

    /** 供非 Activity 上下文（如 RecyclerView item）创建文本。 */
    private TextView text2(Context c, String s, int sizeSp, String color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return tv;
    }
}
