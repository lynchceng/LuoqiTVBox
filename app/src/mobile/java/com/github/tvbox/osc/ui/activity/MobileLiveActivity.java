package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机竖屏直播页 —— 复用 TV 的 loadLiveConfig -> getChannelGroupList -> LiveChannelItem.getUrl。
 * 播放器配置与 TV 播放链路一致（PlayerHelper.updateCfg），并对所有可能为 null 的
 * 分组/频道数据做空安全处理，避免点击直播时闪退。
 */
public class MobileLiveActivity extends MobileBaseActivity {

    private VideoView<AbstractPlayer> videoView;
    private TextView tvStatus, tvCurrent;
    private Spinner groupSpinner;
    private LinearLayout channelList;

    private List<LiveChannelGroup> groups = new ArrayList<>();
    private int groupIndex = 0;
    private int channelIndex = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUI();
            loadLive();
        } catch (Throwable t) {
            // 任何初始化异常都降级为提示页，绝不让应用闪退
            try {
                LinearLayout r = root();
                r.addView(titleBar("直播"));
                TextView err = text("直播初始化失败：" + (t.getMessage() == null
                        ? t.getClass().getSimpleName() : t.getMessage()), 14, C_TEXT_2, false);
                err.setPadding(pad, dp(24), pad, dp(24));
                r.addView(err);
                r.addView(bottomNav(1));
                setContentView(r);
            } catch (Throwable ignored) {}
        }
    }

    private void buildUI() {
        LinearLayout root = root();
        root.addView(titleBar("直播"));

        // 视频区
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.parseColor("#000000"));
        videoView = new VideoView<>(this);
        try {
            videoView.setVideoController(
                    new com.github.tvbox.osc.player.controller.MobileGestureController(this));
        } catch (Throwable ignored) {
            // 控制器创建失败也不影响播放（DKPlayer 允许无控制器）
        }
        try { PlayerHelper.updateCfg(videoView); } catch (Throwable ignored) {}
        box.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
        root.addView(box);

        tvCurrent = text("未选择频道", 15, C_TEXT, true);
        tvCurrent.setPadding(pad, dp(10), pad, dp(4));
        root.addView(tvCurrent);

        // 换台/换源按钮
        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setPadding(pad, dp(4), pad, dp(6));
        TextView prev = smallBtn("◀ 上一个");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchChannel(-1); }
        });
        TextView next = smallBtn("下一个 ▶");
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchChannel(1); }
        });
        TextView source = smallBtn("换源");
        source.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchSource(); }
        });
        ctrl.addView(prev, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ctrl.addView(next, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ctrl.addView(source, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(ctrl);

        // 分组选择
        groupSpinner = new Spinner(this);
        groupSpinner.setBackgroundColor(Color.parseColor(C_CARD));
        root.addView(groupSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvStatus = text("正在加载直播源…", 12, C_TEXT_2, false);
        tvStatus.setPadding(pad, dp(4), pad, dp(4));
        tvStatus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openLiveConfig(); }
        });
        root.addView(tvStatus);

        // 频道列表
        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelList.setPadding(pad, 0, pad, dp(10));
        root.addView(channelList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav(1));
        setContentView(root);
    }

    private void openLiveConfig() {
        try {
            Intent i = new Intent(this, MobileApiConfigActivity.class);
            i.putExtra(MobileApiConfigActivity.EXTRA_FOCUS, "live");
            startActivity(i);
        } catch (Throwable ignored) {}
    }

    private TextView smallBtn(String label) {
        TextView t = text(label, 13, Color.parseColor("#FFFFFF"), false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(9), dp(6), dp(9));
        t.setBackground(roundBg(C_CARD_2, dp(6)));
        return t;
    }

    private void loadLive() {
        try {
            // TV 端在 HomeActivity 启动 127.0.0.1:9978 代理；直播源被包装成该代理地址，
            // 这里兜底确保服务已启动（startServer 幂等），否则内嵌 lives 无法加载。
            try { com.github.tvbox.osc.server.ControlManager.get().startServer(); } catch (Throwable ignored) {}
            ApiConfig.get().loadLiveConfig(true, new ApiConfig.LoadConfigCallback() {
                @Override public void success() { runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try { applyGroups(); }
                        catch (Throwable t) { showError("直播数据解析失败：" + t.getMessage()); }
                    }
                }); }
                @Override public void error(String msg) { runOnUiThread(new Runnable() {
                    @Override public void run() { showError("直播加载失败：" + msg); }
                }); }
                @Override public void notice(String msg) { runOnUiThread(new Runnable() {
                    @Override public void run() { if (tvStatus != null) tvStatus.setText("提示：" + msg); }
                }); }
            });
        } catch (Throwable t) {
            showError("直播配置异常：" + t.getMessage());
        }
    }

    private void showError(String msg) {
        if (tvStatus != null) tvStatus.setText(msg + "（点此配置直播地址）");
        if (channelList != null) channelList.removeAllViews();
    }

    /** 取指定分组的频道列表，永不为 null。 */
    private List<LiveChannelItem> channels(int idx) {
        if (idx < 0 || idx >= groups.size()) return new ArrayList<LiveChannelItem>();
        LiveChannelGroup g = groups.get(idx);
        if (g == null) return new ArrayList<LiveChannelItem>();
        List<LiveChannelItem> items = g.getLiveChannels();
        return items == null ? new ArrayList<LiveChannelItem>() : items;
    }

    private void applyGroups() {
        List<LiveChannelGroup> gs = ApiConfig.get().getChannelGroupList();
        groups = new ArrayList<>();
        if (gs != null) {
            for (LiveChannelGroup g : gs) if (g != null) groups.add(g);
        }
        if (groups.isEmpty()) {
            showError("暂无频道，请配置直播订阅地址");
            return;
        }
        List<String> names = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < groups.size(); i++) {
            LiveChannelGroup g = groups.get(i);
            String n = g.getGroupName();
            names.add(TextUtils.isEmpty(n) ? ("分组 " + (i + 1)) : n);
            List<LiveChannelItem> ch = channels(i);
            total += ch.size();
        }
        try {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            groupSpinner.setAdapter(adapter);
        } catch (Throwable ignored) {}

        int saved = intVal(HawkConfig.LIVE_GROUP_INDEX, 0);
        if (saved < 0 || saved >= groups.size()) saved = 0;
        groupIndex = saved;
        try { groupSpinner.setSelection(groupIndex); } catch (Throwable ignored) {}
        try {
            groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    if (pos < 0 || pos >= groups.size()) return;
                    groupIndex = pos;
                    channelIndex = 0;
                    try { Hawk.put(HawkConfig.LIVE_GROUP_INDEX, pos); } catch (Throwable ignored) {}
                    buildChannels();
                    playCurrent();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        } catch (Throwable ignored) {}

        buildChannels();
        if (tvStatus != null) tvStatus.setText(groups.size() + " 个分组 · " + total + " 个频道");
        playCurrent();
    }

    private void buildChannels() {
        if (channelList == null) return;
        channelList.removeAllViews();
        final List<LiveChannelItem> items = channels(groupIndex);
        if (items.isEmpty()) {
            TextView empty = text("该分组暂无频道（点上方状态可配置直播地址）", 13, C_TEXT_2, false);
            empty.setPadding(0, dp(16), 0, dp(16));
            empty.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openLiveConfig(); }
            });
            channelList.addView(empty);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            LiveChannelItem item = items.get(i);
            if (item == null) continue;
            String num;
            String name;
            try { num = String.valueOf(item.getChannelNum()); } catch (Throwable t) { num = String.valueOf(i + 1); }
            try { name = item.getChannelName(); } catch (Throwable t) { name = ""; }
            if (TextUtils.isEmpty(name)) name = "频道 " + (i + 1);
            TextView t = new TextView(this);
            t.setText(num + ". " + name);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            t.setTextColor(Color.parseColor(i == channelIndex ? C_ACCENT : C_TEXT));
            t.setPadding(dp(10), dp(10), dp(10), dp(10));
            t.setBackground(roundBg(i == channelIndex ? C_CARD_2 : C_CARD, dp(6)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            channelList.addView(t, lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    channelIndex = idx;
                    buildChannels();
                    playCurrent();
                }
            });
        }
    }

    private void switchChannel(int dir) {
        List<LiveChannelItem> items = channels(groupIndex);
        if (items.isEmpty()) return;
        channelIndex = (channelIndex + dir + items.size()) % items.size();
        buildChannels();
        playCurrent();
    }

    private void switchSource() {
        List<LiveChannelItem> items = channels(groupIndex);
        if (channelIndex < 0 || channelIndex >= items.size()) return;
        LiveChannelItem item = items.get(channelIndex);
        if (item == null) return;
        try { item.nextSource(); } catch (Throwable ignored) {}
        playCurrent();
    }

    private void playCurrent() {
        if (videoView == null) return;
        try {
            List<LiveChannelItem> items = channels(groupIndex);
            if (channelIndex < 0 || channelIndex >= items.size()) return;
            LiveChannelItem item = items.get(channelIndex);
            if (item == null) return;
            String url;
            try { url = item.getUrl(); } catch (Throwable t) { url = ""; }
            if (TextUtils.isEmpty(url)) {
                if (tvStatus != null) tvStatus.setText("该频道暂无播放地址");
                return;
            }
            String name = "";
            try { name = item.getChannelName(); } catch (Throwable ignored) {}
            if (!TextUtils.isEmpty(name)) {
                tvCurrent.setText(name);
                try { Hawk.put(HawkConfig.LIVE_CHANNEL, name); } catch (Throwable ignored) {}
            }
            Map<String, String> headers = null;
            try { headers = item.getHeaders(); } catch (Throwable ignored) {}
            videoView.release();
            if (headers == null || headers.isEmpty()) videoView.setUrl(url);
            else videoView.setUrl(url, headers);
            videoView.start();
        } catch (Throwable t) {
            if (tvStatus != null) tvStatus.setText("播放失败：" + (t.getMessage() == null
                    ? "数据源异常" : t.getMessage()));
        }
    }

    private int intVal(String key, int def) {
        try { return Hawk.get(key, def); } catch (Throwable t) { return def; }
    }

    @Override protected void onPause() {
        super.onPause();
        try { if (videoView != null) videoView.pause(); } catch (Throwable ignored) {}
    }
    @Override protected void onResume() {
        super.onResume();
        try { if (videoView != null) videoView.resume(); } catch (Throwable ignored) {}
        // 配置地址变化后重新拉取直播订阅，避免继续用旧配置
        try {
            if (MobileDataInitializer.consumeConfigDirty()) {
                groups.clear();
                channelIndex = 0;
                loadLive();
            }
        } catch (Throwable ignored) {}
    }
    @Override protected void onDestroy() {
        try { if (videoView != null) videoView.release(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
