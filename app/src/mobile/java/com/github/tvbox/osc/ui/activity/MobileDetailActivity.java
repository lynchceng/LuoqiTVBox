package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import com.orhanobut.hawk.Hawk;

import java.util.List;

/**
 * 手机竖屏详情页 —— 复用 TV DetailActivity 的 getDetail -> VodInfo.setVideo 链路。
 * UI 参考目标：顶部播放器预览 + 影片信息（评分/导演/演员/收藏）+ 功能按钮行 + 线路 + 4 列选集。
 */
public class MobileDetailActivity extends MobileBaseActivity {

    private SourceViewModel viewModel;
    private String vodId, sourceKey, vodName, picture;
    private VodInfo vodInfo;
    private boolean fromCollect;

    private ImageView cover, playerCover;
    private TextView tvName, tvMeta, tvDirector, tvActor, tvScore, tvStars, tvDesc, tvCollect;
    private LinearLayout flagBar, episodeGrid;
    private boolean descExpanded = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getIntent().getExtras();
        if (b != null) {
            vodId = b.getString(MobilePlayActivity.EXTRA_ID, "");
            sourceKey = b.getString(MobilePlayActivity.EXTRA_SOURCE_KEY, "");
            vodName = b.getString(MobilePlayActivity.EXTRA_NAME, "");
            picture = b.getString("picture", "");
            fromCollect = b.getBoolean("collect", false);
        }
        viewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        buildUI();
        viewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override public void onChanged(AbsXml xml) { onDetail(xml); }
        });
        if (!TextUtils.isEmpty(vodId)) {
            viewModel.getDetail(sourceKey, vodId);
        }
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------
    private void buildUI() {
        LinearLayout root = root();

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(24));
        scroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.addView(buildPlayerPreview());
        box.addView(buildInfoBlock());
        box.addView(buildDescBlock());
        box.addView(buildActionRow());
        box.addView(sectionTitle("线路"));
        LinearLayout flagScrollWrap = new LinearLayout(this);
        flagScrollWrap.setPadding(pad, 0, pad, dp(2));
        HorizontalScrollView flagScroll = new HorizontalScrollView(this);
        flagBar = new LinearLayout(this);
        flagBar.setOrientation(LinearLayout.HORIZONTAL);
        flagScroll.addView(flagBar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        flagScrollWrap.addView(flagScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(flagScrollWrap);

        box.addView(sectionTitle("选集"));
        episodeGrid = new LinearLayout(this);
        episodeGrid.setOrientation(LinearLayout.VERTICAL);
        episodeGrid.setPadding(pad, 0, pad, dp(10));
        box.addView(episodeGrid);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        if (!TextUtils.isEmpty(picture)) {
            ImgUtil.load(picture, cover, 6);
            ImgUtil.load(picture, playerCover, 8);
        }
        refreshCollectText();
    }

    /** 顶部播放器预览：海报暗化背景 + 中央播放按钮，点击进入播放页。 */
    private FrameLayout buildPlayerPreview() {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#3A1212"), Color.parseColor("#0E1929")});
        box.setBackground(gd);

        playerCover = new ImageView(this);
        playerCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        box.addView(playerCover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        View shade = new View(this);
        shade.setBackgroundColor(Color.parseColor("#A6000000"));
        box.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        TextView back = text("‹", 30, Color.parseColor("#FFFFFF"), false);
        back.setPadding(dp(12), dp(6), dp(12), dp(6));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.gravity = Gravity.TOP | Gravity.LEFT;
        box.addView(back, bl);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView play = text("▶", 40, Color.parseColor("#FFFFFF"), false);
        play.setBackground(roundBg("#66FF7043", dp(40)));
        play.setGravity(Gravity.CENTER);
        play.setPadding(dp(6), dp(6), dp(6), dp(6));
        center.addView(play);
        TextView tip = text("点击播放", 13, Color.parseColor("#DDFFFFFF"), false);
        tip.setPadding(0, dp(10), 0, 0);
        center.addView(tip);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.gravity = Gravity.CENTER;
        box.addView(center, cl);

        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { gotoPlay(); }
        });
        return box;
    }

    /** 影片信息：封面 + 标题 + 地区年份 + 导演演员 + 评分星级 + 收藏。 */
    private LinearLayout buildInfoBlock() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(pad, dp(14), pad, dp(6));

        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(coverPlaceholder());
        head.addView(cover, new LinearLayout.LayoutParams(dp(92), dp(124)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);

        tvName = text(TextUtils.isEmpty(vodName) ? "加载中…" : vodName, 18, C_TEXT, true);
        tvName.setMaxLines(2);
        info.addView(tvName);

        tvMeta = text("", 12, C_TEXT_2, false);
        tvMeta.setPadding(0, dp(5), 0, 0);
        info.addView(tvMeta);

        tvDirector = text("", 11, C_TEXT_2, false);
        tvDirector.setSingleLine(true);
        tvDirector.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvDirector.setPadding(0, dp(3), 0, 0);
        info.addView(tvDirector);

        tvActor = text("", 11, C_TEXT_2, false);
        tvActor.setMaxLines(2);
        tvActor.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvActor.setPadding(0, dp(3), 0, 0);
        info.addView(tvActor);

        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        scoreRow.setPadding(0, dp(6), 0, 0);
        tvScore = text("", 22, Color.parseColor("#FFD54F"), true);
        scoreRow.addView(tvScore);
        tvStars = text("", 12, Color.parseColor("#FFB300"), false);
        tvStars.setPadding(dp(6), 0, 0, 0);
        scoreRow.addView(tvStars);
        info.addView(scoreRow);

        head.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 收藏按钮（参考图右侧黄色胶囊）
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.BOTTOM);
        tvCollect = text("加入收藏", 13, Color.parseColor("#3E2A00"), true);
        tvCollect.setGravity(Gravity.CENTER);
        tvCollect.setPadding(dp(12), dp(8), dp(12), dp(8));
        tvCollect.setBackground(roundBg("#FFD54F", dp(16)));
        tvCollect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCollect(); }
        });
        rightCol.addView(tvCollect, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addView(rightCol, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(124), 0f));
        return head;
    }

    /** 简介：单行摘要 + 箭头，点击展开/收起全文。 */
    private LinearLayout buildDescBlock() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(pad, dp(2), pad, dp(8));
        TextView label = text("简介", 12, C_TEXT_2, true);
        wrap.addView(label);
        tvDesc = text("", 12, C_TEXT_2, false);
        tvDesc.setSingleLine(true);
        tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        wrap.addView(tvDesc, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView arrow = text("▶", 11, C_TEXT_2, false);
        arrow.setPadding(dp(8), 0, 0, 0);
        wrap.addView(arrow);
        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                descExpanded = !descExpanded;
                tvDesc.setSingleLine(!descExpanded);
                arrow.setText(descExpanded ? "▼" : "▶");
            }
        });
        return wrap;
    }

    /** 功能按钮行：下载 / 快搜 / Exo / 投屏 / 设置。 */
    private LinearLayout buildActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(6), dp(12), dp(6), dp(8));
        row.addView(actionBtn("↓", "下载", new View.OnClickListener() {
            @Override public void onClick(View v) { toast("请在播放页使用下载功能"); }
        }));
        row.addView(actionBtn("⌕", "快搜", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String kw = TextUtils.isEmpty(vodName) ? (vodInfo == null ? "" : vodInfo.name) : vodName;
                if (TextUtils.isEmpty(kw)) { toast("片名加载中"); return; }
                Intent i = new Intent(MobileDetailActivity.this, MobileListActivity.class);
                i.putExtra(MobileListActivity.EXTRA_MODE, MobileListActivity.MODE_SEARCH);
                i.putExtra(MobileListActivity.EXTRA_QUERY, kw);
                startActivity(i);
            }
        }));
        row.addView(actionBtn("▶", "Exo", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    int type = Hawk.get(HawkConfig.PLAY_TYPE, 2);
                    int next = type == 1 ? 2 : 1;
                    Hawk.put(HawkConfig.PLAY_TYPE, next);
                    toast(next == 1 ? "已切换 IJK 内核" : "已切换 EXO 内核");
                } catch (Throwable t) { toast("切换内核失败"); }
            }
        }));
        row.addView(actionBtn("▣", "投屏", new View.OnClickListener() {
            @Override public void onClick(View v) { toast("请在播放页使用投屏功能"); }
        }));
        row.addView(actionBtn("⚙", "设置", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileDetailActivity.this, MobileSettingActivity.class));
            }
        }));
        return row;
    }

    private LinearLayout actionBtn(String icon, String label, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), dp(4), dp(4), dp(4));
        TextView ic = text(icon, 21, C_TEXT, false);
        ic.setGravity(Gravity.CENTER);
        b.addView(ic);
        TextView lb = text(label, 11, C_TEXT_2, false);
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(0, dp(3), 0, 0);
        b.addView(lb);
        b.setOnClickListener(click);
        return b;
    }

    // ------------------------------------------------------------------
    // 详情数据
    // ------------------------------------------------------------------
    private void onDetail(AbsXml xml) {
        if (xml == null || xml.movie == null || xml.movie.videoList == null
                || xml.movie.videoList.isEmpty()) {
            toast("获取详情失败");
            return;
        }
        Movie.Video mVideo = xml.movie.videoList.get(0);
        mVideo.id = vodId;
        if (TextUtils.isEmpty(mVideo.name)) mVideo.name = vodName;
        if (TextUtils.isEmpty(mVideo.pic)) mVideo.pic = picture;

        vodInfo = new VodInfo();
        vodInfo.setVideo(mVideo);
        vodInfo.sourceKey = sourceKey;
        vodInfo.id = vodId;
        vodInfo.name = mVideo.name;

        VodInfo his = RoomDataManger.getVodInfo(sourceKey, vodId);
        if (his != null) {
            vodInfo.playFlag = his.playFlag;
            vodInfo.playIndex = his.playIndex;
        }
        if (TextUtils.isEmpty(vodInfo.playFlag) || vodInfo.seriesMap == null
                || !vodInfo.seriesMap.containsKey(vodInfo.playFlag)) {
            if (vodInfo.seriesMap != null && !vodInfo.seriesMap.isEmpty()
                    && vodInfo.seriesFlags != null && !vodInfo.seriesFlags.isEmpty()) {
                vodInfo.playFlag = vodInfo.seriesFlags.get(0).name;
            }
        }

        tvName.setText(TextUtils.isEmpty(mVideo.name) ? "(无片名)" : mVideo.name);
        String meta = "";
        if (!TextUtils.isEmpty(mVideo.area)) meta += "地区：" + mVideo.area + "  ";
        if (mVideo.year > 0) meta += "年份：" + mVideo.year + "  ";
        if (!TextUtils.isEmpty(mVideo.type)) meta += mVideo.type;
        tvMeta.setText(meta.trim());
        tvDirector.setText(TextUtils.isEmpty(mVideo.director) ? ""
                : "导演：" + mVideo.director.replace(",", " "));
        tvActor.setText(TextUtils.isEmpty(mVideo.actor) ? ""
                : "演员：" + mVideo.actor.replace(",", " "));

        // 站点源不一定返回评分，有 tag 时借用展示，否则给中性星级
        String score = TextUtils.isEmpty(mVideo.tag) ? "" : mVideo.tag;
        if (!TextUtils.isEmpty(score) && score.length() <= 5) {
            tvScore.setText(score);
            try {
                float f = Float.parseFloat(score);
                int stars = Math.max(0, Math.min(5, Math.round(f / 2f)));
                tvStars.setText(repeat("★", stars) + repeat("☆", 5 - stars));
            } catch (Throwable t) { tvStars.setText(""); }
        } else {
            tvScore.setText("");
            tvStars.setText("★★★☆☆");
        }

        String desc = TextUtils.isEmpty(mVideo.des) ? "暂无简介" : mVideo.des;
        tvDesc.setText(desc);
        if (!TextUtils.isEmpty(mVideo.pic)) {
            ImgUtil.load(mVideo.pic, cover, 6);
            ImgUtil.load(mVideo.pic, playerCover, 8);
        }
        buildFlags();
        buildEpisodes();
        refreshCollectText();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private void buildFlags() {
        flagBar.removeAllViews();
        if (vodInfo == null || vodInfo.seriesFlags == null) return;
        for (final VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
            boolean sel = flag.name.equals(vodInfo.playFlag);
            TextView t = new TextView(this);
            t.setText(flag.name);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            t.setTextColor(Color.parseColor(sel ? "#3E2A00" : C_TEXT_2));
            t.setPadding(dp(12), dp(6), dp(12), dp(6));
            t.setBackground(roundBg(sel ? "#FFD54F" : C_CARD, dp(14)));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    vodInfo.playFlag = flag.name;
                    vodInfo.playIndex = 0;
                    buildFlags();
                    buildEpisodes();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            flagBar.addView(t, lp);
        }
    }

    private void buildEpisodes() {
        episodeGrid.removeAllViews();
        if (vodInfo == null || vodInfo.seriesMap == null) return;
        List<VodInfo.VodSeries> eps = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (eps == null) return;
        final int cols = 4;
        LinearLayout row = null;
        for (int i = 0; i < eps.size(); i++) {
            final int idx = i;
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                episodeGrid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            boolean sel = idx == vodInfo.playIndex;
            TextView ep = new TextView(this);
            ep.setText(String.format("%02d", i + 1));
            ep.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            ep.setGravity(Gravity.CENTER);
            ep.setTextColor(Color.parseColor(sel ? "#3E2A00" : C_TEXT));
            ep.setPadding(0, dp(9), 0, dp(9));
            ep.setBackground(roundBg(sel ? "#FFD54F" : C_CARD_2, dp(6)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(7);
            lp.bottomMargin = dp(7);
            row.addView(ep, lp);
            ep.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    vodInfo.playIndex = idx;
                    buildEpisodes();
                    gotoPlay();
                }
            });
        }
    }

    private void gotoPlay() {
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.seriesMap.isEmpty()) {
            toast("暂无可播放线路");
            return;
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
        App.getInstance().setVodInfo(vodInfo);
        Intent i = new Intent(this, MobilePlayActivity.class);
        i.putExtra(MobilePlayActivity.EXTRA_SOURCE_KEY, sourceKey);
        i.putExtra(MobilePlayActivity.EXTRA_NAME, vodInfo.name);
        startActivity(i);
    }

    private void toggleCollect() {
        if (vodInfo == null) { toast("详情加载中"); return; }
        boolean collected = RoomDataManger.isVodCollect(sourceKey, vodId);
        if (collected) RoomDataManger.deleteVodCollect(sourceKey, vodInfo);
        else RoomDataManger.insertVodCollect(sourceKey, vodInfo);
        refreshCollectText();
        toast(collected ? "已取消收藏" : "已加入收藏");
    }

    private void refreshCollectText() {
        boolean collected = RoomDataManger.isVodCollect(sourceKey, vodId);
        tvCollect.setText(collected ? "已收藏 ★" : "加入收藏");
    }
}
