package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机竖屏详情页 —— 复用 TV DetailActivity 的 getDetail -> VodInfo.setVideo 链路，
 * UI 为竖屏触摸：封面 + 简介 + 线路选择 + 选集网格 + 收藏 + 播放按钮。
 */
public class MobileDetailActivity extends MobileBaseActivity {

    private SourceViewModel viewModel;
    private String vodId, sourceKey, vodName, picture;
    private VodInfo vodInfo;
    private boolean fromCollect;

    private ImageView cover;
    private TextView tvName, tvMeta, tvDesc, tvCollect;
    private LinearLayout flagBar, episodeGrid;

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

    private void buildUI() {
        LinearLayout root = root();
        root.addView(titleBar("详情"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(20));
        scroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部：封面 + 名称 + 简介
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(pad, pad, pad, dp(10));
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ph = new GradientDrawable();
        ph.setColor(Color.parseColor("#22344A"));
        cover.setBackground(ph);
        head.addView(cover, new LinearLayout.LayoutParams(dp(96), dp(132)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        tvName = text(TextUtils.isEmpty(vodName) ? "加载中…" : vodName, 17, C_TEXT, true);
        tvName.setMaxLines(2);
        info.addView(tvName);
        tvMeta = text("", 12, C_TEXT_2, false);
        tvMeta.setPadding(0, dp(6), 0, 0);
        info.addView(tvMeta);
        tvCollect = text("☆ 收藏", 13, C_ACCENT, false);
        tvCollect.setPadding(0, dp(10), 0, 0);
        tvCollect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCollect(); }
        });
        info.addView(tvCollect);
        head.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        box.addView(head);

        tvDesc = text("", 13, C_TEXT_2, false);
        tvDesc.setPadding(pad, dp(4), pad, dp(10));
        box.addView(tvDesc);

        // 播放按钮
        TextView btnPlay = text("▶ 立即播放", 16, Color.parseColor("#FFFFFF"), true);
        btnPlay.setGravity(Gravity.CENTER);
        btnPlay.setPadding(0, dp(12), 0, dp(12));
        btnPlay.setBackground(roundBg(C_ACCENT, dp(8)));
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { gotoPlay(); }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(pad, dp(4), pad, dp(8));
        box.addView(btnPlay, bp);

        // 线路
        box.addView(sectionTitle("线路"));
        flagBar = new LinearLayout(this);
        flagBar.setOrientation(LinearLayout.HORIZONTAL);
        flagBar.setPadding(pad, 0, pad, dp(6));
        box.addView(flagBar);

        // 选集
        box.addView(sectionTitle("选集"));
        episodeGrid = new LinearLayout(this);
        episodeGrid.setOrientation(LinearLayout.VERTICAL);
        episodeGrid.setPadding(pad, 0, pad, dp(10));
        box.addView(episodeGrid);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        if (!TextUtils.isEmpty(picture)) ImgUtil.load(picture, cover, 6);
        refreshCollectText();
    }

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

        // 读取历史进度
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
        if (!TextUtils.isEmpty(mVideo.area)) meta += mVideo.area + "  ";
        if (mVideo.year > 0) meta += mVideo.year + "  ";
        if (!TextUtils.isEmpty(mVideo.type)) meta += mVideo.type;
        tvMeta.setText(meta.trim());
        tvDesc.setText(TextUtils.isEmpty(mVideo.des) ? "" : "简介：" + mVideo.des);
        if (!TextUtils.isEmpty(mVideo.pic)) ImgUtil.load(mVideo.pic, cover, 6);

        buildFlags();
        buildEpisodes();
        refreshCollectText();
    }

    private void buildFlags() {
        flagBar.removeAllViews();
        if (vodInfo == null || vodInfo.seriesFlags == null) return;
        for (final VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
            boolean sel = flag.name.equals(vodInfo.playFlag);
            TextView t = new TextView(this);
            t.setText(flag.name);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTextColor(Color.parseColor(sel ? "#FFFFFF" : C_TEXT_2));
            t.setPadding(dp(12), dp(7), dp(12), dp(7));
            t.setBackground(roundBg(sel ? C_ACCENT : C_CARD, dp(6)));
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
        int cols = 5;
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
            ep.setText(String.valueOf(i + 1));
            ep.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            ep.setGravity(Gravity.CENTER);
            ep.setTextColor(Color.parseColor(sel ? "#FFFFFF" : C_TEXT));
            ep.setPadding(0, dp(9), 0, dp(9));
            ep.setBackground(roundBg(sel ? C_ACCENT : C_CARD_2, dp(5)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(6);
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
        tvCollect.setText(collected ? "★ 已收藏" : "☆ 收藏");
    }
}
